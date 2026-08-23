# Batch casino simulation

The batch runner reads `BatchInput/batch-scenario.properties`, loads a fixed player list or generates random players, runs the casino, and exports detailed results to `BatchResults`.

## Run it in IntelliJ

1. Open **Run > Edit Configurations** for `CasinoRunner`.
2. Put the absolute path to `batch-scenario.properties` in **Program arguments**.
3. Run `CasinoRunner`.

When `CasinoRunner` receives a file argument, it uses batch mode instead of showing the interactive menu.

## Run it from a terminal

From the `Casino` source directory after compilation:

```powershell
java CasinoRunner "..\batch-scenario.properties"
```

## Input

Edit `batch-scenario.properties` to control:

- A `playersFile` CSV containing a fixed population, or the number of randomly generated players
- Number of simulation rounds
- Random starting-balance, mood, experience, and blacklist ranges
- Relative frequency of high rollers, even players, and low rollers
- Number, capacity, and betting limits of game tables
- Poker rake rate and cap
- Slot target RTP
- Player profit limit for casino-enforced removal
- Staff counts and output location

Using the same `seed` generates the same starting population. The games still use their own random outcomes.

When `playersFile` is set, its path is resolved relative to the scenario file. It must contain this header:

```csv
name,balance,bankrollType,behaviorStyle,mood,blacklisted,pokerParticipation,pokerAggression,pokerSkill
```

Valid bankroll types are `HIGH_ROLLER`, `EVEN_STEVEN`, and `LOW_ROLLER`. Valid behavior styles are `WHALE`, `GRINDER`, `LOW_STAKES`, and `OBNOXIOUS`. The configured `players` count and random player ranges are ignored when a player file is used.

All money is measured in whole dollars. Players wager directly from `balance`; there is no separate chip balance or conversion rate.

## Output

The configured output directory receives:

- `summary.txt` — overall casino results
- `players.csv` — starting traits and final result for every player
- `tables.csv` — profit and usage of each table or slot machine
- `sessions.csv` — every player's results at each table session

CSV files can be opened directly in Excel or imported into another analysis tool.

Players control their own table changes and casino exits. The batch runner does not force periodic table turnover.
Casino-enforced profit removal is separate: `playerProfitLimit=0` disables it; a positive value removes a player once their profit reaches that dollar amount.
