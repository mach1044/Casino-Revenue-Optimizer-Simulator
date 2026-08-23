"""Launches one CasinoBatchRunner subprocess per training/eval episode and
wraps its RL exchange directory. One fresh JVM per episode keeps the file
protocol strictly round-scoped -- no persistent-JVM / episode-reset protocol
is needed.
"""
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, Optional

from . import io_protocol as proto
from .state import RoundState, load_round_state, load_static_preferences


class EpisodeHandle:
    def __init__(self, process: subprocess.Popen, state_dir: Path,
                 scenario_path: Path, expected_rounds: int):
        self.process = process
        self.state_dir = state_dir
        self.scenario_path = scenario_path
        self.expected_rounds = expected_rounds

    def wait_for_static_preferences(self, timeout_s: float = 30.0):
        proto.wait_for_file(proto.static_preferences_path(self.state_dir), timeout_s)
        return load_static_preferences(self.state_dir)

    def read_round(self, round_index: int, timeout_s: float = 30.0) -> RoundState:
        return load_round_state(self.state_dir, round_index, timeout_s=timeout_s)

    def write_moves(self, round_index: int, moves) -> None:
        proto.write_moves(self.state_dir, round_index, moves)

    def read_result(self, round_index: int) -> Dict[int, str]:
        return proto.read_moves_result(self.state_dir, round_index)

    def is_running(self) -> bool:
        return self.process.poll() is None

    def wait(self, timeout_s: Optional[float] = None) -> int:
        return self.process.wait(timeout=timeout_s)

    def kill(self) -> None:
        if self.is_running():
            self.process.kill()


def make_scenario_copy(base_scenario_path: Path, rl_state_directory: Path,
                        overrides: Optional[dict] = None):
    """Writes a temporary scenario file with the RL bridge forced on.

    Copies every key from the base scenario, forces rlBridgeEnabled=true and
    rlStateDirectory to a caller-chosen unique path (one per episode), and
    applies any additional overrides (e.g. a distinct seed per episode).
    Returns (scenario_copy_path, effective_rounds) -- effective_rounds lets
    the caller drive an exact-count loop instead of polling process exit,
    which would otherwise add a full round-timeout of dead time per episode.
    """
    lines = base_scenario_path.read_text(encoding="utf-8").splitlines()
    overrides = dict(overrides or {})
    overrides["rlBridgeEnabled"] = "true"
    # Must be absolute (Java resolves a relative rlStateDirectory against the
    # scenario file's own directory, which is this temp copy's folder, not
    # our caller's cwd) and forward-slashed (.properties files treat
    # backslash as an escape character, so a raw Windows path like
    # "BatchResults\rl-run\episode-0" gets silently mangled on load).
    resolved_state_dir = Path(rl_state_directory).resolve()
    overrides["rlStateDirectory"] = resolved_state_dir.as_posix()
    # Keep each episode's batch-summary CSVs colocated with its exchange
    # directory instead of resolving against the temp scenario copy's folder
    # (where they'd otherwise pile up unbounded across episodes).
    if "outputDirectory" not in overrides:
        overrides["outputDirectory"] = (resolved_state_dir.parent
                                         / f"{resolved_state_dir.name}-output").as_posix()

    existing = {}
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        existing[key.strip()] = value.strip()
    effective_rounds = int(overrides.get("rounds", existing.get("rounds", 500)))

    # A relative playersFile is resolved against the scenario file's own
    # folder -- rewrite it to an absolute path against the *original*
    # scenario's folder (where the CSV actually lives) before copying, for
    # the same reason rlStateDirectory must be absolute above.
    if "playersFile" not in overrides and existing.get("playersFile", "").strip():
        players_file = Path(existing["playersFile"].strip())
        if not players_file.is_absolute():
            players_file = (base_scenario_path.parent / players_file).resolve()
        overrides["playersFile"] = players_file.as_posix()

    written_lines = []
    seen = set()
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            written_lines.append(line)
            continue
        key = stripped.split("=", 1)[0].strip()
        if key in overrides:
            written_lines.append(f"{key}={overrides[key]}")
            seen.add(key)
        else:
            written_lines.append(line)
    for key, value in overrides.items():
        if key not in seen:
            written_lines.append(f"{key}={value}")

    tmp_dir = Path(tempfile.mkdtemp(prefix="casino-rl-scenario-"))
    scenario_copy = tmp_dir / base_scenario_path.name
    scenario_copy.write_text("\n".join(written_lines) + "\n", encoding="utf-8")
    return scenario_copy, effective_rounds


def launch_episode(java_executable: str, classpath: str, scenario_path: Path,
                    state_dir: Path, expected_rounds: int) -> EpisodeHandle:
    if state_dir.exists():
        shutil.rmtree(state_dir)
    state_dir.mkdir(parents=True)

    # classpath must be absolute: the subprocess's cwd is the scenario copy's
    # temp folder (below), so a relative classpath like "out" would resolve
    # against the wrong directory and Java would fail with
    # ClassNotFoundException before ever writing anything.
    absolute_classpath = str(Path(classpath).resolve())
    process = subprocess.Popen(
        [java_executable, "-cp", absolute_classpath, "CasinoBatchRunner", str(scenario_path)],
        cwd=str(scenario_path.parent),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return EpisodeHandle(process, state_dir, scenario_path, expected_rounds)
