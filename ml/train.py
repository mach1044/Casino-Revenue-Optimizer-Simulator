"""Trains gradient-boosted regressors on the Monte Carlo dataset produced by
Casino/MonteCarloDataGenerator.java: predict the effect of adding a
candidate player to a table (on the other players' mood, and on table
profit) from the table's composition and the candidate's traits.

Usage:
    python train.py [path/to/training_data.csv]
"""
import sys
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split

from features import FEATURE_NAMES, encode_features, encode_targets, load_rows

MODELS_DIR = Path(__file__).parent / "models"
DELTA_PROFIT_MODEL_PATH = MODELS_DIR / "delta_profit_model.joblib"
DELTA_MOOD_MODEL_PATH = MODELS_DIR / "delta_mood_model.joblib"


def train_and_report(X_train, X_test, y_train, y_test, label: str) -> GradientBoostingRegressor:
    model = GradientBoostingRegressor(
        # 100 trees cross-validated better than 300 (0.381 vs 0.341 R^2) --
        # the extra trees were overfitting, not helping.
        n_estimators=100, max_depth=3, learning_rate=0.05, random_state=0
    )
    model.fit(X_train, y_train)
    predictions = model.predict(X_test)

    print(f"\n=== {label} ===")
    print(f"R^2:  {r2_score(y_test, predictions):.4f}")
    print(f"MAE:  {mean_absolute_error(y_test, predictions):.4f}")
    print(f"held-out target std: {np.std(y_test):.4f}  "
          f"(MAE should be well below this to show real skill, not just guessing the mean)")

    importances = sorted(zip(FEATURE_NAMES, model.feature_importances_),
                          key=lambda pair: pair[1], reverse=True)
    print("Top feature importances:")
    for name, importance in importances[:10]:
        print(f"  {name:28s} {importance:.4f}")

    return model


def main():
    csv_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("training_data.csv")
    rows = load_rows(csv_path)
    print(f"Loaded {len(rows)} rows from {csv_path}")

    X = encode_features(rows)
    delta_mood, delta_profit = encode_targets(rows)

    X_train, X_test, mood_train, mood_test, profit_train, profit_test = train_test_split(
        X, delta_mood, delta_profit, test_size=0.2, random_state=0
    )

    mood_model = train_and_report(X_train, X_test, mood_train, mood_test, "deltaMood")
    profit_model = train_and_report(X_train, X_test, profit_train, profit_test, "deltaProfit")

    MODELS_DIR.mkdir(exist_ok=True)
    joblib.dump(mood_model, DELTA_MOOD_MODEL_PATH)
    joblib.dump(profit_model, DELTA_PROFIT_MODEL_PATH)
    print(f"\nSaved models to {DELTA_MOOD_MODEL_PATH} and {DELTA_PROFIT_MODEL_PATH}")

    return mood_model, profit_model


if __name__ == "__main__":
    main()
