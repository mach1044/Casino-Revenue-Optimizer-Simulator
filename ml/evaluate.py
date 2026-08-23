"""Compares baseline (no intervention) vs random-move vs GBM-policy final
casino profit across many replicate seeds -- a single run of any condition
is dominated by simulation noise (proven earlier), so this always compares
averages across replicates, never single runs.

Usage: python evaluate.py [replicates]
"""
import csv
import math
import os
import shutil
import sys
import time
from pathlib import Path

import joblib

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # repo root, for `import rl`
from rl import episode_driver  # noqa: E402

import decide_moves as dm  # noqa: E402
from episode_runner import random_decide, run_episode  # noqa: E402
from train import DELTA_PROFIT_MODEL_PATH  # noqa: E402

JAVA = os.environ.get("JAVA_EXECUTABLE", "java")
ROOT = Path(__file__).resolve().parents[1]
CLASSPATH = str(ROOT / "out")
SCENARIO = ROOT / "BatchInput" / "batch-scenario.properties"
STATE_DIR = Path(__file__).parent / "eval-state"
SEED_BASE = 900_000


def gbm_decide_factory(model, allow_kick=True):
    def decide(players, tables_by_id, players_by_table):
        decisions = dm.decide_all(model, players, tables_by_id, players_by_table, allow_kick=allow_kick)
        rows = []
        for d in decisions:
            if d["decision"] == "STAY" or d["decision"].startswith("SKIP"):
                continue
            target = d["targetGameId"] if d["decision"] == "MOVE" else ""
            rows.append([d["playerId"], d["decision"], target])
        return rows
    return decide


def run_baseline(seed, rounds=None):
    """rlBridgeEnabled left at whatever the base scenario has (false) --
    a direct, unmodified CasinoBatchRunner run, just with a distinct seed
    and output directory."""
    lines = SCENARIO.read_text(encoding="utf-8").splitlines()
    out_lines = []
    for line in lines:
        if line.strip().startswith("seed="):
            out_lines.append(f"seed={seed}")
        elif rounds is not None and line.strip().startswith("rounds="):
            out_lines.append(f"rounds={rounds}")
        elif line.strip().startswith("outputDirectory="):
            out_lines.append(f"outputDirectory={(STATE_DIR / f'baseline-{seed}').resolve().as_posix()}")
        elif line.strip().startswith("playersFile="):
            # Must be absolute: the scenario copy lives in STATE_DIR, not
            # next to the original players-input.csv, so a relative path
            # here resolves against the wrong folder (same bug class as
            # rl/episode_driver.make_scenario_copy fixed earlier).
            relative = line.split("=", 1)[1].strip()
            absolute = (SCENARIO.parent / relative).resolve().as_posix()
            out_lines.append(f"playersFile={absolute}")
        else:
            out_lines.append(line)

    scenario_copy = STATE_DIR / f"baseline-{seed}.properties"
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    scenario_copy.write_text("\n".join(out_lines) + "\n", encoding="utf-8")

    import subprocess
    result = subprocess.run([JAVA, "-cp", CLASSPATH, "CasinoBatchRunner", str(scenario_copy)],
                             check=True, capture_output=True, text=True)

    summary_path = STATE_DIR / f"baseline-{seed}" / "summary.txt"
    if not summary_path.exists():
        raise RuntimeError(f"Baseline run produced no summary.txt. stdout={result.stdout!r} stderr={result.stderr!r}")
    for line in summary_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("Casino table profit"):
            return int(line.split(":")[1].strip().replace("$", ""))
    raise RuntimeError(f"Could not find profit line in {summary_path}")


def mean(xs):
    return sum(xs) / len(xs)


def stderr(xs):
    if len(xs) < 2:
        return float("nan")
    m = mean(xs)
    variance = sum((x - m) ** 2 for x in xs) / (len(xs) - 1)
    return math.sqrt(variance) / math.sqrt(len(xs))


def main():
    replicates = int(sys.argv[1]) if len(sys.argv) > 1 else 20
    rounds = int(sys.argv[2]) if len(sys.argv) > 2 else 100
    # Lets a second batch use fresh seeds (e.g. 150) so it's additional signal,
    # not a re-run of the same 150 replicates -- output also goes to a
    # separate file so the first batch's results.csv is never clobbered.
    seed_offset = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    model = joblib.load(DELTA_PROFIT_MODEL_PATH)
    gbm_decide = gbm_decide_factory(model, allow_kick=True)
    gbm_decide_no_kick = gbm_decide_factory(model, allow_kick=False)

    conditions = ["baseline", "policy", "policy_no_kick"]
    results = {label: [] for label in conditions}
    start = time.time()

    # Write incrementally (not just at the end) so a crash mid-run -- we hit
    # a transient Windows file-lock error at replicate 75/150 once already --
    # doesn't throw away every replicate completed so far.
    suffix = f"_offset{seed_offset}" if seed_offset else ""
    results_path = Path(__file__).parent / f"evaluation_results{suffix}.csv"
    with open(results_path, "w", newline="") as results_file:
        writer = csv.writer(results_file)
        writer.writerow(["replicate"] + conditions)

        for local_i in range(replicates):
            i = local_i + seed_offset
            seed = SEED_BASE + i

            baseline_profit = run_baseline(seed, rounds=rounds)
            shutil.rmtree(STATE_DIR / f"baseline-{seed}", ignore_errors=True)
            (STATE_DIR / f"baseline-{seed}.properties").unlink(missing_ok=True)

            state_dir_policy = STATE_DIR / f"policy-{seed}"
            policy_profit = run_episode(SCENARIO, state_dir_policy, JAVA, CLASSPATH, gbm_decide,
                                         seed=seed, rounds=rounds, exchange_interval=10)
            shutil.rmtree(state_dir_policy, ignore_errors=True)
            shutil.rmtree(STATE_DIR / f"policy-{seed}-output", ignore_errors=True)

            state_dir_no_kick = STATE_DIR / f"policy-no-kick-{seed}"
            policy_no_kick_profit = run_episode(SCENARIO, state_dir_no_kick, JAVA, CLASSPATH, gbm_decide_no_kick,
                                                 seed=seed, rounds=rounds, exchange_interval=10)
            shutil.rmtree(state_dir_no_kick, ignore_errors=True)
            shutil.rmtree(STATE_DIR / f"policy-no-kick-{seed}-output", ignore_errors=True)

            results["baseline"].append(baseline_profit)
            results["policy"].append(policy_profit)
            results["policy_no_kick"].append(policy_no_kick_profit)
            writer.writerow([i, baseline_profit, policy_profit, policy_no_kick_profit])
            results_file.flush()

            elapsed = time.time() - start
            print(f"replicate {local_i + 1}/{replicates} done, elapsed {elapsed:.1f}s "
                  f"(avg {elapsed / (local_i + 1):.1f}s/replicate)")

    total_elapsed = time.time() - start
    print(f"\nTotal time: {total_elapsed:.1f}s for {replicates} replicates x {len(conditions)} conditions")

    print("\n=== Summary ===")
    for label in conditions:
        xs = results[label]
        print(f"{label:15s} mean=${mean(xs):.0f}  stderr=${stderr(xs):.0f}  "
              f"min=${min(xs)}  max=${max(xs)}")


if __name__ == "__main__":
    main()
