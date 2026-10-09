import tempfile
import json
import hashlib
import os
import unittest
from pathlib import Path
from unittest.mock import patch
from unittest.mock import MagicMock

import learning
import server


def synthetic():
    rows = []
    for game in range(15):
        for day in range(8):
            action = {"productCount": 30 + day * 3, "price": 5 + day * 0.2,
                      "quality": 0.5, "assortment": 2, "advertisingIntensity": 0,
                      "advertisingDays": 0, "cycleDays": 3 + day // 3}
            snapshot = {"stock": day, "capacity": 10, "balance": 100,
                        "baseCost": 2, "sales": [{"sold": 3 + day, "stock": 10}],
                        "competitors": [{"price": 7}]}
            # Chosen action's outcome, deliberately different from previous sales.
            rows.append((game, learning.features(snapshot, action), 30 + day * 3 + game % 3,
                         snapshot, action))
    return rows


class TrainingTest(unittest.TestCase):
    def test_experiment_reuses_fixed_test_seed_groups_after_more_games(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(learning, "ARTIFACT", Path(directory) / "model.joblib"), \
                patch.dict(os.environ, {"STRATEGY_DATASET": "experiment"}):
            first = learning.train(synthetic())
            extra = []
            for row in synthetic()[:16]:
                extra.append((row[0] + 100, *row[1:]))
            second = learning.train(synthetic() + extra)
            self.assertEqual(first["groups"]["test"], second["groups"]["test"])
            self.assertEqual(first["holdout_sha256"], second["holdout_sha256"])

    def test_duplicate_or_missing_daily_labels_never_become_training_cycles(self):
        snapshot = json.dumps({"stock": 0, "capacity": 10, "balance": 100, "baseCost": 2})
        action = json.dumps({"productCount": 10, "price": 5, "quality": 0.5, "assortment": 1,
                             "advertisingIntensity": 0, "advertisingDays": 0, "cycleDays": 2})
        def row(episode, date, sold):
            return (episode, episode, 0, 2, snapshot, action, date, sold, episode, 1,
                    "CAUTIOUS", "Бюджетный", 45, "market-v3")
        cursor = MagicMock()
        cursor.fetchall.return_value = [row(1, 1, 2), row(1, 2, 2),
                                        row(2, 1, 3), row(2, 1, 3), row(2, 2, 3),
                                        row(3, 1, 4)]
        connection = MagicMock()
        connection.__enter__.return_value.cursor.return_value.__enter__.return_value = cursor
        with patch.dict(os.environ, {"STRATEGY_DATABASE_URL": "unused", "STRATEGY_DATASET": "experiment"}), \
                patch.object(learning.psycopg2, "connect", return_value=connection):
            episodes = learning.load_episodes()
        self.assertEqual([1], [episode[0] for episode in episodes])
        self.assertEqual(1, learning._dataset_info["duplicate_cycles"])
        self.assertEqual(1, learning._dataset_info["incomplete_cycles"])

    def test_each_training_attempt_keeps_its_report_and_artifact(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(learning, "ARTIFACT", Path(directory) / "model.joblib"):
            first = learning.train(synthetic())
            first_digest = hashlib.sha256((Path(directory) / "training-runs" /
                                           (first["training_run_id"] + ".joblib")).read_bytes()).hexdigest()
            second = learning.train(synthetic())
            self.assertNotEqual(first["training_run_id"], second["training_run_id"])
            history = Path(directory) / "training-runs"
            for report in (first, second):
                saved = json.loads((history / (report["training_run_id"] + ".json")).read_text())
                self.assertEqual(report["training_run_id"], saved["training_run_id"])
                self.assertEqual(report["models"]["LastSales"], saved["models"]["LastSales"])
                self.assertEqual(15, report["splits"][0] + report["splits"][1] + report["splits"][2])
                self.assertFalse(set(report["groups"]["train"]) & set(report["groups"]["test"]))
                for model in report["models"].values():
                    self.assertGreater(model["test"]["n"], 0)
            self.assertTrue((history / (first["training_run_id"] + ".joblib")).exists())
            self.assertEqual(first_digest, hashlib.sha256((history /
                (first["training_run_id"] + ".joblib")).read_bytes()).hexdigest())
            with patch.object(server, "ARTIFACT", learning.ARTIFACT), patch.dict(os.environ, {"STRATEGY_DATASET": "experiment"}):
                self.assertEqual(first["training_run_id"], server.app.test_client().get(
                    "/training-runs/" + first["training_run_id"]).get_json()["training_run_id"])
                self.assertEqual(400, server.app.test_client().get("/training-runs/invalid").status_code)

    def test_no_future_or_hidden_features(self):
        row = synthetic()[0]
        changed = dict(row[3], hiddenBuyerBudget=999999, gameId=9999)
        self.assertEqual(learning.features(row[3], row[4]), learning.features(changed, row[4]))

    def test_split_by_whole_games_and_train_competing_models(self):
        rows = synthetic()
        train, validation, test = learning.split_games(rows)
        groups = [set(rows[i][0] for i in indices) for indices in (train, validation, test)]
        self.assertFalse(groups[0] & groups[1] or groups[0] & groups[2] or groups[1] & groups[2])
        with tempfile.TemporaryDirectory() as directory, patch.object(learning, "ARTIFACT", Path(directory) / "model.joblib"):
            report = learning.train(rows)
            self.assertEqual({"LastSales", "Ridge", "CatBoost", "LightGBM", "RandomForest"}, set(report["models"]))
            self.assertEqual("trained", report["status"])
            self.assertTrue(learning.ARTIFACT.exists())

    def test_sparse_data_does_not_activate_model(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(learning, "ARTIFACT", Path(directory) / "model.joblib"):
            self.assertEqual("insufficient_data", learning.train(synthetic()[:20])["status"])
            self.assertFalse(learning.ARTIFACT.exists())

    def test_repeated_seed_games_stay_in_the_same_split(self):
        rows = [e + (e[0] // 2,) for e in synthetic()]
        train, validation, test = learning.split_games(rows)
        seeds = [set(rows[i][5] for i in part) for part in (train, validation, test)]
        self.assertFalse(seeds[0] & seeds[1] or seeds[0] & seeds[2] or seeds[1] & seeds[2])

    def test_predictor_exposes_verified_challengers_and_abstains_outside_training_range(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.joblib"
            with patch.object(learning, "ARTIFACT", path), patch.object(server, "ARTIFACT", path):
                self.assertEqual("trained", learning.train(synthetic())["status"])
                snapshot, action = synthetic()[0][3:5]
                response = server.app.test_client().post("/predict", json={"snapshot": snapshot,
                                                                   "plans": [action, action | {"price": 9999}]})
                self.assertEqual(200, response.status_code)
                payload = response.get_json()
                self.assertIsNotNone(payload["predictions"][0])
                self.assertIsNone(payload["predictions"][1])
                self.assertTrue(payload["comparisons"])
                self.assertTrue(all(c["predictions"][1] is None for c in payload["comparisons"]))


if __name__ == "__main__":
    unittest.main()
