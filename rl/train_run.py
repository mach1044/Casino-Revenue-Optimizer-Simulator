"""Real training entry point. Run from the repo root as a module, not a
plain script (train.py uses package-relative imports, e.g. `from . import
episode_driver`, which only resolve when it's loaded as part of the `rl`
package):

    python -m rl.train_run

Starts on the small scenario (BatchInput/scenario-small.properties) --
cheap to iterate on. Once loss/profit trends look sane, switch
scenario_path to BatchInput/batch-scenario.properties for a real-size run
(much slower per episode: 500 rounds/500 players instead of 20/20).

Superseded by ../ml/ -- see the root README for why. Kept for reference,
not part of the active pipeline.
"""
import os
from pathlib import Path

from rl import train

JAVA = os.environ.get("JAVA_EXECUTABLE", "java")

model = train.train(
    scenario_path=Path("BatchInput/scenario-small.properties"),
    episodes_dir=Path("BatchResults/rl-training"),
    java_executable=JAVA,
    classpath="out",
    num_episodes=100,
    checkpoint_every=10,
    checkpoint_path=Path("BatchResults/model.pt"),
)
print("Training run complete. Final checkpoint: BatchResults/model.pt")
