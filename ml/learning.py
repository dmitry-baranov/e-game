"""Train only on opted-in finished games, using snapshots captured before the actual action."""
import json
import os
import tempfile
import hashlib
import time
import uuid
import threading
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import psycopg2
from catboost import CatBoostRegressor
from lightgbm import LGBMRegressor
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

ARTIFACT = Path(os.environ.get("STRATEGY_ARTIFACT", "ml/artifacts/champion.joblib"))
FEATURE_VERSION = 1
MIN_GAMES = 12
FEATURE_NAMES = ("product_count", "price", "quality", "assortment", "advertising_intensity",
                 "advertising_days", "cycle_days", "stock", "capacity", "balance", "base_cost",
                 "past_sales_days", "past_sales_mean", "latest_sales", "past_stockouts",
                 "min_visible_competitor_price", "visible_competitors")

_run_id = None
_started = None
_train_mutex = threading.Lock()
_dataset_info = {}


def save_report(report):
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    report = report | {"training_run_id": _run_id or str(uuid.uuid4()),
                       "created_at": datetime.now(timezone.utc).isoformat(),
                       "duration_seconds": round(time.monotonic() - _started, 3) if _started else None}
    history = ARTIFACT.parent / "training-runs"
    history.mkdir(exist_ok=True)
    immutable = history / (report["training_run_id"] + ".json")
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=history, delete=False) as file:
        json.dump(report, file, indent=2, ensure_ascii=False, default=str)
        temp = file.name
    try:
        # Link a fully written temporary file atomically; fail if the run already exists.
        os.link(temp, immutable)
    finally:
        os.unlink(temp)
    if os.environ.get("STRATEGY_REPORT_DATABASE_URL") and os.environ.get("STRATEGY_DATASET") == "experiment":
        with psycopg2.connect(os.environ["STRATEGY_REPORT_DATABASE_URL"]) as connection:
            with connection.cursor() as cursor:
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS training_run_report (
                      training_run_id UUID PRIMARY KEY,
                      created_at TIMESTAMPTZ NOT NULL,
                      status TEXT NOT NULL,
                      artifact_sha256 TEXT,
                      report JSONB NOT NULL)
                """)
                cursor.execute("""
                    INSERT INTO training_run_report (training_run_id, created_at, status, artifact_sha256, report)
                    VALUES (%s, %s, %s, %s, %s::jsonb)
                """, (report["training_run_id"], report["created_at"], report["status"],
                      report.get("artifact_sha256"), json.dumps(report, default=str)))
                schema = Path(__file__).with_name("analytics_views.sql")
                if schema.exists():
                    cursor.execute(schema.read_text(encoding="utf-8"))
    path = ARTIFACT.with_name("last-report.json")
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=ARTIFACT.parent, delete=False) as file:
        json.dump(report, file, indent=2, ensure_ascii=False, default=str)
        temp = file.name
    os.replace(temp, path)
    return report


def features(snapshot, action):
    sales = snapshot.get("sales") or []
    competitors = snapshot.get("competitors") or []
    prices = [float(c["price"]) for c in competitors if c.get("price") is not None]
    sold = [int(s["sold"]) for s in sales]
    return [
        float(action["productCount"]), float(action["price"]), float(action["quality"]),
        float(action["assortment"]), float(action["advertisingIntensity"]),
        float(action["advertisingDays"]), float(action["cycleDays"]),
        float(snapshot.get("stock") or 0), float(snapshot.get("capacity") or action["productCount"]),
        float(snapshot.get("balance") or 0), float(snapshot.get("baseCost") or 0),
        len(sold), np.mean(sold) if sold else 0, sold[0] if sold else 0,
        sum(s.get("stock", 0) == 0 for s in sales),
        min(prices) if prices else 0, len(prices),
    ]


def load_episodes():
    global _dataset_info
    # Database access is read-only; the DB user should have SELECT on these tables only.
    dataset = os.environ.get("STRATEGY_DATASET", "consented")
    if dataset not in ("consented", "experiment"):
        raise ValueError("Unknown training dataset")
    experiment_filter = ("AND EXISTS (SELECT 1 FROM experiment_run r "
                         "JOIN experiment_series es ON es.id = r.series_id "
                         "WHERE r.game_id = g.id AND r.status = 'COMPLETED' "
                         "AND COALESCE(es.mode, 'COLLECT') = 'COLLECT')") if dataset == "experiment" else ""
    group_column = ("(SELECT MIN(r.seed) FROM experiment_run r WHERE r.game_id = g.id)"
                    if dataset == "experiment" else "m.game_id")
    with psycopg2.connect(os.environ["STRATEGY_DATABASE_URL"]) as connection:
        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT e.id, m.game_id, e.decision_day, e.cycle_days,
                       e.snapshot_json, e.action_json, s.trade_date, s.products_sold,
                        """ + group_column + """ AS scenario_seed,
                        """ + ("(SELECT MIN(r.series_id) FROM experiment_run r WHERE r.game_id = g.id)" if dataset == "experiment" else "NULL") + """ AS series_id,
                        m.selected_strategy,
                        """ + ("(SELECT MIN(r.market) FROM experiment_run r WHERE r.game_id = g.id)" if dataset == "experiment" else "NULL") + """ AS market,
                        g.current_day,
                        """ + ("(SELECT MIN(es.generator_version) FROM experiment_run r "
                               "JOIN experiment_series es ON es.id=r.series_id WHERE r.game_id=g.id)" if dataset == "experiment" else "NULL") + """ AS generator_version
                FROM strategy_training_episode e
                JOIN manufacturer m ON m.id = e.manufacturer_id
                JOIN game g ON g.id = m.game_id
                LEFT JOIN statistics_info s ON s.manufacturer_id = m.id
                    AND s.trade_date > e.decision_day
                    AND s.trade_date <= e.decision_day + e.cycle_days
                WHERE g.status = 'FINISHED' AND m.allow_strategy_training = true
                """ + experiment_filter + """
                ORDER BY e.id, s.trade_date
            """)
            rows = cursor.fetchall()
    groups = {}
    for eid, game, day, cycle, snapshot, action, trade_day, sold, scenario_seed, series_id, policy, market, horizon, generator in rows:
        row = groups.setdefault(eid, {"game": game, "day": day, "cycle": cycle,
                                      "snapshot": snapshot, "action": action, "seed": scenario_seed,
                                      "series_id": series_id, "policy": policy, "market": market, "horizon": horizon,
                                      "generator": generator,
                                      "sales": [], "days": set(), "duplicates": 0})
        if trade_day is not None:
            if trade_day in row["days"]:
                row["duplicates"] += 1
            elif sold is not None:
                row["days"].add(trade_day)
                row["sales"].append(sold)
    episodes = []
    incomplete = 0
    duplicates = 0
    for row in groups.values():
        # Missing trading days are not zero sales; skip incomplete cycles.
        if row["duplicates"]:
            duplicates += 1
            continue
        if len(row["days"]) != row["cycle"]:
            incomplete += 1
            continue
        snapshot, action = json.loads(row["snapshot"]), json.loads(row["action"])
        episodes.append((row["game"], features(snapshot, action), sum(row["sales"]), snapshot, action,
                         row["seed"], row["series_id"], row["policy"], row["market"], row["horizon"], row["generator"]))
    _dataset_info = {"candidate_episodes": len(groups), "incomplete_cycles": incomplete,
                     "duplicate_cycles": duplicates,
                     "eligible_cycles": len(episodes)}
    return episodes


def split_games(episodes, fixed_test_groups=None):
    games = np.array([e[5] if len(e) > 5 else e[0] for e in episodes])
    all_indices = np.arange(len(episodes))
    if fixed_test_groups is None:
        train_val, test = next(GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
                               .split(all_indices, groups=games))
    else:
        test = all_indices[[str(group) in fixed_test_groups for group in games]]
        train_val = all_indices[[str(group) not in fixed_test_groups for group in games]]
        if not len(test) or len(set(games[train_val])) < 2 or \
                not fixed_test_groups.issubset(set(map(str, games[test]))):
            raise ValueError("Fixed holdout groups missing or too few training groups")
    train_offset, val_offset = next(GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=43)
                                    .split(train_val, groups=games[train_val]))
    return train_val[train_offset], train_val[val_offset], test


def train(episodes=None):
    global _run_id, _started
    with _train_mutex:
        _run_id, _started = str(uuid.uuid4()), time.monotonic()
        try:
            return _train(episodes)
        except Exception as error:
            if not (ARTIFACT.parent / "training-runs" / (_run_id + ".json")).exists():
                save_report({"status": "error", "error": type(error).__name__,
                             "dataset": os.environ.get("STRATEGY_DATASET", "consented"),
                             "feature_version": FEATURE_VERSION})
            raise
        finally:
            _run_id, _started = None, None


def _train(episodes=None):
    global _dataset_info
    if episodes is None:
        episodes = load_episodes()
    else:
        _dataset_info = {"candidate_episodes": len(episodes), "incomplete_cycles": 0,
                         "duplicate_cycles": 0,
                         "eligible_cycles": len(episodes)}
    if len(episodes) < 60 or len(set(e[0] for e in episodes)) < MIN_GAMES or \
            len(set(e[5] if len(e) > 5 else e[0] for e in episodes)) < 5:
        return save_report({"status": "insufficient_data", "dataset": os.environ.get("STRATEGY_DATASET", "consented"),
                            "feature_version": FEATURE_VERSION, "selection": _dataset_info,
                            "games": len(set(e[0] for e in episodes)), "cycles": len(episodes)})
    holdout_file = ARTIFACT.parent / "test-groups.json"
    fixed_test_groups = None
    if os.environ.get("STRATEGY_DATASET") == "experiment" and holdout_file.exists():
        fixed_test_groups = set(json.loads(holdout_file.read_text(encoding="utf-8"))["groups"])
    train_idx, val_idx, test_idx = split_games(episodes, fixed_test_groups)
    if os.environ.get("STRATEGY_DATASET") == "experiment" and fixed_test_groups is None:
        holdout_file.parent.mkdir(parents=True, exist_ok=True)
        selected = sorted(set(str(episodes[i][5] if len(episodes[i]) > 5 else episodes[i][0]) for i in test_idx))
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=holdout_file.parent, delete=False) as file:
            json.dump({"groups": selected, "created_at": datetime.now(timezone.utc).isoformat()}, file)
            temporary_holdout = file.name
        try:
            os.link(temporary_holdout, holdout_file)
        finally:
            os.unlink(temporary_holdout)
    x = np.asarray([e[1] for e in episodes], dtype=float)
    y = np.asarray([e[2] for e in episodes], dtype=float)
    if x.shape[1] != len(FEATURE_NAMES):
        raise ValueError("Feature schema changed without updating FEATURE_VERSION")
    # All algorithms receive the same numeric, pre-decision features. No fitting on validation/test.
    models = {
        "Ridge": make_pipeline(StandardScaler(), Ridge(alpha=10)),
        "RandomForest": RandomForestRegressor(n_estimators=150, min_samples_leaf=4, n_jobs=1, random_state=42),
        "CatBoost": CatBoostRegressor(iterations=300, depth=4, learning_rate=0.04, verbose=False,
                                      allow_writing_files=False, thread_count=1, random_seed=42),
        "LightGBM": LGBMRegressor(n_estimators=200, num_leaves=7, min_child_samples=15, verbosity=-1, n_jobs=1, random_state=42),
    }
    baseline = lambda indices: np.array([
        (np.mean([s["sold"] for s in episodes[i][3].get("sales", [])])
         * episodes[i][4]["cycleDays"]) if episodes[i][3].get("sales") else 0
        for i in indices
    ])
    baseline_val = mean_absolute_error(y[val_idx], baseline(val_idx))
    def diagnostics(indices, predicted):
        actual = y[indices]
        difference = predicted - actual
        return {"n": len(indices), "mae": round(float(np.mean(abs(difference))), 3),
                "median_absolute_error": round(float(np.median(abs(difference))), 3),
                "zero_sales_n": int(np.sum(actual == 0)),
                "zero_sales_mae": round(float(np.mean(abs(difference[actual == 0]))), 3) if np.any(actual == 0) else None,
                "overestimated": int(np.sum(difference > 0)), "underestimated": int(np.sum(difference < 0))}

    def sliced(indices, predicted):
        dimensions = {
            "market": lambda i: episodes[i][8] if len(episodes[i]) > 8 and episodes[i][8] else "нет данных",
            "policy": lambda i: episodes[i][7] if len(episodes[i]) > 7 and episodes[i][7] else "нет данных",
            "price": lambda i: "<10" if x[i, 1] < 10 else ("10–19" if x[i, 1] < 20 else "≥20"),
            "horizon": lambda i: ("≤60" if episodes[i][9] <= 60 else
                                   "61–90" if episodes[i][9] <= 90 else ">90")
                                   if len(episodes[i]) > 9 and episodes[i][9] is not None else "нет данных",
        }
        groups = {}
        for dimension, category in dimensions.items():
            labels = sorted(set(category(i) for i in indices))
            groups[dimension] = {label: diagnostics(indices[[category(i) == label for i in indices]],
                                                      predicted[[category(i) == label for i in indices]])
                                 for label in labels}
        return groups

    def examples(indices, predicted):
        return [{"actual": round(float(y[i]), 2), "predicted": round(float(value), 2)}
                for i, value in list(zip(indices, predicted))[:100]]

    partitions = {"train": train_idx, "validation": val_idx, "test": test_idx}
    results = {"LastSales": {part: diagnostics(indices, baseline(indices))
                             for part, indices in partitions.items()}}
    results["LastSales"].update(validation_mae=round(baseline_val, 3),
                                test_mae=results["LastSales"]["test"]["mae"])
    results["LastSales"]["slices"] = {part: sliced(indices, baseline(indices))
                                      for part, indices in partitions.items() if part != "train"}
    results["LastSales"]["samples"] = {part: examples(indices, baseline(indices))
                                       for part, indices in partitions.items() if part != "train"}
    best = None
    candidates = []
    for name, model in models.items():
        model.fit(x[train_idx], y[train_idx])
        prediction = np.maximum(0, model.predict(x[val_idx]))
        score = mean_absolute_error(y[val_idx], prediction)
        results[name] = {part: diagnostics(indices, np.maximum(0, model.predict(x[indices])))
                         for part, indices in partitions.items()}
        results[name].update(validation_mae=round(score, 3), test_mae=results[name]["test"]["mae"],
                             parameters=model.get_params())
        results[name]["slices"] = {part: sliced(indices, np.maximum(0, model.predict(x[indices])))
                                    for part, indices in partitions.items() if part != "train"}
        results[name]["samples"] = {part: examples(indices, np.maximum(0, model.predict(x[indices])))
                                     for part, indices in partitions.items() if part != "train"}
        if score < baseline_val:
            candidates.append({"name": name, "model": model,
                               "error90": float(np.quantile(abs(y[val_idx] - prediction), 0.9)),
                               "validation_mae": float(score)})
        if score < baseline_val and (best is None or score < best[0]):
            best = (score, name, model, np.quantile(abs(y[val_idx] - prediction), 0.9))
    report = {"status": "baseline" if best is None else "trained", "dataset": os.environ.get("STRATEGY_DATASET", "consented"),
               "games": len(set(e[0] for e in episodes)),
               "cycles": len(episodes), "splits": [len(set(episodes[i][0] for i in part)) for part in (train_idx, val_idx, test_idx)],
               "models": results, "feature_version": FEATURE_VERSION,
               "feature_ranges": {name: {"min": round(float(x[train_idx, column].min()), 3),
                                          "max": round(float(x[train_idx, column].max()), 3)}
                                  for column, name in enumerate(FEATURE_NAMES)},
               "selection": _dataset_info,
               "series_ids": sorted(set(str(e[6]) for e in episodes if len(e) > 6 and e[6] is not None)),
               "generator_versions": sorted(set(str(e[10]) for e in episodes if len(e) > 10 and e[10] is not None)),
               "code_version": os.environ.get("STRATEGY_CODE_VERSION", "нет данных"),
               "groups": {part: sorted(set(str(episodes[i][5] if len(episodes[i]) > 5 else episodes[i][0])
                                            for i in indices)) for part, indices in partitions.items()},
               "holdout_sha256": hashlib.sha256(holdout_file.read_bytes()).hexdigest()
                                  if os.environ.get("STRATEGY_DATASET") == "experiment" else None,
               "game_ids": {part: sorted(set(str(episodes[i][0]) for i in indices))
                            for part, indices in partitions.items()}}
    if best is None:
        return save_report(report)
    score, name, model, error = best
    # Holdout is reported, never used for selection. Activate only if better than current validation result.
    if ARTIFACT.exists():
        current = joblib.load(ARTIFACT)
        if current.get("feature_version") == FEATURE_VERSION:
            previous_score = mean_absolute_error(y[val_idx], np.maximum(0, current["model"].predict(x[val_idx])))
            if previous_score <= score:
                return save_report(report | {"status": "kept_previous", "champion": current["name"],
                                             "version": current.get("version"),
                                             "artifact_training_run_id": current.get("training_run_id"),
                                             "artifact_sha256": hashlib.sha256(ARTIFACT.read_bytes()).hexdigest()})
    artifact = {"name": name, "model": model, "validation_mae": score,
                 "error90": float(error), "version": datetime.now(timezone.utc).isoformat(),
                 "training_run_id": _run_id,
                "feature_version": FEATURE_VERSION, "report": report,
                "challengers": [c for c in candidates if c["name"] != name],
                "price_range": (float(x[train_idx, 1].min()), float(x[train_idx, 1].max())),
                "count_range": (float(x[train_idx, 0].min()), float(x[train_idx, 0].max())),
                "quality_range": (float(x[train_idx, 2].min()), float(x[train_idx, 2].max())),
                "ads_range": (float(x[train_idx, 4].min()), float(x[train_idx, 4].max()))}
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=ARTIFACT.parent, delete=False) as file:
        tmp = file.name
    try:
        joblib.dump(artifact, tmp)
        digest = hashlib.sha256(Path(tmp).read_bytes()).hexdigest()
        versioned = ARTIFACT.parent / "training-runs" / (_run_id + ".joblib")
        versioned.parent.mkdir(parents=True, exist_ok=True)
        os.link(tmp, versioned)
        os.replace(tmp, ARTIFACT)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    return save_report(report | {"champion": name, "version": artifact["version"],
                                 "artifact_sha256": digest, "artifact_file": versioned.name})


if __name__ == "__main__":
    print(json.dumps(train(), ensure_ascii=False, indent=2))
