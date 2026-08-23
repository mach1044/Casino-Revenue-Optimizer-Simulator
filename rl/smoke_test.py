"""One-off smoke test: 2 tiny training episodes to prove train.py's
torch-dependent code path actually runs, before committing to a real
500-episode training run. Run as a module from the repo root (train.py
uses package-relative imports, so it must load as part of the `rl`
package): `python -m rl.smoke_test`.
"""
import os
from pathlib import Path

from rl import train

JAVA = os.environ.get("JAVA_EXECUTABLE", "java")

model = train.train(
    scenario_path=Path("BatchInput/scenario-small.properties"),
    episodes_dir=Path("BatchResults/rl-smoke-episodes"),
    java_executable=JAVA,
    classpath="out",
    num_episodes=2,
    round_timeout_s=15,
)
print("Smoke test finished without error.")
