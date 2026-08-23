import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates training data for the Monte-Carlo + gradient-boosting pipeline
 * (mc_gbm/), a separate approach from the RL bridge (rl/) -- this file does
 * not touch or depend on anything in rl/ or Casino/CasinoRLBridge.java.
 *
 * For many randomly sampled table compositions and one randomly sampled
 * "candidate" player, runs replicated simulations with and without the
 * candidate present, and records the averaged effect on the other players'
 * mood and on table profit. Each row is one (composition, candidate) ->
 * (deltaMood, deltaProfit) training example.
 *
 * Usage: java MonteCarloDataGenerator <output.csv> [samples] [replicates] [rounds]
 */
public class MonteCarloDataGenerator {
    private static final String[] GAME_TYPES = {"Blackjack", "Roulette", "Craps", "Poker"};
    private static final int[] CAPACITY = {5, 6, 8, 8}; // matches BatchInput/batch-scenario.properties
    private static final BehaviorType[] BEHAVIOR_TYPES = BehaviorType.values();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java MonteCarloDataGenerator <output.csv> [samples] [replicates] [rounds]");
            return;
        }
        Path outputPath = Paths.get(args[0]);
        int samples = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        int replicates = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 100;

        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("gameType,countGrinder,countObnoxious,countWhale,countLowStakes,avgBankroll,"
                    + "candidateBehaviorStyle,candidateBankrollStyle,candidateBalance,deltaMood,deltaProfit");
            writer.newLine();

            for (int sample = 0; sample < samples; sample++) {
                int gameIndex = ThreadLocalRandom.current().nextInt(GAME_TYPES.length);
                String gameType = GAME_TYPES[gameIndex];
                int capacity = CAPACITY[gameIndex];

                // Randomize occupancy itself (0 to capacity-1 other players),
                // not just who's sitting there -- training data previously
                // always used exactly capacity-1 others, so the model had
                // never seen a sparse or empty table, which turn out to be
                // common in live runs (e.g. Roulette fully empty by late
                // rounds once players start leaving).
                int otherCount = ThreadLocalRandom.current().nextInt(capacity);
                List<PlayerSpec> others = new ArrayList<PlayerSpec>();
                for (int i = 0; i < otherCount; i++) {
                    others.add(randomPlayerSpec());
                }
                PlayerSpec candidate = randomPlayerSpec();

                RunResult withCandidate = runReplicates(gameType, capacity, others, candidate, replicates, rounds);
                RunResult withoutCandidate = runReplicates(gameType, capacity, others, null, replicates, rounds);

                // Emit one raw row per replicate (pairing "with" replicate i
                // against "without" replicate i) instead of one averaged row
                // per sample -- trades clean-but-few labels for noisy-but-
                // abundant ones, letting the model's own training do the
                // denoising instead of pre-averaging.
                String prefix = gameType + "," + countOf(others, BehaviorType.GRINDER) + ","
                        + countOf(others, BehaviorType.OBNOXIOUS) + ","
                        + countOf(others, BehaviorType.WHALE) + ","
                        + countOf(others, BehaviorType.LOW_STAKES) + ","
                        + decimal(avgBalance(others)) + ","
                        + candidate.behaviorType.name() + "," + candidate.bankrollStyle.name() + ","
                        + candidate.balance + ",";
                for (int i = 0; i < replicates; i++) {
                    double deltaMood = withCandidate.othersMood[i] - withoutCandidate.othersMood[i];
                    double deltaProfit = withCandidate.profit[i] - withoutCandidate.profit[i];
                    writer.write(prefix + decimal(deltaMood) + "," + decimal(deltaProfit));
                    writer.newLine();
                }

                if ((sample + 1) % 25 == 0) {
                    System.out.println("generated " + (sample + 1) + "/" + samples
                            + " (" + ((sample + 1) * replicates) + " rows so far)");
                }
            }
        }

        System.out.println("Wrote " + (samples * replicates) + " rows to " + outputPath);
    }

    private static RunResult runReplicates(String gameType, int capacity, List<PlayerSpec> others,
                                           PlayerSpec candidate, int replicates, int rounds) {
        RunResult result = new RunResult(replicates);
        for (int r = 0; r < replicates; r++) {
            Game table = buildTable(gameType, capacity);
            List<Player> seated = new ArrayList<Player>();
            for (PlayerSpec spec : others) {
                Player player = spec.build(seated.size());
                player.joinTable(table);
                seated.add(player);
            }
            Player candidatePlayer = null;
            if (candidate != null) {
                candidatePlayer = candidate.build(1000 + seated.size());
                candidatePlayer.joinTable(table);
            }

            for (int round = 0; round < rounds; round++) {
                table.simulateRound();
                for (Player player : seated) {
                    if (player.getCurrentSession() != null) player.getCurrentSession().completeRound();
                }
                if (candidatePlayer != null && candidatePlayer.getCurrentSession() != null) {
                    candidatePlayer.getCurrentSession().completeRound();
                }
                for (Player player : seated) {
                    if (player.getCurrentSession() == null) continue;
                    player.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(player, table));
                }
                if (candidatePlayer != null && candidatePlayer.getCurrentSession() != null) {
                    candidatePlayer.updateEmotionalStatusAfterRound(
                            TableEnvironment.evaluate(candidatePlayer, table));
                }
            }

            double moodTotal = 0;
            for (Player player : seated) {
                moodTotal += player.getMood();
            }
            // "Effect on other players' mood" is undefined with zero others
            // (nobody there to be affected) -- default to 0 rather than
            // dividing by zero, matching composition_of()'s convention on
            // the Python side for an empty player list.
            result.othersMood[r] = seated.isEmpty() ? 0.0 : moodTotal / seated.size();
            result.profit[r] = table.getProfit();
        }
        return result;
    }

    private static Game buildTable(String gameType, int capacity) {
        int minBet = 5;
        int maxBet = 2000;
        if ("Blackjack".equals(gameType)) return new Blackjack(0, capacity, minBet, maxBet);
        if ("Roulette".equals(gameType)) return new Roulette(0, 0, capacity, minBet, maxBet, 500);
        if ("Craps".equals(gameType)) return new Craps(0, capacity, minBet, maxBet);
        if ("Poker".equals(gameType)) {
            // Matches BatchInput/batch-scenario.properties: no real maxbet, just a 5% rake.
            return new Poker(0, capacity, minBet, 1_000_000_000, 0.05, 1_000_000_000,
                    ThreadLocalRandom.current().nextLong());
        }
        throw new IllegalArgumentException("Unsupported game type: " + gameType);
    }

    private static PlayerSpec randomPlayerSpec() {
        BehaviorType behaviorType = BEHAVIOR_TYPES[ThreadLocalRandom.current().nextInt(BEHAVIOR_TYPES.length)];
        BankrollStyle bankrollStyle = compatibleBankroll(behaviorType);
        int balance = randomBalanceFor(bankrollStyle);
        return new PlayerSpec(behaviorType, bankrollStyle, balance);
    }

    private static BankrollStyle compatibleBankroll(BehaviorType behaviorType) {
        switch (behaviorType) {
            case WHALE:
                return BankrollStyle.HIGH_ROLLER;
            case GRINDER:
                return ThreadLocalRandom.current().nextBoolean()
                        ? BankrollStyle.HIGH_ROLLER : BankrollStyle.EVEN_STEVEN;
            case LOW_STAKES:
                return ThreadLocalRandom.current().nextBoolean()
                        ? BankrollStyle.EVEN_STEVEN : BankrollStyle.LOW_ROLLER;
            case OBNOXIOUS:
                BankrollStyle[] all = BankrollStyle.values();
                return all[ThreadLocalRandom.current().nextInt(all.length)];
            default:
                throw new IllegalArgumentException("Unknown behavior type: " + behaviorType);
        }
    }

    // Matches the ranges in BatchInput/generate_players.py.
    private static int randomBalanceFor(BankrollStyle bankrollStyle) {
        switch (bankrollStyle) {
            case LOW_ROLLER:
                return randomInt(150, 600);
            case EVEN_STEVEN:
                return randomInt(450, 1800);
            case HIGH_ROLLER:
                return randomInt(3000, 25000);
            default:
                throw new IllegalArgumentException("Unknown bankroll style: " + bankrollStyle);
        }
    }

    private static int randomInt(int low, int high) {
        return ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private static int countOf(List<PlayerSpec> specs, BehaviorType type) {
        int count = 0;
        for (PlayerSpec spec : specs) {
            if (spec.behaviorType == type) count++;
        }
        return count;
    }

    private static double avgBalance(List<PlayerSpec> specs) {
        if (specs.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (PlayerSpec spec : specs) sum += spec.balance;
        return sum / specs.size();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static BehaviorStyle newBehaviorStyle(BehaviorType type) {
        return BehaviorStyleFactory.create(type);
    }

    private static final class PlayerSpec {
        final BehaviorType behaviorType;
        final BankrollStyle bankrollStyle;
        final int balance;

        PlayerSpec(BehaviorType behaviorType, BankrollStyle bankrollStyle, int balance) {
            this.behaviorType = behaviorType;
            this.bankrollStyle = bankrollStyle;
            this.balance = balance;
        }

        Player build(int id) {
            return new Player(id, "P" + id, balance,
                    PlayerProfile.forStyles(bankrollStyle, newBehaviorStyle(behaviorType)));
        }
    }

    private static final class RunResult {
        final double[] othersMood;
        final double[] profit;

        RunResult(int replicates) {
            othersMood = new double[replicates];
            profit = new double[replicates];
        }
    }
}
