"""Optional inference service: falls back to Java rules if no verified artifact exists."""
import os
import json
import threading
import time

import joblib
from flask import Flask, jsonify, request
from learning import ARTIFACT, FEATURE_VERSION, features, train, load_episodes

app = Flask(__name__)
_cached = None
_mtime = None
_training_lock = threading.Lock()


def artifact():
    global _cached, _mtime
    try:
        mtime = ARTIFACT.stat().st_mtime_ns
        if _mtime != mtime:
            _cached = joblib.load(ARTIFACT)
            _mtime = mtime
        return _cached if _cached["feature_version"] == FEATURE_VERSION else None
    except (OSError, KeyError, ValueError):
        return None


@app.get("/health")
def health():
    model = artifact()
    return jsonify({"ready": model is not None, "model": model["name"] if model else None,
                    "version": model["version"] if model else None})


@app.post("/train")
def train_on_demand():
    if os.environ.get("STRATEGY_DATASET") != "experiment":
        return jsonify({"error": "only available for isolated experiments"}), 403
    if not _training_lock.acquire(blocking=False):
        return jsonify({"status": "running"}), 409

    def work():
        try:
            app.logger.info("Manual training: %s", train())
        except Exception:
            app.logger.exception("Experiment training failed")
        finally:
            _training_lock.release()

    threading.Thread(target=work, daemon=True).start()
    return jsonify({"status": "started"}), 202


@app.get("/training-report")
def training_report():
    path = ARTIFACT.with_name("last-report.json")
    if not path.exists():
        return jsonify({"status": "not_trained"})
    with path.open(encoding="utf-8") as file:
        return jsonify(json.load(file))


@app.post("/predict")
def predict():
    model = artifact()
    if model is None:
        return jsonify({"error": "no validated model"}), 503
    payload = request.get_json()
    if not isinstance(payload, dict) or not isinstance(payload.get("plans"), list) or len(payload["plans"]) > 10:
        return jsonify({"error": "invalid request"}), 400
    output = []
    comparisons = [{"name": challenger["name"], "version": model["version"], "predictions": []}
                   for challenger in model.get("challengers", [])]
    try:
        for plan in payload["plans"]:
            vector = features(payload["snapshot"], plan)
            price, count = vector[1], vector[0]
            if not (model["price_range"][0] <= price <= model["price_range"][1]
                    and model["count_range"][0] <= count <= model["count_range"][1]
                    and model["quality_range"][0] <= vector[2] <= model["quality_range"][1]
                    and model["ads_range"][0] <= vector[4] <= model["ads_range"][1]):
                output.append(None)
                for comparison in comparisons:
                    comparison["predictions"].append(None)
                continue
            estimate = max(0, float(model["model"].predict([vector])[0]))
            supply = int(payload["snapshot"]["stock"]) + int(count)
            low = min(supply, max(0, round(estimate - model["error90"])))
            typical = min(supply, round(estimate))
            high = min(supply, max(typical, round(estimate + model["error90"])))
            output.append({"low": low, "typical": typical, "high": high})
            for challenger, comparison in zip(model.get("challengers", []), comparisons):
                value = max(0, float(challenger["model"].predict([vector])[0]))
                comparison["predictions"].append({"low": min(supply, max(0, round(value - challenger["error90"]))),
                                                   "typical": min(supply, round(value)),
                                                   "high": min(supply, round(value + challenger["error90"]))})
    except (KeyError, TypeError, ValueError, OverflowError):
        return jsonify({"error": "invalid features"}), 400
    return jsonify({"source": "MODEL", "name": model["name"], "version": model["version"],
                    "predictions": output, "comparisons": comparisons})


def retrain_periodically():
    last_games = 0
    while True:
        delay = int(os.environ.get("STRATEGY_RETRAIN_SECONDS", "3600"))
        try:
            episodes = load_episodes()
            games = len(set(e[0] for e in episodes))
            if games >= 12 and (artifact() is None or games >= last_games + 3):
                report = train(episodes)
                if report["status"] in ("trained", "kept_previous", "baseline"):
                    last_games = games
                app.logger.info("Training: %s", report)
        except Exception:
            app.logger.exception("Background training failed; existing model remains active")
            delay = 60  # App may still be creating the schema on first startup.
        time.sleep(delay)


if os.environ.get("STRATEGY_AUTO_TRAIN") == "true":
    threading.Thread(target=retrain_periodically, daemon=True).start()
