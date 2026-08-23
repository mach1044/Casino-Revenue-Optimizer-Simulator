# Casino Floor Allocation

A hand-built casino simulation (Java) paired with a Monte Carlo + gradient-boosted-tree
policy (Python/scikit-learn) that decides where to seat players to maximize house profit
— evaluated with real statistical rigor, not eyeballed single runs.

**Full write-up with results, methodology, and findings:**
[Case study report](https://claude.ai/code/artifact/a71b6894-acb9-4be8-8c22-a7d3be025374)

## Origin

This started in high school as a CS project with a friend — the idea was just to
simulate a casino: its players, its games, the environment as a whole. While building
it out, the simulated players started showing genuinely interesting emergent behavior,
which raised a better question: could a machine actually learn to allocate players in
a way that increases the house's profit, and what would it learn if it could?

The first attempt used reinforcement learning, on the reasoning that RL is suited to
exactly this kind of problem — where it's hard to tell, in hindsight, how much a single
decision actually moved the outcome. That turned out to be the problem itself: per-round
profit is dominated by unseeded simulation randomness, so the RL signal was too noisy
to train on reliably. Switching to Monte Carlo–averaged data feeding gradient-boosted
trees fixed that, and led to the results below.

## What this is

500 simulated players — whales, grinders, low-stakes regulars, obnoxious disruptors,
each with their own mood, bankroll, and patience — work a floor of blackjack, roulette,
craps, poker, and slots tables. Every round, each player decides for themselves whether
to play, switch tables, or leave, driven by a mood/tilt/momentum/social-affinity model.

The goal throughout was realism, not abstraction — the conclusions only mean anything
if the underlying economics are real. Blackjack runs a real 6-deck shoe with 75%
penetration, dealer-stands-on-soft-17, 3:2 blackjack payouts, full basic strategy
(splitting, doubling, soft/hard hands) and Hi-Lo count tracking. Roulette caps
high-payout bet categories separately from the table max, the way real tables do.
Poker charges a rake rate with a cap; slots pay out against a configured RTP with a
real jackpot mechanic and jackpot odds. The player archetypes — Whale, Grinder,
Low-Stakes, Obnoxious — are modeled on recognizable real casino player types, each
with different bankrolls, bet-sizing behavior, and tolerance for losing before they
walk away.

The population itself is shaped like a real casino floor's, not a uniform sample:
starting bankrolls are left-skewed across three tiers (54% Low Roller at $150-600,
38% Even Steven at $450-1,800, 8% High Roller at $3,000-25,000), with balances skewed
toward the low end within each tier too — most players are modest, a few are large,
matching the shape a real floor's clientele would take. How long someone stays is
similarly not fixed: it emerges from mood, momentum, tilt, and loss pressure rather
than a round counter. In a real 500-round batch run, players stayed for an average of
127 ticks (median 120) and actually played an average of 115 rounds, with 11.6%
eventually removed by the casino's own profit-limit rule.

On top of that, a second layer asks: **could the casino itself do better by reseating
players between tables?** "Allocating" a player means one of three concrete decisions,
made fresh every 10 rounds for every seated player:

- **Stay** — leave them at their current table.
- **Move** to a specific other table of the *same game type* (a Blackjack player only
  ever gets compared against other Blackjack tables, never Roulette or Poker) — chosen
  from every open table of that type, including at most one representative empty table.
- **Kick** — remove them from the casino floor entirely, permanently.

The choice isn't hand-coded. A gradient-boosted regression model, trained on Monte
Carlo data, predicts the profit impact of removing this player from their current
table's composition and the profit impact of adding them to each candidate table.
The policy compares "stay" (0), "move" (predicted loss from leaving, plus predicted
gain from joining the best alternative), and "kick" (predicted loss from leaving,
nothing else) and greedily takes whichever scores highest.

The headline finding, after 300 replicate simulations: a policy that can permanently
remove ("kick") players shows no measurable profit benefit. A policy restricted to
reseating only (no removal) trends positive but falls just short of conventional
statistical significance (p = 0.070). The full report covers why, including a live
audit of what the policy actually does and a false pattern the model found that didn't
survive scrutiny.

## Design notes

The simulation itself (`Casino/`, `Players/`) has **zero external dependencies** — no
build tool, no JSON library, no third-party jars. Every save/load and every CSV
export/import is hand-rolled with `BufferedReader`/`BufferedWriter`. The only external
dependency in the whole project is `scikit-learn` (plus `numpy`), and only in the
Python training pipeline.

## Repository layout

| Path | Contents |
|---|---|
| `Casino/` | Simulation engine — floor, games (Blackjack, Roulette, Craps, Poker, Slots), batch runner |
| `Players/` | Player behavior model — bankroll/behavior styles, mood & exit-pressure logic |
| `ml/` | Active ML pipeline — Monte Carlo data generation, GBM training, decision policy, evaluation |
| `rl/` | Earlier reinforcement-learning approach (superseded — see below); its file-exchange plumbing is reused by `ml/` |
| `BatchInput/` | Scenario configs and the fixed 500-player population + generator script |
| `BatchResults/` | Output of interactive/manual batch runs |
| `out/` | Compiled `.class` files (build output, not source) |

### Why there's an `rl/` and an `ml/`

**The RL approach (`rl/`).** A small pairwise-scorer neural network (`concat(player
features, table features) -> scalar value`) was trained with TD-learning: after each
round, the reward was the change in total casino profit round-over-round, and the TD
target for every currently-seated player was `reward + gamma * (next round's average
scored value)` — an afterstate-value setup in the same family as TD-Gammon, since
there's no fixed action space to index a per-action Q-head over. The policy side
scored every (player, candidate table) pair with the same network and took the
argmax. Java and Python stayed as separate processes, exchanging per-round state and
move decisions over a polled file protocol (`static_preferences.csv`,
`tables_state.csv`/`players_state.csv` each round, `moves_<N>.csv` back).

It didn't train reliably. The reward signal — one round's profit change — is
dominated by unseeded simulation randomness (card draws, dice, spins), not by
whichever move the policy just made, so the same state/action pair could look good
in one round and bad in the next for reasons that had nothing to do with the
decision. TD-learning has no way to separate that out from real signal one round at
a time.

**The Monte Carlo + GBM approach (`ml/`), what's actually deployed.** Instead of
learning online from single noisy rounds, `MonteCarloDataGenerator.java` samples a
random table composition, simulates it hundreds of times, and averages — turning
"what does adding this player do to this table's profit, on average" into a clean,
low-noise number before any learning happens at all. Two independent
`GradientBoostingRegressor`s (scikit-learn, 100 trees, depth 3) are trained on those
averaged examples: one predicts the resulting change in the other players' mood
(`deltaMood`, trained but not used for decisions — see below), the other predicts the
change in table profit (`deltaProfit`, what the policy actually acts on). Each
training row's features are a one-hot game type, the existing table's composition
(counts of each behavior style present, average bankroll), and the candidate player's
own behavior style, bankroll tier, and balance.

`rl/`'s file-protocol code (`io_protocol.py`, `episode_driver.py`) is still used by
`ml/` as shared plumbing for the live evaluation runs; its model/training code
(`model.py`, `train.py`, `policy.py`, `candidates.py`, `eval.py`) is not.

## Running the simulation directly

```bash
# Compile
javac -d out $(find Casino Players -name "*.java")

# Run a batch scenario (see BatchInput/batch-scenario.properties for all options)
java -cp out CasinoBatchRunner BatchInput/batch-scenario.properties
```

Results (`summary.txt`, `players.csv`, `tables.csv`, `sessions.csv`) are written to the
scenario's `outputDirectory`.

## Tests

Java tests are plain classes with a `main()` and hand-rolled `check()`/`AssertionError`
assertions — no JUnit, run the same way as any other class:

```bash
java -cp out BlackjackTestRunner
java -cp out CrapsTestRunner
java -cp out PokerTestRunner
java -cp out CasinoSaveTestRunner
java -cp out CasinoRLBridgeTestRunner   # exercises the full Java<->Python file protocol
java -cp out BehaviorStyleTestRunner
java -cp out PlayerDecisionTestRunner
```

`CasinoRLBridgeTestRunner` is the most load-bearing one — it validates `movePlayer`/
`kickPlayer` (success, capacity rejection, affordability rejection, no-op cases) and
round-trips the actual CSV/marker-file protocol the Python side depends on, entirely
in Java, with no Python process required.

## Running the ML pipeline

```bash
cd ml
pip install -r requirements.txt

# 1. Generate Monte Carlo training data (500 sampled compositions, averaged over
#    300 replicate simulations each, 100 rounds per replicate)
java -cp ../out MonteCarloDataGenerator training_data.csv 500 300 100

# 2. Average the raw replicate rows into one clean row per sampled composition
python aggregate.py training_data.csv 300 training_data_avg.csv

# 3. Train the deltaMood / deltaProfit gradient-boosted regressors
python train.py training_data_avg.csv

# 4. Evaluate the trained policy against doing nothing, over many replicates
python evaluate.py 150 200   # 150 replicates, 200 rounds each
```

`evaluate.py` runs three conditions per replicate on matched seeds — baseline (no
intervention), policy (moves and kicks allowed), and policy-no-kick (moves only) —
and writes incremental results to `evaluation_results.csv` so a run can be resumed
or analyzed mid-flight.

## Requirements

- JDK 17+ (developed against OpenJDK 26)
- Python 3.10+, `scikit-learn`, `numpy` (see `ml/requirements.txt`)
- `scipy` if reproducing the statistical significance tests from the report
- `java` on your `PATH`, or set `JAVA_EXECUTABLE` to a full path if not

## A note on reproducibility

Population generation is driven by a fixed CSV (`BatchInput/players-input.csv`), not
randomly regenerated per run. In-round game outcomes (cards, dice, spins) run on an
unseeded random source by design, so no two runs — even with an identical scenario —
produce identical results. This is why every result in the report is a distribution
over many replicate runs, compared with Welch's t-test and confidence intervals,
rather than a single run's number.

## Known limitations & next steps

- **The no-kick policy's edge isn't confirmed yet.** It's trending positive and
  consistent across two independent 150-replicate batches (p = 0.070 at n = 283), but
  a proper power calculation says reaching conventional significance needs roughly
  1,468 replicates at the observed effect size — a bounded, known compute cost, not
  an open question.
- **The policy currently acts on predictions smaller than its own error margin.**
  A live decision audit found 98% of moves are triggered by a predicted gain under
  $500, against a model MAE of ~$950. Gating moves on a minimum predicted-gain
  threshold (e.g. only act above the model's own MAE) would cut the noise-driven
  churn and is the most promising next change to try.
- **The regression model is a bankroll detector more than a social-dynamics
  optimizer.** `candidateBalance` alone accounts for over half its feature importance;
  the composition features (counts of each behavior style at a table) matter far less
  than originally hoped. A model architecture that more directly targets composition
  effects (e.g. explicit interaction features, or a model trained on paired
  before/after states) might recover more of that signal.
- **Slots are excluded from the reallocation policy** — single-seat machines have no
  "composition" to optimize around, so they're out of scope by design, not an
  oversight.
