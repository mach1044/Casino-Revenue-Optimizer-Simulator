"""Shared round-by-round episode driver for evaluation conditions (random,
GBM policy). Reuses rl/episode_driver.py + rl/io_protocol.py plumbing only.
"""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # repo root, for `import rl`
from rl import episode_driver  # noqa: E402
from rl import io_protocol as proto  # noqa: E402


def run_episode(scenario_path, state_dir, java_executable, classpath, decide_fn,
                 seed=None, rounds=None, round_timeout_s=30.0, exchange_interval=10, poll_s=0.005):
    """exchange_interval: Java only pauses for a Python decision every this
    many rounds (not every round) -- cuts polling overhead roughly
    proportionally, not just decision-computation cost. poll_s: how often
    each side checks for the other's file; lower = less polling latency at
    the cost of more frequent (cheap) file-existence checks. rounds:
    overrides the scenario file's own round count when given."""
    overrides = {"rlExchangeIntervalRounds": str(exchange_interval),
                 "rlPollIntervalMillis": str(max(1, int(poll_s * 1000)))}
    if seed is not None:
        overrides["seed"] = str(seed)
    if rounds is not None:
        overrides["rounds"] = str(rounds)
    scenario_copy, expected_rounds = episode_driver.make_scenario_copy(
        scenario_path, state_dir, overrides=overrides)
    handle = episode_driver.launch_episode(java_executable, classpath, scenario_copy, state_dir, expected_rounds)
    try:
        handle.wait_for_static_preferences()
        for round_index in range(0, expected_rounds, exchange_interval):
            proto.wait_for_file(proto.state_done_path(state_dir, round_index),
                                 timeout_s=round_timeout_s, poll_s=poll_s)
            tables = proto.read_csv_rows(proto.tables_state_path(state_dir))
            players = proto.read_csv_rows(proto.players_state_path(state_dir))
            tables_by_id = {t["gameId"]: t for t in tables}
            players_by_table = {}
            for p in players:
                if p["currentGameId"]:
                    players_by_table.setdefault(p["currentGameId"], []).append(p)
            rows = decide_fn(players, tables_by_id, players_by_table)
            proto.atomic_write_csv(proto.moves_path(state_dir, round_index),
                                    ["playerId", "action", "targetGameId"], rows)
            proto.touch_marker(proto.moves_done_path(state_dir, round_index))
        handle.wait(timeout_s=60)
    finally:
        handle.kill()

    final_tables = proto.read_csv_rows(proto.tables_state_path(state_dir))
    return sum(int(t["profit"]) for t in final_tables)


def random_decide(players, tables_by_id, players_by_table, move_probability=0.10):
    """Same-game-type random legal move, no kicks -- the sanity-floor baseline."""
    rows = []
    reserved = {gid: int(t["numPlayers"]) for gid, t in tables_by_id.items()}
    for player in players:
        if not player["currentGameId"] or random.random() >= move_probability:
            continue
        current_type = tables_by_id[player["currentGameId"]]["gameType"]
        candidates = [
            gid for gid, t in tables_by_id.items()
            if gid != player["currentGameId"] and t["gameType"] == current_type
            and reserved[gid] < int(t["capacity"])
            and float(player["balance"]) >= float(t["minBet"])
        ]
        if not candidates:
            continue
        target = random.choice(candidates)
        rows.append([player["playerId"], "MOVE", target])
        reserved[target] += 1
    return rows
