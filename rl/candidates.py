"""Candidate destination tables for one player, mirroring the eligibility
rule CasinoSimulationEngine.runPreGameStage uses for a player's own
voluntary table choice (capacity + affordability), plus the casino's design
choice that a relocation defaults to the player's current game type and
only considers a cross-game move when the player has a genuine (positive)
preference for that other game -- reusing Player.getGameChoiceWeight via the
static_preferences.csv export rather than inventing a new preference model.
"""
from typing import Dict, List, Tuple

from .state import PlayerState, TableState


def generate_candidates(player: PlayerState, tables: Dict[int, TableState],
                         preferences: Dict[Tuple[int, int], float]) -> List[TableState]:
    if not player.is_seated:
        return []

    def eligible(table: TableState) -> bool:
        return (table.game_id != player.current_game_id
                and table.has_room
                and player.balance >= table.min_bet)

    same_game = [table for table in tables.values()
                 if table.game_type == player.current_game_type and eligible(table)]
    cross_game = [table for table in tables.values()
                  if table.game_type != player.current_game_type and eligible(table)
                  and preferences.get((player.player_id, table.game_id), 0.0) > 0]
    return same_game + cross_game
