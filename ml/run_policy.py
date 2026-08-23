"""Runs a full CasinoBatchRunner episode driven by the gradient-boosting
decision model (STAY/MOVE/KICK, re-decided every `exchange_interval`
rounds). Thin wrapper around episode_runner.run_episode.

Usage: python run_policy.py <scenario.properties> [java_executable] [classpath]
"""
import sys
from pathlib import Path

import joblib

from episode_runner import run_episode
import decide_moves as dm
from train import DELTA_PROFIT_MODEL_PATH


def gbm_decide_factory(model):
    def decide(players, tables_by_id, players_by_table):
        decisions = dm.decide_all(model, players, tables_by_id, players_by_table)
        rows = []
        for d in decisions:
            if d["decision"] == "STAY" or d["decision"].startswith("SKIP"):
                continue
            target = d["targetGameId"] if d["decision"] == "MOVE" else ""
            rows.append([d["playerId"], d["decision"], target])
        return rows
    return decide


def main():
    if len(sys.argv) < 2:
        print("Usage: python run_policy.py <scenario.properties> [java_executable] [classpath]")
        return
    scenario_path = Path(sys.argv[1])
    java_executable = sys.argv[2] if len(sys.argv) > 2 else "java"
    classpath = sys.argv[3] if len(sys.argv) > 3 else "out"
    state_dir = Path(__file__).parent / "policy-run-state"

    model = joblib.load(DELTA_PROFIT_MODEL_PATH)
    final_profit = run_episode(scenario_path, state_dir, java_executable, classpath,
                                gbm_decide_factory(model))
    print(f"Episode complete. Final total profit=${final_profit}")


if __name__ == "__main__":
    main()
