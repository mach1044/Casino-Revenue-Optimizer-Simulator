"""Fixed-width feature vectors for the pairwise scorer. Width must not depend
on how many players/tables a given scenario has, so the network's input
layer is stable across scenarios of different size.
"""
from typing import List

import numpy as np

from .state import PlayerState, TableState

# Fixed vocabularies, matching the Java enum .name()s / Game class simple
# names written into players_state.csv / tables_state.csv.
BANKROLL_STYLES = ["HIGH_ROLLER", "EVEN_STEVEN", "LOW_ROLLER"]
BEHAVIOR_STYLES = ["WHALE", "GRINDER", "LOW_STAKES", "OBNOXIOUS"]
GAME_TYPES = ["Blackjack", "Roulette", "Slots", "Craps", "Poker"]

PLAYER_FEATURE_DIM = (
    1                        # balance, normalized by starting balance
    + len(BANKROLL_STYLES)
    + len(BEHAVIOR_STYLES)
    + 4                      # mood, momentum, socialScore, tilt
    + 1                      # win streak (normalized)
    + 1                      # loss streak (normalized)
    + 1                      # rounds at table (normalized)
    + 1                      # ticks in casino (normalized)
    + 1                      # table switches (normalized)
    + 1                      # visit return fraction, clamped to [-1, 1]
    + 3                      # poker participation / aggression / skill
)

TABLE_FEATURE_DIM = (
    len(GAME_TYPES)
    + 1                      # occupancy fraction
    + 1                      # normalized min bet
    + 1                      # normalized max bet
    + 1                      # normalized capacity
    + 1                      # poker rake rate (0 when N/A)
    + 1                      # slot RTP (0 when N/A)
)


def _one_hot(value: str, vocabulary: List[str]) -> List[float]:
    return [1.0 if value == item else 0.0 for item in vocabulary]


def _normalize(value: float, scale: float) -> float:
    return float(value) / scale if scale else 0.0


def player_features(player: PlayerState) -> np.ndarray:
    starting = max(1, player.starting_balance)
    visit_return = (player.balance - player.starting_balance) / starting
    features = (
        [_normalize(player.balance, starting)]
        + _one_hot(player.bankroll_style, BANKROLL_STYLES)
        + _one_hot(player.behavior_style, BEHAVIOR_STYLES)
        + [player.mood, player.momentum, player.social_score, player.tilt]
        + [_normalize(player.win_streak, 10.0)]
        + [_normalize(player.loss_streak, 10.0)]
        + [_normalize(player.rounds_at_table, 100.0)]
        + [_normalize(player.ticks_in_casino, 300.0)]
        + [_normalize(player.table_switches, 5.0)]
        + [max(-1.0, min(1.0, visit_return))]
        + [player.poker_participation, player.poker_aggression, player.poker_skill]
    )
    assert len(features) == PLAYER_FEATURE_DIM
    return np.array(features, dtype=np.float32)


def table_features(table: TableState) -> np.ndarray:
    features = (
        _one_hot(table.game_type, GAME_TYPES)
        + [table.occupancy]
        + [_normalize(table.min_bet, 100.0)]
        + [_normalize(table.max_bet, 500.0)]
        + [_normalize(table.capacity, 10.0)]
        + [table.poker_rake_rate or 0.0]
        + [table.slot_rtp or 0.0]
    )
    assert len(features) == TABLE_FEATURE_DIM
    return np.array(features, dtype=np.float32)
