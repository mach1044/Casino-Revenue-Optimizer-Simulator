import java.util.ArrayList;
import java.util.List;

/**
 * Throwaway experiment: does an Obnoxious player's presence measurably move
 * the OTHER players' mood/behavior, even if raw table profit is too noisy to
 * show it? Not part of the test suite.
 *
 * Unlike the profit-only version, this drives the full per-round lifecycle
 * (session checkpoint + emotional-status update) so mood actually updates --
 * the first version only called simulateRound(), which never touches mood.
 */
public class SignalVsNoiseCheck {
    private static final int ROUNDS = 200;
    private static final int REPLICATES = 400;

    public static void main(String[] args) {
        Result withObnoxious = runReplicates(true);
        Result withoutObnoxious = runReplicates(false);

        report("WITH obnoxious player", withObnoxious);
        report("WITHOUT obnoxious player", withoutObnoxious);

        System.out.println();
        compare("table profit", withObnoxious.profit, withoutObnoxious.profit);
        compare("avg final mood (other 4 players)", withObnoxious.mood, withoutObnoxious.mood);
        compare("avg total wagered (other 4 players)", withObnoxious.wagered, withoutObnoxious.wagered);
    }

    private static Result runReplicates(boolean includeObnoxious) {
        Result result = new Result(REPLICATES);
        for (int r = 0; r < REPLICATES; r++) {
            Blackjack table = new Blackjack(0, 5, 5, 2000);
            List<Player> roster = buildRoster(includeObnoxious);
            for (Player player : roster) {
                player.joinTable(table);
            }

            for (int round = 0; round < ROUNDS; round++) {
                table.simulateRound();
                for (Player player : roster) {
                    if (player.getCurrentSession() == null) continue;
                    player.getCurrentSession().completeRound();
                }
                for (Player player : roster) {
                    if (player.getCurrentSession() == null) continue;
                    TableEnvironment env = TableEnvironment.evaluate(player, table);
                    player.updateEmotionalStatusAfterRound(env);
                }
            }

            result.profit[r] = table.getProfit();

            // "Other 4 players" = everyone except the Obnoxious one (id 4),
            // so the comparison is apples-to-apples across both conditions.
            double moodSum = 0;
            double wageredSum = 0;
            int count = 0;
            for (Player player : roster) {
                if (player.getId() == 4) continue; // skip Obnoxious itself
                moodSum += player.getMood();
                wageredSum += player.getCurrentSession().getTotalWagered();
                count++;
            }
            result.mood[r] = moodSum / count;
            result.wagered[r] = wageredSum / count;
        }
        return result;
    }

    private static List<Player> buildRoster(boolean includeObnoxious) {
        List<Player> players = new ArrayList<Player>();
        players.add(new Player(0, "Whale", 25000,
                PlayerProfile.forStyles(BankrollStyle.HIGH_ROLLER, new WhaleStyle())));
        players.add(new Player(1, "Grinder1", 800,
                PlayerProfile.forStyles(BankrollStyle.EVEN_STEVEN, new GrinderStyle())));
        players.add(new Player(2, "Grinder2", 800,
                PlayerProfile.forStyles(BankrollStyle.EVEN_STEVEN, new GrinderStyle())));
        players.add(new Player(3, "LowStakes", 300,
                PlayerProfile.forStyles(BankrollStyle.LOW_ROLLER, new LowStakesStyle())));
        if (includeObnoxious) {
            players.add(new Player(4, "Obnoxious", 500,
                    PlayerProfile.forStyles(BankrollStyle.EVEN_STEVEN, new ObnoxiousStyle())));
        }
        return players;
    }

    private static void report(String label, Result result) {
        System.out.println(label + ":");
        System.out.println("  profit:  mean=" + mean(result.profit) + " stdev=" + stdev(result.profit));
        System.out.println("  mood:    mean=" + mean(result.mood) + " stdev=" + stdev(result.mood));
        System.out.println("  wagered: mean=" + mean(result.wagered) + " stdev=" + stdev(result.wagered));
    }

    private static void compare(String label, double[] with, double[] without) {
        double meanWith = mean(with);
        double meanWithout = mean(without);
        double se = Math.sqrt(stderr(with) * stderr(with) + stderr(without) * stderr(without));
        double diff = meanWithout - meanWith;
        System.out.println(label + ": diff(without-with)=" + diff
                + " combinedSE=" + se + " t=" + (diff / se));
    }

    private static final class Result {
        double[] profit;
        double[] mood;
        double[] wagered;

        Result(int n) {
            profit = new double[n];
            mood = new double[n];
            wagered = new double[n];
        }
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stdev(double[] values) {
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / (values.length - 1));
    }

    private static double stderr(double[] values) {
        return stdev(values) / Math.sqrt(values.length);
    }
}
