"""Outer training loop: runs episodes, TD-updates the pairwise scorer.

TD pairing, spelled out because it's easy to get an off-by-one here: round t
contributes reward r_t = totalProfit(after t) - totalProfit(after t-1) and a
realized-placement score V_t. The update at round t uses r_{t-1} (computed
and stashed during the *previous* iteration) together with this round's V_t
to form the bootstrap target for V_{t-1}:

    target = r_{t-1} + gamma * V_t          (target detached, no gradient)
    loss   = mean_i (V_{t-1}(i) - target)^2

Every player seated in round t-1 shares that same target but contributes
gradient through their own features -- this is the mechanism by which the
network can eventually learn that features correlated with "an Obnoxious
player is nearby" predict lower future value, without any reward ever being
attributed to a specific player or move.

Implementation note: we carry raw features (numpy arrays) across the
iteration boundary, not the score tensors themselves. Adam (like most
PyTorch optimizers) updates parameters in place, so a tensor computed in a
previous iteration becomes stale the moment optimizer.step() runs -- trying
to backward() through it on a later iteration raises a "modified by an
inplace operation" autograd error. Recomputing the forward pass fresh, right
before each backward() call, avoids that entirely.
"""
from pathlib import Path
from typing import List, Optional, Tuple

import numpy as np
import torch

from . import episode_driver
from .features import player_features, table_features
from .model import PairwiseScorer
from .policy import choose_moves

FeaturePairs = List[Tuple[np.ndarray, np.ndarray]]


def _batched_score(model: PairwiseScorer, pairs: FeaturePairs) -> torch.Tensor:
    player_batch = torch.from_numpy(np.stack([p for p, _ in pairs]))
    table_batch = torch.from_numpy(np.stack([t for _, t in pairs]))
    return model(player_batch, table_batch)


def run_episode(model: PairwiseScorer, optimizer: torch.optim.Optimizer,
                 java_executable: str, classpath: str, scenario_path: Path,
                 episodes_dir: Path, episode_index: int,
                 gamma: float = 0.97, stay_margin: float = 0.02,
                 epsilon: float = 0.10, round_timeout_s: float = 30.0) -> dict:
    state_dir = episodes_dir / f"episode-{episode_index}"
    scenario_copy, expected_rounds = episode_driver.make_scenario_copy(
        scenario_path, state_dir, overrides={"seed": str(1_000_000 + episode_index)})
    handle = episode_driver.launch_episode(
        java_executable, classpath, scenario_copy, state_dir, expected_rounds)

    previous_pairs: Optional[FeaturePairs] = None   # round t-1's realized (player, table) features
    previous_reward: Optional[float] = None         # r_{t-1}
    previous_total_profit = 0.0
    total_loss = 0.0
    td_updates = 0

    try:
        preferences = handle.wait_for_static_preferences()

        for round_index in range(expected_rounds):
            state = handle.read_round(round_index, timeout_s=round_timeout_s)

            current_pairs: FeaturePairs = []
            for player in state.players:
                if not player.is_seated:
                    continue
                table = state.tables[player.current_game_id]
                current_pairs.append((player_features(player), table_features(table)))

            current_total_profit = state.total_profit()
            reward = current_total_profit - previous_total_profit
            previous_total_profit = current_total_profit

            if previous_pairs:
                with torch.no_grad():
                    next_value = (_batched_score(model, current_pairs).mean()
                                  if current_pairs else torch.tensor(0.0))
                    target = previous_reward + gamma * next_value

                predictions = _batched_score(model, previous_pairs)
                loss = torch.mean((predictions - target) ** 2)
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
                total_loss += loss.item()
                td_updates += 1

            previous_pairs = current_pairs
            previous_reward = reward

            moves = choose_moves(model, state, preferences,
                                  stay_margin=stay_margin, epsilon=epsilon)
            handle.write_moves(round_index, moves)

        handle.wait(timeout_s=60)
    finally:
        handle.kill()

    return {
        "rounds": expected_rounds,
        "mean_td_loss": (total_loss / td_updates) if td_updates else None,
        "final_profit": previous_total_profit,
    }


def train(scenario_path: Path, episodes_dir: Path, java_executable: str = "java",
          classpath: str = ".", num_episodes: int = 500,
          checkpoint_every: int = 25, checkpoint_path: Optional[Path] = None,
          **episode_kwargs) -> PairwiseScorer:
    model = PairwiseScorer()
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)

    for episode_index in range(num_episodes):
        stats = run_episode(model, optimizer, java_executable, classpath,
                             scenario_path, episodes_dir, episode_index, **episode_kwargs)
        print(f"episode {episode_index}: rounds={stats['rounds']} "
              f"loss={stats['mean_td_loss']} profit={stats['final_profit']}")

        if checkpoint_path and (episode_index + 1) % checkpoint_every == 0:
            torch.save(model.state_dict(), checkpoint_path)

    return model
