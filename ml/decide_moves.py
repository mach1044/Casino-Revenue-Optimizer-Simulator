"""Decides STAY / MOVE / KICK for players using the trained deltaProfit
gradient-boosting model (models/delta_profit_model.joblib).

Reads live table/player state from an RL-bridge exchange directory (reuses
rl/state.py's CSV parsing only -- no dependency on rl's training/torch code)
and, for each seated player, picks whichever of {stay, move to table T,
kick} the model predicts is most profitable:

    Delta_old  = model's predicted effect of this player on their CURRENT
                 table (removing them undoes this effect)
    Delta_new(T) = model's predicted effect of this player joining
                   candidate table T

    stay  = 0
    move to best T = -Delta_old + Delta_new(T)   (moving removes the old
                      effect and adds the new one)
    kick  = -Delta_old                            (only the removal effect)

Only game types MonteCarloDataGenerator.java was trained on are supported
(currently Blackjack/Roulette/Craps/Poker -- see features.GAME_TYPES).
Slots is deliberately excluded: it's single-player (capacity 1), so there's
no "composition" to optimize around.

Usage: python decide_moves.py <rl-exchange-directory>
"""
import sys
from collections import Counter
from pathlib import Path

import joblib
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # repo root, for `import rl`
from rl import io_protocol as proto  # noqa: E402

from features import BANKROLL_STYLES, BEHAVIOR_STYLES, GAME_TYPES  # noqa: E402
from train import DELTA_PROFIT_MODEL_PATH  # noqa: E402

SUPPORTED_GAME_TYPES = set(GAME_TYPES)  # Blackjack, Roulette, Craps


def _one_hot(value, vocabulary):
    return [1.0 if value == item else 0.0 for item in vocabulary]


def composition_of(players, exclude_player_id=None):
    """Counts by behavior style + average balance, matching the training schema."""
    others = [p for p in players if p["playerId"] != exclude_player_id]
    counts = Counter(p["behaviorStyle"] for p in others)
    avg_balance = (sum(int(p["balance"]) for p in others) / len(others)) if others else 0.0
    return {
        "countGrinder": counts.get("GRINDER", 0),
        "countObnoxious": counts.get("OBNOXIOUS", 0),
        "countWhale": counts.get("WHALE", 0),
        "countLowStakes": counts.get("LOW_STAKES", 0),
        "avgBankroll": avg_balance,
    }


def encode_row(game_type, composition, candidate_behavior, candidate_bankroll, candidate_balance):
    features = (
        _one_hot(game_type, GAME_TYPES)
        + [
            composition["countGrinder"], composition["countObnoxious"],
            composition["countWhale"], composition["countLowStakes"],
            composition["avgBankroll"],
        ]
        + _one_hot(candidate_behavior, BEHAVIOR_STYLES)
        + _one_hot(candidate_bankroll, BANKROLL_STYLES)
        + [candidate_balance]
    )
    return np.array(features, dtype=np.float64)


def predict_delta_profit(model, game_type, composition, player):
    row = encode_row(game_type, composition, player["behaviorStyle"],
                      player["bankrollStyle"], float(player["balance"])).reshape(1, -1)
    return float(model.predict(row)[0])


def decide_for_player(model, player, tables_by_id, players_by_table, allow_kick=True):
    """Single-player, single-call-at-a-time version -- kept for reference/
    small cases. decide_all() below batches everything into one predict()
    call instead; prefer that for anything with real player/table counts."""
    current_game_id = player["currentGameId"]
    if not current_game_id:
        return {"playerId": player["playerId"], "decision": "SKIP (not seated)"}

    current_table = tables_by_id[current_game_id]
    if current_table["gameType"] not in SUPPORTED_GAME_TYPES:
        return {"playerId": player["playerId"], "decision": f"SKIP (unsupported game {current_table['gameType']})"}

    old_composition = composition_of(players_by_table[current_game_id], exclude_player_id=player["playerId"])
    delta_old = predict_delta_profit(model, current_table["gameType"], old_composition, player)

    best_table_id, best_delta_new = None, float("-inf")
    for game_id, table in tables_by_id.items():
        if game_id == current_game_id or table["gameType"] != current_table["gameType"]:
            continue
        if int(table["numPlayers"]) >= int(table["capacity"]):
            continue
        composition = composition_of(players_by_table.get(game_id, []))
        delta_new = predict_delta_profit(model, table["gameType"], composition, player)
        if delta_new > best_delta_new:
            best_table_id, best_delta_new = game_id, delta_new

    return _finalize_decision(player["playerId"], delta_old, best_table_id, best_delta_new, allow_kick=allow_kick)


def _finalize_decision(player_id, delta_old, best_table_id, best_delta_new, allow_kick=True):
    stay_value = 0.0
    move_value = (-delta_old + best_delta_new) if best_table_id is not None else float("-inf")

    options = [("STAY", stay_value, None), ("MOVE", move_value, best_table_id)]
    if allow_kick:
        options.append(("KICK", -delta_old, None))

    best_action = max(options, key=lambda option: option[1])
    action, value, target = best_action
    return {
        "playerId": player_id, "decision": action, "targetGameId": target,
        "value": round(value, 2), "deltaOld": round(delta_old, 2),
        "bestDeltaNew": round(best_delta_new, 2) if best_table_id is not None else None,
    }


def load_state(state_dir):
    tables = proto.read_csv_rows(proto.tables_state_path(state_dir))
    players = proto.read_csv_rows(proto.players_state_path(state_dir))
    tables_by_id = {t["gameId"]: t for t in tables}
    players_by_table = {}
    for p in players:
        if p["currentGameId"]:
            players_by_table.setdefault(p["currentGameId"], []).append(p)
    return players, tables_by_id, players_by_table


def decide_all(model, players, tables_by_id, players_by_table, allow_kick=True):
    """Same STAY/MOVE/KICK decision per player as decide_for_player, but
    builds every (player, candidate) row up front and scores them all in a
    single model.predict() call instead of one call per row -- avoids
    scikit-learn's per-call overhead being paid hundreds of thousands of
    times for a real-size floor."""
    rows = []
    manifest = []  # parallel to rows: (player_index, "OLD" or game_id of candidate)
    skips = {}     # player_index -> decision dict, for players never scored

    for i, player in enumerate(players):
        current_game_id = player["currentGameId"]
        if not current_game_id:
            skips[i] = {"playerId": player["playerId"], "decision": "SKIP (not seated)"}
            continue
        current_table = tables_by_id[current_game_id]
        if current_table["gameType"] not in SUPPORTED_GAME_TYPES:
            skips[i] = {"playerId": player["playerId"],
                        "decision": f"SKIP (unsupported game {current_table['gameType']})"}
            continue

        old_composition = composition_of(players_by_table[current_game_id], exclude_player_id=player["playerId"])
        rows.append(encode_row(current_table["gameType"], old_composition,
                                player["behaviorStyle"], player["bankrollStyle"], float(player["balance"])))
        manifest.append((i, "OLD"))

        seen_empty_candidate = False
        for game_id, table in tables_by_id.items():
            if game_id == current_game_id or table["gameType"] != current_table["gameType"]:
                continue
            if int(table["numPlayers"]) >= int(table["capacity"]):
                continue
            if int(table["numPlayers"]) == 0:
                # Every empty table of this type looks identical to the model
                # (same all-zero composition) -- scoring more than one adds
                # no information, so only the first stands in for all of them.
                if seen_empty_candidate:
                    continue
                seen_empty_candidate = True
            composition = composition_of(players_by_table.get(game_id, []))
            rows.append(encode_row(table["gameType"], composition,
                                    player["behaviorStyle"], player["bankrollStyle"], float(player["balance"])))
            manifest.append((i, game_id))

    predictions = model.predict(np.vstack(rows)) if rows else np.array([])

    delta_old = {}
    best_candidate = {}  # player_index -> (best_table_id, best_delta_new)
    for (player_index, tag), prediction in zip(manifest, predictions):
        if tag == "OLD":
            delta_old[player_index] = prediction
            continue
        best_table_id, best_delta_new = best_candidate.get(player_index, (None, float("-inf")))
        if prediction > best_delta_new:
            best_candidate[player_index] = (tag, prediction)

    decisions = []
    for i, player in enumerate(players):
        if i in skips:
            decisions.append(skips[i])
            continue
        best_table_id, best_delta_new = best_candidate.get(i, (None, float("-inf")))
        decisions.append(_finalize_decision(player["playerId"], delta_old[i], best_table_id, best_delta_new,
                                             allow_kick=allow_kick))
    return decisions


def write_decisions_as_moves(state_dir, round_index, decisions):
    """Writes non-STAY/SKIP decisions as this round's moves file + marker --
    same file CasinoRLBridge.exchange() is already waiting on."""
    rows = []
    for decision in decisions:
        if decision["decision"] in ("STAY",) or decision["decision"].startswith("SKIP"):
            continue
        target = decision["targetGameId"] if decision["decision"] == "MOVE" else ""
        rows.append([decision["playerId"], decision["decision"], target])
    proto.atomic_write_csv(proto.moves_path(state_dir, round_index),
                            ["playerId", "action", "targetGameId"], rows)
    proto.touch_marker(proto.moves_done_path(state_dir, round_index))
    return rows


def main():
    if len(sys.argv) < 2:
        print("Usage: python decide_moves.py <rl-exchange-directory> [round_index_to_write]")
        return

    state_dir = Path(sys.argv[1])
    model = joblib.load(DELTA_PROFIT_MODEL_PATH)
    players, tables_by_id, players_by_table = load_state(state_dir)
    decisions = decide_all(model, players, tables_by_id, players_by_table)

    for decision in decisions:
        print(decision)

    if len(sys.argv) > 2:
        round_index = int(sys.argv[2])
        rows = write_decisions_as_moves(state_dir, round_index, decisions)
        print(f"\nWrote {len(rows)} move(s) to round {round_index}'s moves file")


if __name__ == "__main__":
    main()
