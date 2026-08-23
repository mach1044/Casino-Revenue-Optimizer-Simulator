"""Parses the per-round CSV state Java's CasinoRLBridge exports into typed
Python objects. Column names/order here must match CasinoRLBridge.java's
buildTablesState/buildPlayersState/writeStaticPreferences exactly.
"""
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from . import io_protocol as proto


def _opt_int(value: str) -> Optional[int]:
    return int(value) if value not in ("", None) else None


def _opt_float(value: str) -> Optional[float]:
    return float(value) if value not in ("", None) else None


@dataclass(frozen=True)
class TableState:
    game_id: int
    game_type: str
    capacity: int
    min_bet: int
    max_bet: int
    num_players: int
    profit: int
    poker_rake_rate: Optional[float]
    poker_rake_cap: Optional[int]
    slot_rtp: Optional[float]

    @property
    def occupancy(self) -> float:
        return self.num_players / self.capacity if self.capacity else 0.0

    @property
    def has_room(self) -> bool:
        return self.num_players < self.capacity


@dataclass(frozen=True)
class PlayerState:
    player_id: int
    name: str
    bankroll_style: str
    behavior_style: str
    current_game_id: Optional[int]
    current_game_type: Optional[str]
    balance: int
    mood: float
    momentum: float
    social_score: float
    tilt: float
    win_streak: int
    loss_streak: int
    rounds_at_table: int
    rounds_played: int
    ticks_in_casino: int
    table_switches: int
    starting_balance: int
    session_starting_balance: Optional[int]
    session_total_wagered: Optional[int]
    session_total_winnings: Optional[int]
    poker_participation: float
    poker_aggression: float
    poker_skill: float

    @property
    def is_seated(self) -> bool:
        return self.current_game_id is not None


@dataclass
class RoundState:
    round_index: int
    tables: Dict[int, TableState]
    players: List[PlayerState]

    def total_profit(self) -> int:
        """Cumulative casino table profit as of this round -- diff two
        consecutive RoundStates' total_profit() to get a per-round reward."""
        return sum(table.profit for table in self.tables.values())


def load_static_preferences(state_dir: Path) -> Dict[Tuple[int, int], float]:
    """(playerId, gameId) -> preferenceWeight, written once before round 0."""
    rows = proto.read_csv_rows(proto.static_preferences_path(state_dir))
    return {
        (int(row["playerId"]), int(row["gameId"])): float(row["preferenceWeight"])
        for row in rows
    }


def _parse_table(row: dict) -> TableState:
    return TableState(
        game_id=int(row["gameId"]),
        game_type=row["gameType"],
        capacity=int(row["capacity"]),
        min_bet=int(row["minBet"]),
        max_bet=int(row["maxBet"]),
        num_players=int(row["numPlayers"]),
        profit=int(row["profit"]),
        poker_rake_rate=_opt_float(row["pokerRakeRate"]),
        poker_rake_cap=_opt_int(row["pokerRakeCap"]),
        slot_rtp=_opt_float(row["slotRtp"]),
    )


def _parse_player(row: dict) -> PlayerState:
    return PlayerState(
        player_id=int(row["playerId"]),
        name=row["name"],
        bankroll_style=row["bankrollStyle"],
        behavior_style=row["behaviorStyle"],
        current_game_id=_opt_int(row["currentGameId"]),
        current_game_type=row["currentGameType"] or None,
        balance=int(row["balance"]),
        mood=float(row["mood"]),
        momentum=float(row["momentum"]),
        social_score=float(row["socialScore"]),
        tilt=float(row["tilt"]),
        win_streak=int(row["winStreak"]),
        loss_streak=int(row["lossStreak"]),
        rounds_at_table=int(row["roundsAtTable"]),
        rounds_played=int(row["roundsPlayed"]),
        ticks_in_casino=int(row["ticksInCasino"]),
        table_switches=int(row["tableSwitches"]),
        starting_balance=int(row["startingBalance"]),
        session_starting_balance=_opt_int(row["sessionStartingBalance"]),
        session_total_wagered=_opt_int(row["sessionTotalWagered"]),
        session_total_winnings=_opt_int(row["sessionTotalWinnings"]),
        poker_participation=float(row["pokerParticipation"]),
        poker_aggression=float(row["pokerAggression"]),
        poker_skill=float(row["pokerSkill"]),
    )


def load_round_state(state_dir: Path, round_index: int,
                      timeout_s: float = 30.0, poll_s: float = 0.05) -> RoundState:
    """Waits for round_index's state marker, then parses both CSVs.

    Raises io_protocol.BridgeTimeoutError if Java never produces this round
    (e.g. the episode ended after the previous round).
    """
    proto.wait_for_file(proto.state_done_path(state_dir, round_index), timeout_s, poll_s)
    tables = {
        table.game_id: table
        for table in (_parse_table(row)
                      for row in proto.read_csv_rows(proto.tables_state_path(state_dir)))
    }
    players = [_parse_player(row)
               for row in proto.read_csv_rows(proto.players_state_path(state_dir))]
    return RoundState(round_index=round_index, tables=tables, players=players)
