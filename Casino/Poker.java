/*
 * Poker.java
 * -------------------------------------------------------------------------
 * A deliberately heuristic poker economy rather than a card-by-card rules
 * engine. Participation chooses who enters, average aggression controls the
 * shared contribution target, and relative skill weights one showdown order.
 * All-ins create side pots, and the casino earns only the configured rake.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

public class Poker extends Game {

    private static final double DEFAULT_RAKE_RATE = 0.05;
    private static final double CONTRIBUTION_VOLATILITY = 0.18;

    private final Random random;
    private final double rakeRate;
    private final int rakeCap;
    private long attemptedHands;
    private long playedHands;
    private long totalPotVolume;
    private long totalRake;
    private final Map<Player, Long> handsEntered = new IdentityHashMap<Player, Long>();
    private final Map<Player, Long> totalContributed = new IdentityHashMap<Player, Long>();

    public Poker(int id, int tablePop, int minBet, int maxBet){
        this(id, tablePop, minBet, maxBet, DEFAULT_RAKE_RATE, maxBet, new Random());
    }

    public Poker(int id, int tablePop, int minBet, int maxBet, long seed){
        this(id, tablePop, minBet, maxBet, DEFAULT_RAKE_RATE, maxBet, new Random(seed));
    }

    public Poker(int id, int tablePop, int minBet, int maxBet,
                 double rakeRate, int rakeCap, long seed){
        this(id, tablePop, minBet, maxBet, rakeRate, rakeCap, new Random(seed));
    }

    private Poker(int id, int tablePop, int minBet, int maxBet,
                  double rakeRate, int rakeCap, Random random){
        super(id, tablePop, minBet, maxBet);
        validateRake(rakeRate, rakeCap);
        this.rakeRate = rakeRate;
        this.rakeCap = rakeCap;
        this.random = random;
        initializeGame();
    }

    // Constructor used when loading a saved casino. Poker uses the standard
    // five-percent rake and a cap equal to the table maximum contribution.
    public Poker(int id, int profit, int tablePop, int minBet, int maxBet){
        super(id, profit, tablePop, minBet, maxBet);
        rakeRate = DEFAULT_RAKE_RATE;
        rakeCap = maxBet;
        random = new Random();
        initializeGame();
    }

    private void initializeGame(){
        // Poker has no house-banked wager choices, but Game utilities expect
        // an action collection for descriptive/statistical operations.
        numActions = 1;
        actions = new Action[]{new Action(0, "Enter Hand", 1.0, 0.0)};
    }

    private void validateRake(double rate, int cap){
        if(Double.isNaN(rate) || rate < 0 || rate > 1){
            throw new IllegalArgumentException("Poker rake rate must be between 0 and 1");
        }
        if(cap < 0){
            throw new IllegalArgumentException("Poker rake cap cannot be negative");
        }
    }

    public double getRakeRate(){
        return rakeRate;
    }

    public int getRakeCap(){
        return rakeCap;
    }

    public long getAttemptedHands(){
        return attemptedHands;
    }

    public long getPlayedHands(){
        return playedHands;
    }

    public long getTotalPotVolume(){
        return totalPotVolume;
    }

    public long getTotalRake(){
        return totalRake;
    }

    public long getHandsEntered(Player player){
        return handsEntered.getOrDefault(player, 0L);
    }

    public long getTotalContributed(Player player){
        return totalContributed.getOrDefault(player, 0L);
    }

    @Override
    public void simulateRound(){
        attemptedHands++;

        ArrayList<Player> participants = new ArrayList<Player>();
        for(Player player : currentTable){
            if(player.getBalance() >= getMinBet()
                    && random.nextDouble() < player.getPokerParticipation()){
                participants.add(player);
            }
        }

        // No money moves unless at least two players independently elect to
        // enter. This avoids charging a lone player for a nonexistent hand.
        if(participants.size() < 2){
            return;
        }

        int sharedContribution = calculateSharedContribution(participants);
        int matchedContribution = capAtLargestMatchableAmount(
                participants, sharedContribution);
        ArrayList<PokerEntry> entries = new ArrayList<PokerEntry>();
        int pot = 0;
        for(Player player : participants){
            // A short-stacked player goes all-in. Unequal all-in amounts are
            // separated into side pots below; everyone else matches exactly.
            int contribution = Math.min(matchedContribution, player.getBalance());
            if(collectBet(player, contribution)){
                entries.add(new PokerEntry(player, contribution));
                pot += contribution;
                handsEntered.put(player, getHandsEntered(player) + 1);
                totalContributed.put(player, getTotalContributed(player) + contribution);
            }
        }

        if(entries.size() < 2){
            // This is unreachable with valid table limits because every
            // selected participant can afford at least the minimum wager.
            refundEntries(entries);
            return;
        }

        playedHands++;
        totalPotVolume += pot;
        int rake = Math.min(rakeCap, (int)Math.round(pot * rakeRate));
        totalRake += rake;

        ArrayList<SidePot> sidePots = buildSidePots(entries);
        ArrayList<Player> ranking = createSkillWeightedRanking(entries);
        distributePots(sidePots, ranking, rake);
    }

    /**
     * Calculates one matched target for the hand. Wagers are spread over the
     * table range logarithmically so that small pots remain common while high
     * aggression can still produce occasional pots near the table maximum.
     */
    private int calculateSharedContribution(ArrayList<Player> participants){
        if(getMinBet() == getMaxBet()){
            return getMinBet();
        }

        double totalAggression = 0;
        for(Player player : participants){
            totalAggression += player.getPokerAggression();
        }
        double averageAggression = totalAggression / participants.size();
        double position = averageAggression
                + random.nextGaussian() * CONTRIBUTION_VOLATILITY;
        position = Math.max(0, Math.min(1, position));

        double tableRatio = getMaxBet() / (double)getMinBet();
        int desired = (int)Math.round(getMinBet() * Math.pow(tableRatio, position));
        return Math.max(getMinBet(), Math.min(getMaxBet(), desired));
    }

    /**
     * No player can wager more than at least one opponent can match. This is
     * the real-poker rule that returns an unmatched all-in excess, expressed
     * here before money moves by using the table's second-largest stack.
     */
    private int capAtLargestMatchableAmount(ArrayList<Player> participants,
                                            int sharedContribution){
        int largest = 0;
        int secondLargest = 0;
        for(Player player : participants){
            int stack = player.getBalance();
            if(stack >= largest){
                secondLargest = largest;
                largest = stack;
            }
            else if(stack > secondLargest){
                secondLargest = stack;
            }
        }
        return Math.min(sharedContribution, secondLargest);
    }

    private ArrayList<SidePot> buildSidePots(ArrayList<PokerEntry> entries){
        ArrayList<Integer> contributionLevels = new ArrayList<Integer>();
        for(PokerEntry entry : entries){
            if(!contributionLevels.contains(entry.contribution)){
                contributionLevels.add(entry.contribution);
            }
        }
        Collections.sort(contributionLevels);

        ArrayList<SidePot> pots = new ArrayList<SidePot>();
        int previousLevel = 0;
        for(int level : contributionLevels){
            ArrayList<Player> eligible = new ArrayList<Player>();
            for(PokerEntry entry : entries){
                if(entry.contribution >= level){
                    eligible.add(entry.player);
                }
            }
            int amount = (level - previousLevel) * eligible.size();
            pots.add(new SidePot(amount, eligible));
            previousLevel = level;
        }
        return pots;
    }

    /**
     * Produces one consistent showdown order. The first selection preserves
     * the old winner odds; later selections determine side-pot fallbacks.
     */
    private ArrayList<Player> createSkillWeightedRanking(ArrayList<PokerEntry> entries){
        ArrayList<Player> remaining = new ArrayList<Player>();
        for(PokerEntry entry : entries){
            remaining.add(entry.player);
        }

        ArrayList<Player> ranking = new ArrayList<Player>();
        while(!remaining.isEmpty()){
            double totalWeight = 0;
            for(Player player : remaining){
                totalWeight += winnerWeight(player);
            }

            double selection = random.nextDouble() * totalWeight;
            int selectedIndex = remaining.size() - 1;
            for(int i = 0; i < remaining.size(); i++){
                selection -= winnerWeight(remaining.get(i));
                if(selection < 0){
                    selectedIndex = i;
                    break;
                }
            }
            ranking.add(remaining.remove(selectedIndex));
        }
        return ranking;
    }

    private void distributePots(ArrayList<SidePot> pots,
                                ArrayList<Player> ranking, int rake){
        Map<Player, Integer> payouts = new IdentityHashMap<Player, Integer>();
        int remainingRake = rake;

        // Treat the rake as one charge for the hand, removing it from the
        // main pot first and then from later side pots only if necessary.
        for(SidePot pot : pots){
            int potRake = Math.min(remainingRake, pot.amount);
            int payout = pot.amount - potRake;
            remainingRake -= potRake;
            if(payout <= 0){
                continue;
            }

            Player winner = highestRankedEligible(ranking, pot.eligiblePlayers);
            payouts.put(winner, payouts.getOrDefault(winner, 0) + payout);
        }

        // Aggregate all pots won by the same player so one hand records at
        // most one win and reverses exactly one provisional table loss.
        for(Player player : ranking){
            int payout = payouts.getOrDefault(player, 0);
            if(payout > 0){
                distributeWinnings(player, payout);
            }
        }
    }

    private Player highestRankedEligible(ArrayList<Player> ranking,
                                         ArrayList<Player> eligiblePlayers){
        for(Player player : ranking){
            if(eligiblePlayers.contains(player)){
                return player;
            }
        }
        throw new IllegalStateException("Poker side pot has no eligible winner");
    }

    private double winnerWeight(Player player){
        return 0.5 + player.getEffectivePokerSkill();
    }

    private void refundEntries(ArrayList<PokerEntry> entries){
        for(PokerEntry entry : entries){
            returnBet(entry.player, entry.contribution);
        }
    }

    @Override
    public String toString(){
        return "Heuristic Poker - " + super.toString() + "\nRake: "
                + (rakeRate * 100) + "% (cap $" + rakeCap + ")";
    }

    private static final class PokerEntry {
        private final Player player;
        private final int contribution;

        private PokerEntry(Player player, int contribution){
            this.player = player;
            this.contribution = contribution;
        }
    }

    private static final class SidePot {
        private final int amount;
        private final ArrayList<Player> eligiblePlayers;

        private SidePot(int amount, ArrayList<Player> eligiblePlayers){
            this.amount = amount;
            this.eligiblePlayers = eligiblePlayers;
        }
    }
}
