"""Evaluates a trained policy, or a uniformly-random-move baseline, over
held-out seeds -- verification step C from the plan.

The third baseline (no intervention at all) needs no Python: run
`java -cp <classpath> CasinoBatchRunner <scenario.properties>` directly with
rlBridgeEnabled=false (the default) and read "Casino table profit ($)" from
summary.txt. Comparing that number against this module's "trained" and
"random" results is the actual research question.
"""
import random
from pathlib import Path
from typing import List, Optional

import torch

from . import episode_driver
from .candidates import generate_candidates
from .model import PairwiseScorer
from .policy import choose_moves
from .state import RoundState


def _random_moves(state: RoundState, preferences, move_probability: float):
    moves = []
    reserved = {game_id: table.num_players for game_id, table in state.tables.items()}
    for player in state.players:
        if not player.is_seated or random.random() >= move_probability:
            continue
        live_tables = {
            game_id: table for game_id, table in state.tables.items()
            if reserved[game_id] < table.capacity or game_id == player.current_game_id
        }
        candidates = generate_candidates(player, live_tables, preferences)
        if not candidates:
            continue
        chosen = random.choice(candidates)
        moves.append((player.player_id, chosen.game_id))
        reserved[chosen.game_id] += 1
    return moves


def run_episode(mode: str, java_executable: str, classpath: str, scenario_path: Path,
                 state_dir: Path, seed: int, model: Optional[PairwiseScorer] = None,
                 move_probability: float = 0.10, round_timeout_s: float = 30.0) -> dict:
    """mode: "trained" (requires model, runs greedy with epsilon=0) or "random"."""
    if mode == "trained" and model is None:
        raise ValueError('mode="trained" requires a model')
    if mode not in ("trained", "random"):
        raise ValueError(f"Unknown mode: {mode}")

    scenario_copy, expected_rounds = episode_driver.make_scenario_copy(
        scenario_path, state_dir, overrides={"seed": str(seed)})
    handle = episode_driver.launch_episode(
        java_executable, classpath, scenario_copy, state_dir, expected_rounds)

    last_state: Optional[RoundState] = None
    try:
        preferences = handle.wait_for_static_preferences()
        for round_index in range(expected_rounds):
            state = handle.read_round(round_index, timeout_s=round_timeout_s)
            last_state = state
            if mode == "trained":
                moves = choose_moves(model, state, preferences, stay_margin=0.0, epsilon=0.0)
            else:
                moves = _random_moves(state, preferences, move_probability)
            handle.write_moves(round_index, moves)
        handle.wait(timeout_s=60)
    finally:
        handle.kill()

    return {
        "seed": seed,
        "final_profit": last_state.total_profit() if last_state else 0,
    }


def evaluate(mode: str, java_executable: str, classpath: str, scenario_path: Path,
             episodes_dir: Path, seeds: List[int], model: Optional[PairwiseScorer] = None,
             **kwargs) -> List[dict]:
    results = []
    for seed in seeds:
        state_dir = episodes_dir / f"eval-{mode}-{seed}"
        results.append(run_episode(mode, java_executable, classpath, scenario_path,
                                    state_dir, seed, model=model, **kwargs))
    return results
