/*                                                                           *
 * Blackjack.java                                                            *
 * ------------------------------------------------------------------------- *
 * Simulates a casino blackjack table using a shared six-deck shoe.          *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Blackjack extends Game {

    private static final int DECKS_IN_SHOE = 6;
    private static final int CARDS_PER_DECK = 52;
    private static final double SHUFFLE_PENETRATION = 0.75;
    private static final double BLACKJACK_PAYOUT = 1.5;
    private static final boolean DEALER_HITS_SOFT_17 = false;

    private int[] shoe;
    private int nextCard;
    private int runningCount;
    private long totalWagered;
    private long totalHandsSettled;
    private long totalPushes;
    private final Random random;

    public Blackjack(int id, int tablePop, int minBet, int maxBet){
        this(id, tablePop, minBet, maxBet, new Random());
    }

    public Blackjack(int id, int tablePop, int minBet, int maxBet, long seed){
        this(id, tablePop, minBet, maxBet, new Random(seed));
    }

    private Blackjack(int id, int tablePop, int minBet, int maxBet, Random random){
        super(id, tablePop, minBet, maxBet);
        this.random = random;
        initializeGame();
    }

    // Constructor used when loading a previously saved simulation.
    public Blackjack(int id, int profit, int tablePop, int minBet, int maxBet){
        super(id, profit, tablePop, minBet, maxBet);
        random = new Random();
        initializeGame();
    }

    private void initializeGame(){
        actions = new Action[1];
        // Approximate probability of a resolved hand being a player win.
        actions[0] = new Action(0, "Win", 0.42, 1.0);
        buildShoe();
    }

    private void buildShoe(){
        shoe = new int[DECKS_IN_SHOE * CARDS_PER_DECK];
        int index = 0;
        for(int deck = 0; deck < DECKS_IN_SHOE; deck++){
            for(int rank = 1; rank <= 13; rank++){
                int value = rank == 1 ? 11 : Math.min(rank, 10);
                for(int suit = 0; suit < 4; suit++){
                    shoe[index++] = value;
                }
            }
        }
        shuffleShoe();
    }

    private void shuffleShoe(){
        for(int i = shoe.length - 1; i > 0; i--){
            int other = random.nextInt(i + 1);
            swap(i, other);
        }
        nextCard = 0;
        runningCount = 0;
    }

    private void prepareShoeForRound(){
        if(nextCard >= (int)(shoe.length * SHUFFLE_PENETRATION)){
            shuffleShoe();
        }
    }

    /**
     * Deals the next card from the shoe. The original method name is kept for
     * compatibility with the rest of the project.
     */
    public int dispellCard(){
        if(nextCard >= shoe.length){
            shuffleShoe();
        }
        int card = shoe[nextCard++];
        recomputeActionOdds(card);
        return card;
    }

    /**
     * Backwards-compatible manual shuffle operation.
     */
    public void bogosort(){
        shuffleShoe();
    }

    void swap(int first, int second){
        int temporary = shoe[first];
        shoe[first] = shoe[second];
        shoe[second] = temporary;
    }

    public int getRunningCount(){
        return runningCount;
    }

    public int getCardsRemaining(){
        return shoe.length - nextCard;
    }

    public double getTrueCount(){
        double decksRemaining = Math.max(0.25, getCardsRemaining()
                / (double)CARDS_PER_DECK);
        return runningCount / decksRemaining;
    }

    public long getTotalWagered(){
        return totalWagered;
    }

    public long getTotalHandsSettled(){
        return totalHandsSettled;
    }

    public long getTotalPushes(){
        return totalPushes;
    }

    @Override
    public void simulateRound(){
        prepareShoeForRound();

        ArrayList<RoundPlayer> roundPlayers = new ArrayList<RoundPlayer>();
        for(Player player : currentTable){
            int betAmount = player.calculateBlackjackBet(this);
            if(collectBlackjackBet(player, betAmount, false)){
                roundPlayers.add(new RoundPlayer(player, betAmount));
            }
        }

        if(roundPlayers.isEmpty()){
            return;
        }

        Hand dealer = new Hand();

        // Standard table deal: each player receives a card, dealer receives
        // the up-card, then everyone receives their second card.
        for(RoundPlayer roundPlayer : roundPlayers){
            roundPlayer.firstHand().hand.add(dispellCard());
        }
        dealer.add(dispellCard());
        int dealerUpCard = dealer.value();
        for(RoundPlayer roundPlayer : roundPlayers){
            roundPlayer.firstHand().hand.add(dispellCard());
        }
        dealer.add(dispellCard());

        boolean dealerBlackjack = dealer.isBlackjack();

        if(!dealerBlackjack){
            for(RoundPlayer roundPlayer : roundPlayers){
                playRoundPlayer(roundPlayer, dealerUpCard);
            }

            if(dealerMustPlay(roundPlayers)){
                while(dealer.value() < 17
                        || (DEALER_HITS_SOFT_17 && dealer.value() == 17 && dealer.isSoft())){
                    dealer.add(dispellCard());
                }
            }
        }

        settleHands(roundPlayers, dealer, dealerBlackjack);
    }

    private void playRoundPlayer(RoundPlayer roundPlayer, int dealerUpCard){
        RoundHand original = roundPlayer.firstHand();
        if(original.isNaturalBlackjack()){
            return;
        }

        // A player may split the original hand once, producing at most two
        // hands. Re-splitting either resulting hand is intentionally disabled.
        if(usesBasicStrategy(roundPlayer.player)
                && original.hand.isPair()
                && shouldSplit(original.hand.cardAt(0), dealerUpCard)
                && collectBlackjackBet(roundPlayer.player, original.bet, false)){
            int firstCard = original.hand.cardAt(0);
            int secondCard = original.hand.cardAt(1);
            boolean splitAces = firstCard == 11;

            roundPlayer.hands.clear();
            roundPlayer.hands.add(new RoundHand(firstCard, original.bet, true, splitAces));
            roundPlayer.hands.add(new RoundHand(secondCard, original.bet, true, splitAces));

            // Play split hands in casino order: finish the first hand before
            // dealing the next card to the second hand.
            for(RoundHand splitHand : roundPlayer.hands){
                splitHand.hand.add(dispellCard());
                if(!splitHand.splitAces){
                    playHand(roundPlayer.player, splitHand, dealerUpCard);
                }
            }
            return;
        }

        playHand(roundPlayer.player, original, dealerUpCard);
    }

    private void playHand(Player player, RoundHand roundHand, int dealerUpCard){
        Hand hand = roundHand.hand;
        while(!hand.isBust() && hand.value() < 21){
            if(usesBasicStrategy(player) && hand.size() == 2
                    && shouldDouble(hand, dealerUpCard)){
                int additionalBet = roundHand.bet;
                if(collectBlackjackBet(player, additionalBet, true)){
                    roundHand.bet += additionalBet;
                    hand.add(dispellCard());
                    return;
                }
            }

            boolean intendedHit = usesBasicStrategy(player)
                    ? shouldHit(hand, dealerUpCard)
                    : hand.value() <= 15;
            if(!intendedHit){
                return;
            }
            hand.add(dispellCard());
        }
    }

    private boolean usesBasicStrategy(Player player){
        return player.getBehaviorStyle().getBlackjackStrategy()
                == BlackjackStrategy.BASIC;
    }

    private boolean shouldSplit(int pairValue, int dealerUpCard){
        switch(pairValue){
            case 11: // Aces
            case 8:
                return true;
            case 9:
                return (dealerUpCard >= 2 && dealerUpCard <= 6)
                        || dealerUpCard == 8 || dealerUpCard == 9;
            case 7:
                return dealerUpCard >= 2 && dealerUpCard <= 7;
            case 6:
                return dealerUpCard >= 2 && dealerUpCard <= 6;
            case 4:
                return dealerUpCard == 5 || dealerUpCard == 6;
            case 3:
            case 2:
                return dealerUpCard >= 2 && dealerUpCard <= 7;
            default:
                // Never split ten-valued cards or fives under basic strategy.
                return false;
        }
    }

    private boolean shouldDouble(Hand hand, int dealerUpCard){
        int value = hand.value();
        if(hand.isSoft()){
            if(value == 13 || value == 14){
                return dealerUpCard == 5 || dealerUpCard == 6;
            }
            if(value == 15 || value == 16){
                return dealerUpCard >= 4 && dealerUpCard <= 6;
            }
            if(value == 17){
                return dealerUpCard >= 3 && dealerUpCard <= 6;
            }
            if(value == 18){
                return dealerUpCard >= 2 && dealerUpCard <= 6;
            }
            return value == 19 && dealerUpCard == 6;
        }

        if(value == 9){
            return dealerUpCard >= 3 && dealerUpCard <= 6;
        }
        if(value == 10){
            return dealerUpCard >= 2 && dealerUpCard <= 9;
        }
        return value == 11 && dealerUpCard != 11;
    }

    private boolean shouldHit(Hand hand, int dealerUpCard){
        int value = hand.value();
        if(value >= 21){
            return false;
        }

        // Hit/stand portion of basic strategy. Split and double decisions are
        // handled before this fallback is used.
        if(hand.isSoft()){
            if(value <= 17){
                return true;
            }
            return value == 18 && (dealerUpCard == 9 || dealerUpCard == 10 || dealerUpCard == 11);
        }

        if(value <= 11){
            return true;
        }
        if(value == 12){
            return dealerUpCard <= 3 || dealerUpCard >= 7;
        }
        if(value <= 16){
            return dealerUpCard >= 7;
        }
        return false;
    }

    private boolean dealerMustPlay(ArrayList<RoundPlayer> roundPlayers){
        for(RoundPlayer roundPlayer : roundPlayers){
            for(RoundHand roundHand : roundPlayer.hands){
                if(!roundHand.isNaturalBlackjack() && !roundHand.hand.isBust()){
                    return true;
                }
            }
        }
        return false;
    }

    private void settleHands(ArrayList<RoundPlayer> roundPlayers, Hand dealer,
                             boolean dealerBlackjack){
        for(RoundPlayer roundPlayer : roundPlayers){
            for(RoundHand roundHand : roundPlayer.hands){
                totalHandsSettled++;
                Hand playerHand = roundHand.hand;
                Player player = roundPlayer.player;
                int bet = roundHand.bet;

                if(roundHand.isNaturalBlackjack()){
                    if(dealerBlackjack){
                        returnBet(player, bet);
                        totalPushes++;
                    }
                    else{
                        int blackjackReturn = (int)Math.round(bet * (BLACKJACK_PAYOUT + 1));
                        distributeWinnings(player, blackjackReturn);
                    }
                    continue;
                }

                if(dealerBlackjack || playerHand.isBust()){
                    continue;
                }

                if(dealer.isBust() || playerHand.value() > dealer.value()){
                    distributeWinnings(player, bet * 2);
                }
                else if(playerHand.value() == dealer.value()){
                    returnBet(player, bet);
                    totalPushes++;
                }
            }
        }
    }

    private boolean collectBlackjackBet(Player player, int amount,
                                        boolean supplementsExistingHand){
        if(!collectBet(player, amount)){
            return false;
        }
        totalWagered += amount;

        // Player.placeBet initially records every collected wager as a loss.
        // A double is a larger wager on the same hand, not a second hand, so
        // keep its money movement but remove the extra outcome count.
        if(supplementsExistingHand && player.getCurrentSession() != null){
            player.getCurrentSession().addTableLosses(-1);
        }
        return true;
    }

    /**
     * Maintains a standard Hi-Lo running count for reporting. It does not
     * alter cards or force game outcomes.
     */
    public void recomputeActionOdds(int card){
        if(card >= 2 && card <= 6){
            runningCount++;
        }
        else if(card == 10 || card == 11){
            runningCount--;
        }
    }

    // Package-private deterministic hook used by the rule test runner.
    void setShoeForTesting(int... cards){
        if(cards == null || cards.length == 0){
            throw new IllegalArgumentException("Test shoe cannot be empty");
        }
        shoe = Arrays.copyOf(cards, cards.length);
        nextCard = 0;
        runningCount = 0;
    }

    static final class Hand {
        private final ArrayList<Integer> cards = new ArrayList<Integer>();

        void add(int card){
            cards.add(card);
        }

        int value(){
            int total = 0;
            int aces = 0;
            for(int card : cards){
                total += card;
                if(card == 11){
                    aces++;
                }
            }
            while(total > 21 && aces > 0){
                total -= 10;
                aces--;
            }
            return total;
        }

        boolean isSoft(){
            int total = 0;
            int aces = 0;
            for(int card : cards){
                total += card;
                if(card == 11){
                    aces++;
                }
            }
            while(total > 21 && aces > 0){
                total -= 10;
                aces--;
            }
            return aces > 0;
        }

        boolean isBlackjack(){
            return cards.size() == 2 && value() == 21;
        }

        boolean isBust(){
            return value() > 21;
        }

        int size(){
            return cards.size();
        }

        int cardAt(int index){
            return cards.get(index);
        }

        boolean isPair(){
            return cards.size() == 2 && cards.get(0).equals(cards.get(1));
        }
    }

    private static final class RoundPlayer {
        private final Player player;
        private final ArrayList<RoundHand> hands = new ArrayList<RoundHand>();

        private RoundPlayer(Player player, int bet){
            this.player = player;
            hands.add(new RoundHand(bet));
        }

        private RoundHand firstHand(){
            return hands.get(0);
        }
    }

    private static final class RoundHand {
        private final Hand hand = new Hand();
        private int bet;
        private final boolean fromSplit;
        private final boolean splitAces;

        private RoundHand(int bet){
            this.bet = bet;
            fromSplit = false;
            splitAces = false;
        }

        private RoundHand(int firstCard, int bet, boolean fromSplit, boolean splitAces){
            this.bet = bet;
            this.fromSplit = fromSplit;
            this.splitAces = splitAces;
            hand.add(firstCard);
        }

        private boolean isNaturalBlackjack(){
            return !fromSplit && hand.isBlackjack();
        }
    }
}
