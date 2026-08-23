import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free round-trip checks for simulation save files. */
public class CasinoSaveTestRunner {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path saveDirectory = Files.createTempDirectory("casino-save-test-");
        Path savePath = saveDirectory.resolve("simulation.txt");

        Casino original = new Casino();
        original.addPlayer("Saved Player", 1_000, 2,
                new LowStakesStyle());
        original.addGame(1, 5, 5, 2);

        Player player = original.getPlayers().get(0);
        Game game = original.getCasinoFloor().get(0);
        PlaySessionRecord session = player.joinTable(game);
        original.addRecord(session);
        game.collectBet(player, 5);
        session.completeRound();
        player.leaveTable();

        check(original.saveSimToFile(savePath.toString()),
                "An initial simulation save must be created");

        Casino loaded = new Casino();
        check(loaded.loadSimFromFile(savePath.toString()),
                "The initial simulation save must load");
        check(loaded.getPlayers().size() == 1
                        && loaded.getPlayers().get(0).getBalance() == 995,
                "A loaded simulation must restore the player and chip balance");
        check(loaded.getPlayRecords().size() == 1,
                "A loaded simulation must restore its session history");

        PlaySessionRecord loadedSession = loaded.getPlayRecords().get(0);
        check(loadedSession.getStartingBalance() == 1_000
                        && loadedSession.getEndingBalance() == 995,
                "A loaded session must restore starting and ending balances");
        check(loadedSession.getTotalWagered() == 5
                        && loadedSession.getRoundsPlayed() == 1,
                "A loaded session must restore its simulation result");

        loaded.getPlayers().get(0).addWinnings(10);
        check(loaded.saveSimToFile(savePath.toString()),
                "A loaded simulation must resave to the same file");

        Casino reloaded = new Casino();
        check(reloaded.loadSimFromFile(savePath.toString())
                        && reloaded.getPlayers().get(0).getBalance() == 1_005,
                "Resaving must overwrite the same file with current results");

        System.out.println("Casino save assertions passed: " + assertions);
    }

    private static void check(boolean condition, String message){
        assertions++;
        if(!condition){
            throw new AssertionError(message);
        }
    }
}
