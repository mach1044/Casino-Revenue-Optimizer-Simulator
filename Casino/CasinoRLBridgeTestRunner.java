import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free checks for Casino.movePlayer and the RL file protocol. */
public class CasinoRLBridgeTestRunner {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testMoveSuccess();
        testMoveCapacityRejection();
        testMoveAffordabilityRejection();
        testMoveUnknownIdsAndSameTable();
        testMoveNotSeated();
        testKickSuccess();
        testKickUnknownAndAlreadyGone();
        testFileProtocolRoundTrip();
        testTimeout();

        System.out.println("Casino RL bridge assertions passed: " + assertions);
    }

    private static void testMoveSuccess() {
        Casino casino = new Casino();
        casino.addPlayer("Player A", 500, 2, new LowStakesStyle());
        casino.addGame(2, 5, 100, 0); // origin: Blackjack, capacity 2
        casino.addGame(1, 5, 100, 0); // target: Blackjack, capacity 1

        Player playerA = casino.getPlayers().get(0);
        Game origin = casino.getCasinoFloor().get(0);
        Game target = casino.getCasinoFloor().get(1);
        playerA.joinTable(origin);

        MoveResult result = casino.movePlayer(playerA.getId(), target.getId());
        check(result == MoveResult.SUCCESS, "A move to an open, affordable table must succeed");
        check(playerA.getCurrentSession().getGame() == target,
                "A successful move must seat the player at the target table");
        check(origin.getNumPlayers() == 0, "The origin table must lose the moved player");
        check(target.getNumPlayers() == 1, "The target table must gain the moved player");
    }

    private static void testMoveCapacityRejection() {
        Casino casino = new Casino();
        casino.addPlayer("Player A", 500, 2, new LowStakesStyle());
        casino.addPlayer("Player B", 500, 2, new LowStakesStyle());
        casino.addGame(2, 5, 100, 0); // origin: capacity 2
        casino.addGame(1, 5, 100, 0); // target: capacity 1

        Player playerA = casino.getPlayers().get(0);
        Player playerB = casino.getPlayers().get(1);
        Game origin = casino.getCasinoFloor().get(0);
        Game target = casino.getCasinoFloor().get(1);
        playerA.joinTable(origin);
        playerB.joinTable(target); // fills the target's only seat

        MoveResult result = casino.movePlayer(playerA.getId(), target.getId());
        check(result == MoveResult.TARGET_FULL, "A move onto a full table must be rejected");
        check(playerA.getCurrentSession().getGame() == origin,
                "A rejected move must leave the player at their original table");
        check(origin.getNumPlayers() == 1, "A rejected move must not change the origin table's occupancy");
        check(target.getNumPlayers() == 1, "A rejected move must not change the target table's occupancy");
    }

    private static void testMoveAffordabilityRejection() {
        Casino casino = new Casino();
        casino.addPlayer("Player C", 3, 2, new LowStakesStyle());
        casino.addGame(2, 1, 100, 0); // origin: minBet 1, affordable
        casino.addGame(2, 5, 100, 0); // target: minBet 5, unaffordable at balance 3

        Player playerC = casino.getPlayers().get(0);
        Game origin = casino.getCasinoFloor().get(0);
        Game target = casino.getCasinoFloor().get(1);
        playerC.joinTable(origin);

        MoveResult result = casino.movePlayer(playerC.getId(), target.getId());
        check(result == MoveResult.INSUFFICIENT_BALANCE,
                "A move to a table above the player's balance must be rejected");
        check(playerC.getCurrentSession().getGame() == origin,
                "A rejected move must leave the player at their original table");
    }

    private static void testMoveUnknownIdsAndSameTable() {
        Casino casino = new Casino();
        casino.addPlayer("Player A", 500, 2, new LowStakesStyle());
        casino.addGame(2, 5, 100, 0);

        Player playerA = casino.getPlayers().get(0);
        Game origin = casino.getCasinoFloor().get(0);
        playerA.joinTable(origin);

        check(casino.movePlayer(9999, origin.getId()) == MoveResult.PLAYER_NOT_FOUND,
                "Moving an unknown player id must be rejected");
        check(casino.movePlayer(playerA.getId(), 9999) == MoveResult.TARGET_NOT_FOUND,
                "Moving to an unknown table id must be rejected");
        check(casino.movePlayer(playerA.getId(), origin.getId()) == MoveResult.SAME_TABLE,
                "Moving a player to their current table must be a no-op result");
    }

    private static void testMoveNotSeated() {
        Casino casino = new Casino();
        casino.addPlayer("Player D", 500, 2, new LowStakesStyle());
        casino.addGame(2, 5, 100, 0);

        Player playerD = casino.getPlayers().get(0);
        Game target = casino.getCasinoFloor().get(0);

        check(casino.movePlayer(playerD.getId(), target.getId()) == MoveResult.PLAYER_NOT_SEATED,
                "Moving an unseated player must be rejected");
    }

    private static void testKickSuccess() {
        Casino casino = new Casino();
        casino.addPlayer("Player A", 500, 2, new LowStakesStyle());
        casino.addGame(2, 5, 100, 0);

        Player playerA = casino.getPlayers().get(0);
        Game origin = casino.getCasinoFloor().get(0);
        playerA.joinTable(origin);

        MoveResult result = casino.kickPlayer(playerA.getId());
        check(result == MoveResult.SUCCESS, "Kicking an in-casino player must succeed");
        check(!playerA.isInCasino(), "A kicked player must no longer be in the casino");
        check(playerA.getVisitState().wasRemovedByCasino(),
                "A kicked player must be marked as removed by the casino, not a voluntary exit");
        check(playerA.getCurrentSession() == null, "A kicked player must be unseated");
        check(origin.getNumPlayers() == 0, "Kicking a player must free their table seat");
    }

    private static void testKickUnknownAndAlreadyGone() {
        Casino casino = new Casino();
        casino.addPlayer("Player B", 500, 2, new LowStakesStyle());
        Player playerB = casino.getPlayers().get(0);

        check(casino.kickPlayer(9999) == MoveResult.PLAYER_NOT_FOUND,
                "Kicking an unknown player id must be rejected");

        check(casino.kickPlayer(playerB.getId()) == MoveResult.SUCCESS,
                "Kicking an in-casino player must succeed");
        check(casino.kickPlayer(playerB.getId()) == MoveResult.PLAYER_NOT_IN_CASINO,
                "Kicking a player who already left must be rejected, not silently repeated");
    }

    /**
     * Drives Casino.exchange's real, blocking file protocol end to end: a
     * background thread stands in for the Python process, waiting for the
     * exported round state and then writing back a move.
     */
    private static void testFileProtocolRoundTrip() throws Exception {
        Path stateDirectory = Files.createTempDirectory("casino-rl-bridge-test-");

        Casino casino = new Casino();
        casino.addPlayer("Player E", 500, 2, new LowStakesStyle());
        casino.addGame(2, 5, 100, 0); // origin
        casino.addGame(2, 5, 100, 0); // target

        Player playerE = casino.getPlayers().get(0);
        Game origin = casino.getCasinoFloor().get(0);
        Game target = casino.getCasinoFloor().get(1);
        playerE.joinTable(origin);

        CasinoRLBridge bridge = new CasinoRLBridge(stateDirectory, 20, 5000);
        bridge.writeStaticPreferences(casino);

        Path preferencesFile = stateDirectory.resolve("static_preferences.csv");
        check(Files.exists(preferencesFile), "writeStaticPreferences must create static_preferences.csv");
        String preferencesContent = Files.readString(preferencesFile);
        check(preferencesContent.startsWith("playerId,gameId,gameType,preferenceWeight"),
                "static_preferences.csv must start with the expected header");
        check(preferencesContent.contains(playerE.getId() + "," + origin.getId() + ",Blackjack"),
                "static_preferences.csv must contain a row for the player and origin table");

        final Exception[] standInFailure = new Exception[1];
        Thread pythonStandIn = new Thread(() -> {
            try {
                Path stateDone = stateDirectory.resolve("round_0.state.done");
                waitForExistence(stateDone, 5000);

                String playersState = Files.readString(stateDirectory.resolve("players_state.csv"));
                if (!playersState.contains(playerE.getId() + ",\"Player E\",LOW_ROLLER,LOW_STAKES,"
                        + origin.getId() + ",Blackjack,")) {
                    throw new AssertionError(
                            "players_state.csv must describe the player seated at the origin table");
                }

                Path movesTmp = stateDirectory.resolve("moves_0.csv.tmp");
                Files.writeString(movesTmp, "playerId,targetGameId\n"
                        + playerE.getId() + "," + target.getId() + "\n");
                Files.move(movesTmp, stateDirectory.resolve("moves_0.csv"));
                Files.writeString(stateDirectory.resolve("round_0.moves.done"), "");
            } catch (Exception exception) {
                standInFailure[0] = exception;
            }
        });
        pythonStandIn.start();

        casino.getControls(); // no-op touch to keep casino "used" before exchange
        bridge.exchange(casino, 0);
        pythonStandIn.join(5000);

        check(standInFailure[0] == null,
                "The Python stand-in thread must not fail: " + standInFailure[0]);
        check(playerE.getCurrentSession().getGame() == target,
                "exchange() must apply the move written by the external process");
        check(origin.getNumPlayers() == 0 && target.getNumPlayers() == 1,
                "exchange() must update table occupancy for the applied move");

        String resultContent = Files.readString(stateDirectory.resolve("round_0.moves.result.csv"));
        check(resultContent.contains(playerE.getId() + ",MOVE," + target.getId() + ",SUCCESS"),
                "round_0.moves.result.csv must echo the successful move");
    }

    private static void testTimeout() throws Exception {
        Path stateDirectory = Files.createTempDirectory("casino-rl-bridge-timeout-test-");
        Casino casino = new Casino();
        CasinoRLBridge bridge = new CasinoRLBridge(stateDirectory, 20, 150);

        boolean timedOut = false;
        try {
            bridge.exchange(casino, 0);
        } catch (IOException expected) {
            timedOut = expected.getMessage() != null
                    && expected.getMessage().toLowerCase().contains("timed out");
        }
        check(timedOut, "exchange() must throw a clear timeout error when no moves file ever arrives");
    }

    private static void waitForExistence(Path path, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!Files.exists(path)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("Timed out waiting for " + path + " to be created");
            }
            Thread.sleep(10);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
