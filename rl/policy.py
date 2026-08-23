"""Turns the pairwise scorer into an actual move decision each round.

Separate from the TD loss in rl/train.py: this scores *candidate*
(not-yet-realized) pairs and argmaxes, independent of how the network is
being fit.
"""
import random
from typing import Dict, List, Tuple

import torch

from .candidates import generate_candidates
from .features import player_features, table_features
from .model import PairwiseScorer
from .state import RoundState, TableState


def choose_moves(model: PairwiseScorer, state: RoundState,
                  preferences: Dict[Tuple[int, int], float],
                  stay_margin: float = 0.0, epsilon: float = 0.0
                  ) -> List[Tuple[int, int]]:
    """Returns (playerId, targetGameId) pairs; a player with no entry stays.

    Reserves a chosen destination's capacity as it assigns moves within this
    round so two players are never sent to the same table's last open seat
    from one snapshot -- Casino.movePlayer on the Java side remains the
    authoritative backstop (applied in file order) either way.
    """
    reserved_occupancy = {game_id: table.num_players for game_id, table in state.tables.items()}
    moves: List[Tuple[int, int]] = []

    seated_players = [player for player in state.players if player.is_seated]
    random.shuffle(seated_players)  # don't always favor low player ids for scarce seats

    with torch.no_grad():
        for player in seated_players:
            live_tables = {
                game_id: table for game_id, table in state.tables.items()
                if reserved_occupancy[game_id] < table.capacity or game_id == player.current_game_id
            }
            candidates = generate_candidates(player, live_tables, preferences)
            if not candidates:
                continue

            if epsilon > 0 and random.random() < epsilon:
                chosen = random.choice(candidates)
                moves.append((player.player_id, chosen.game_id))
                reserved_occupancy[chosen.game_id] += 1
                continue

            player_feat = torch.from_numpy(player_features(player)).unsqueeze(0)
            current_table = state.tables[player.current_game_id]
            stay_score = model(player_feat,
                                torch.from_numpy(table_features(current_table)).unsqueeze(0)).item()

            best_table: TableState = None
            best_score = stay_score + stay_margin
            for table in candidates:
                score = model(player_feat, torch.from_numpy(table_features(table)).unsqueeze(0)).item()
                if score > best_score:
                    best_score = score
                    best_table = table

            if best_table is not None:
                moves.append((player.player_id, best_table.game_id))
                reserved_occupancy[best_table.game_id] += 1

    return moves
