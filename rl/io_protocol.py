"""File-protocol primitives shared by every module that talks to the Java
CasinoRLBridge (Casino/CasinoRLBridge.java): path naming, atomic writes, and
polling. Every filename here must match CasinoRLBridge's Java-side naming
exactly -- round numbers are embedded in the moves/marker filenames so a
stale or late file can never be misread as the current round's.
"""
import csv
import os
import time
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple


class BridgeTimeoutError(TimeoutError):
    """Raised when a round's state or moves marker never appears in time."""


def static_preferences_path(state_dir: Path) -> Path:
    return state_dir / "static_preferences.csv"


def tables_state_path(state_dir: Path) -> Path:
    return state_dir / "tables_state.csv"


def players_state_path(state_dir: Path) -> Path:
    return state_dir / "players_state.csv"


def state_done_path(state_dir: Path, round_index: int) -> Path:
    return state_dir / f"round_{round_index}.state.done"


def moves_path(state_dir: Path, round_index: int) -> Path:
    return state_dir / f"moves_{round_index}.csv"


def moves_done_path(state_dir: Path, round_index: int) -> Path:
    return state_dir / f"round_{round_index}.moves.done"


def moves_result_path(state_dir: Path, round_index: int) -> Path:
    return state_dir / f"round_{round_index}.moves.result.csv"


def wait_for_file(path: Path, timeout_s: float, poll_s: float = 0.05) -> None:
    """Polls until `path` exists, raising BridgeTimeoutError past the deadline."""
    deadline = time.monotonic() + timeout_s
    while not path.exists():
        if time.monotonic() >= deadline:
            raise BridgeTimeoutError(
                f"Timed out after {timeout_s}s waiting for {path} "
                "-- is the Java CasinoBatchRunner process still running?")
        time.sleep(poll_s)


def atomic_write_csv(path: Path, header: Sequence[str], rows: Iterable[Sequence]) -> None:
    """Writes to a temp file then atomically renames, matching the Java side's
    tmp-then-rename convention so a reader never observes a partial write."""
    tmp_path = path.with_name(path.name + ".tmp")
    with open(tmp_path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        for row in rows:
            writer.writerow(row)
    os.replace(tmp_path, path)  # atomic on both POSIX and Windows


def touch_marker(path: Path) -> None:
    """Creates a zero-byte marker file; existence alone is the signal.
    No tmp+rename needed (unlike atomic_write_csv): an empty file has no
    content a reader could observe half-written, so a direct create is safe."""
    path.write_bytes(b"")


def read_csv_rows(path: Path) -> List[dict]:
    # Windows can transiently hold an exclusive lock on a file for a few
    # milliseconds right after it's renamed into place (commonly antivirus
    # real-time scanning) -- retry briefly instead of failing the whole run
    # on what's usually a race, not a real problem.
    last_error = None
    for attempt in range(5):
        try:
            with open(path, newline="", encoding="utf-8") as handle:
                return list(csv.DictReader(handle))
        except PermissionError as error:
            last_error = error
            time.sleep(0.05 * (attempt + 1))
    raise last_error


def write_moves(state_dir: Path, round_index: int, moves: Iterable[Tuple[int, int]]) -> None:
    """Writes this round's move decisions and signals Java with the .done marker.

    A player with no row here is left where they are ("stay").
    """
    atomic_write_csv(moves_path(state_dir, round_index),
                      ["playerId", "targetGameId"], moves)
    touch_marker(moves_done_path(state_dir, round_index))


def read_moves_result(state_dir: Path, round_index: int) -> Dict[int, str]:
    """Reads Java's echo of what actually happened to each requested move.

    Necessary because two moves can legally target the same near-full table
    from one snapshot; Java applies them in file order and the loser is
    rejected -- this lets training code avoid crediting a move that never
    actually landed.
    """
    results: Dict[int, str] = {}
    for row in read_csv_rows(moves_result_path(state_dir, round_index)):
        results[int(row["playerId"])] = row["result"]
    return results
