"""Shared feature encoding for the Monte Carlo + gradient boosting pipeline.
Standalone from rl/ -- reads MonteCarloDataGenerator.java's CSV output only.
"""
import csv
from pathlib import Path

import numpy as np

GAME_TYPES = ["Blackjack", "Roulette", "Craps", "Poker"]
BEHAVIOR_STYLES = ["WHALE", "GRINDER", "LOW_STAKES", "OBNOXIOUS"]
BANKROLL_STYLES = ["HIGH_ROLLER", "EVEN_STEVEN", "LOW_ROLLER"]

FEATURE_NAMES = (
    [f"game_{g}" for g in GAME_TYPES]
    + ["countGrinder", "countObnoxious", "countWhale", "countLowStakes", "avgBankroll"]
    + [f"candidateBehavior_{b}" for b in BEHAVIOR_STYLES]
    + [f"candidateBankroll_{b}" for b in BANKROLL_STYLES]
    + ["candidateBalance"]
)


def _one_hot(value, vocabulary):
    return [1.0 if value == item else 0.0 for item in vocabulary]


def load_rows(csv_path: Path):
    with open(csv_path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def encode_features(rows):
    features = []
    for row in rows:
        features.append(
            _one_hot(row["gameType"], GAME_TYPES)
            + [
                float(row["countGrinder"]),
                float(row["countObnoxious"]),
                float(row["countWhale"]),
                float(row["countLowStakes"]),
                float(row["avgBankroll"]),
            ]
            + _one_hot(row["candidateBehaviorStyle"], BEHAVIOR_STYLES)
            + _one_hot(row["candidateBankrollStyle"], BANKROLL_STYLES)
            + [float(row["candidateBalance"])]
        )
    return np.array(features, dtype=np.float64)


def encode_targets(rows):
    delta_mood = np.array([float(r["deltaMood"]) for r in rows])
    delta_profit = np.array([float(r["deltaProfit"]) for r in rows])
    return delta_mood, delta_profit
