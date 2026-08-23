# Casino Controllables

This list records casino-controlled simulation settings and their implementation status.

## Agreed controls

1. **Table betting limits**
   - Minimum bet per game or table
   - Maximum bet per game or table

2. **Poker rake**
   - Rake percentage
   - Maximum rake cap

3. **Removing players from tables**
   - Maximum rounds allowed at one table
   - Whether to remove a player who cannot afford the table minimum

4. **Removing players from the casino**
   - Maximum rounds allowed in the casino
   - Player profit limit: remove a player when `currentBalance - startingBalance` reaches the configured amount
   - Whether blacklisted players are automatically removed

5. **Slot-machine payback**
   - Target payback/RTP percentage

6. **Table assignment policy**
   - `PREFERENCE_WEIGHTED`: use player game preferences
   - `RANDOM`: randomly select an eligible table
   - `LEAST_OCCUPIED`: prefer the table with the most open seats
   - `CASINO_PRIORITY`: use casino-defined priorities for game types or tables

## Important distinction

Casino-controlled removal is separate from a player's voluntary decision to stay, switch tables, or leave the casino.

## Current implementation status

- Minimum and maximum table bets are implemented.
- Poker rake rate and cap are exposed in `BatchInput/batch-scenario.properties`.
- Poker choice uses `1 - (rake / cutoff)^2`. The cutoff is 10% for grinders
  and obnoxious players, 25% for low-stakes players, and 20% for whales.
- Slot RTP does not affect player game choice.
- Target slot RTP is exposed in `BatchInput/batch-scenario.properties`.
- Profit-limit removal is exposed as `playerProfitLimit`; `0` disables it.
- Table-session limits are not implemented; players control their own table changes and exits.
- Other casino removal policies and selectable table-assignment policies still need implementation.
