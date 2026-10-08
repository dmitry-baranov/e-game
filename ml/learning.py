"""Train only on opted-in finished games, using snapshots captured before the actual action."""
import json
import os
import tempfile
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


def save_report(report):
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    path = ARTIFACT.with_name("last-report.json")
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=ARTIFACT.parent, delete=False) as file:
        json.dump(report | {"created_at": datetime.now(timezone.utc).isoformat()}, file, indent=2)
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
                       """ + group_column + """ AS scenario_seed
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
    for eid, game, day, cycle, snapshot, action, trade_day, sold, scenario_seed in rows:
        row = groups.setdefault(eid, {"game": game, "day": day, "cycle": cycle,
                                      "snapshot": snapshot, "action": action, "seed": scenario_seed,
                                      "sales": [], "days": set()})
        if trade_day is not None and trade_day not in row["days"] and sold is not None:
            row["days"].add(trade_day)
            row["sales"].append(sold)
    episodes = []
    for row in groups.values():
        # Missing trading days are not zero sales; skip incomplete cycles.
        if len(row["days"]) != row["cycle"]:
            continue
        snapshot, action = json.loads(row["snapshot"]), json.loads(row["action"])
        episodes.append((row["game"], features(snapshot, action), sum(row["sales"]), snapshot, action,
                         row["seed"]))
    return episodes


def split_games(episodes):
    games = np.array([e[5] if len(e) > 5 else e[0] for e in episodes])
    all_indices = np.arange(len(episodes))
    train_val, test = next(GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
                           .split(all_indices, groups=games))
    train_offset, val_offset = next(GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=43)
                                    .split(train_val, groups=games[train_val]))
    return train_val[train_offset], train_val[val_offset], test


def train(episodes=None):
    episodes = load_episodes() if episodes is None else episodes
    if len(episodes) < 60 or len(set(e[0] for e in episodes)) < MIN_GAMES or \
            len(set(e[5] if len(e) > 5 else e[0] for e in episodes)) < 5:
        return save_report({"status": "insufficient_data", "games": len(set(e[0] for e in episodes)), "cycles": len(episodes)})
    train_idx, val_idx, test_idx = split_games(episodes)
    x = np.asarray([e[1] for e in episodes], dtype=float)
    y = np.asarray([e[2] for e in episodes], dtype=float)
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
    results = {"LastSales": {"validation_mae": round(baseline_val, 3),
                             "test_mae": round(mean_absolute_error(y[test_idx], baseline(test_idx)), 3)}}
    best = None
    candidates = []
    for name, model in models.items():
        model.fit(x[train_idx], y[train_idx])
        prediction = np.maximum(0, model.predict(x[val_idx]))
        score = mean_absolute_error(y[val_idx], prediction)
        results[name] = {"validation_mae": round(score, 3),
                         "test_mae": round(mean_absolute_error(y[test_idx], np.maximum(0, model.predict(x[test_idx]))), 3)}
        if score < baseline_val:
            candidates.append({"name": name, "model": model,
                               "error90": float(np.quantile(abs(y[val_idx] - prediction), 0.9)),
                               "validation_mae": float(score)})
        if score < baseline_val and (best is None or score < best[0]):
            best = (score, name, model, np.quantile(abs(y[val_idx] - prediction), 0.9))
    report = {"status": "baseline" if best is None else "trained", "dataset": os.environ.get("STRATEGY_DATASET", "consented"),
              "games": len(set(e[0] for e in episodes)),
              "cycles": len(episodes), "splits": [len(set(episodes[i][0] for i in part)) for part in (train_idx, val_idx, test_idx)],
              "models": results, "feature_version": FEATURE_VERSION}
    if best is None:
        return save_report(report)
    score, name, model, error = best
    # Holdout is reported, never used for selection. Activate only if better than current validation result.
    if ARTIFACT.exists():
        current = joblib.load(ARTIFACT)
        if current.get("feature_version") == FEATURE_VERSION:
            previous_score = mean_absolute_error(y[val_idx], np.maximum(0, current["model"].predict(x[val_idx])))
            if previous_score <= score:
                return save_report(report | {"status": "kept_previous", "champion": current["name"]})
    artifact = {"name": name, "model": model, "validation_mae": score,
                "error90": float(error), "version": datetime.now(timezone.utc).isoformat(),
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
        os.replace(tmp, ARTIFACT)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    return save_report(report | {"champion": name, "version": artifact["version"]})


if __name__ == "__main__":
    print(json.dumps(train(), ensure_ascii=False, indent=2))
