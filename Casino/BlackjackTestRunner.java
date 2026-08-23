import java.util.Arrays;

/**
 * Dependency-free rule and return test runner for Blackjack.
 */
public class BlackjackTestRunner {

    private static int assertions;

    public static void main(String[] args){
        int roiHands = args.length > 0 ? Integer.parseInt(args[0]) : 5_000_000;

        testHandValues();
        testShoeComposition();
        testFirstCardDistribution();
        testNaturalBlackjackPayout();
        testDealerBlackjack();
        testMutualBlackjackPush();
        testNormalWinAndPush();
        testDoubleDown();
        testSingleSplitLimit();
        testSplitAces();
        testDoubleAfterSplit();
        testSimpleHitToFifteenStrategy();
        testGrinderFlatBetsRegardlessOfCount();
        testSharedDealerHand();
        testAccountingConservation();

        System.out.println("Rule assertions passed: " + assertions);
        runReturnSimulation(roiHands);
    }

    private static void testHandValues(){
        Blackjack.Hand hand = hand(11, 6, 10);
        check(hand.value() == 17, "Ace must convert from 11 to 1 when needed");
        check(!hand.isBust(), "Soft-ace conversion must prevent a false bust");

        hand = hand(11, 11, 9);
        check(hand.value() == 21, "Two aces and nine must equal 21");
        check(!hand.isBlackjack(), "A three-card 21 is not a natural blackjack");

        hand = hand(11, 10);
        check(hand.isBlackjack(), "Ace and ten must be a natural blackjack");

        hand = hand(10, 8, 6);
        check(hand.isBust(), "A hard 24 must bust");
    }

    private static void testShoeComposition(){
        Blackjack game = new Blackjack(1, 1, 10, 100, 12345L);
        int[] counts = new int[12];
        for(int i = 0; i < 6 * 52; i++){
            counts[game.dispellCard()]++;
        }

        check(counts[11] == 24, "Six-deck shoe must contain 24 aces");
        for(int value = 2; value <= 9; value++){
            check(counts[value] == 24, "Six-deck shoe must contain 24 cards valued " + value);
        }
        check(counts[10] == 96, "Six-deck shoe must contain 96 ten-valued cards");
        check(game.getRunningCount() == 0, "Complete shoe must finish with Hi-Lo count zero");
    }

    private static void testFirstCardDistribution(){
        int trials = 10_000;
        int aces = 0;
        int tens = 0;
        for(int i = 0; i < trials; i++){
            Blackjack game = new Blackjack(i, 1, 10, 100, 10_000L + i);
            int card = game.dispellCard();
            if(card == 11){
                aces++;
            }
            if(card == 10){
                tens++;
            }
        }

        double aceRate = aces / (double)trials;
        double tenRate = tens / (double)trials;
        check(aceRate > 0.06 && aceRate < 0.095,
                "First-card ace rate must be near 4/52, observed " + aceRate);
        check(tenRate > 0.27 && tenRate < 0.35,
                "First-card ten rate must be near 16/52, observed " + tenRate);
        System.out.printf("First-card distribution: ace=%.3f%%, ten-value=%.3f%%%n",
                aceRate * 100, tenRate * 100);
    }

    private static void testNaturalBlackjackPayout(){
        RoundFixture fixture = fixture(11, 9, 10, 7);
        fixture.game.simulateRound();
        check(fixture.player.getBalance() == 115,
                "Ten-chip natural must produce fifteen-chip profit");
        check(fixture.game.getProfit() == -15,
                "Table must record the natural blackjack payout");
    }

    private static void testDealerBlackjack(){
        RoundFixture fixture = fixture(10, 11, 10, 10);
        fixture.game.simulateRound();
        check(fixture.player.getBalance() == 90,
                "Non-blackjack player must lose to dealer blackjack");
        check(fixture.game.getProfit() == 10,
                "Table must retain the losing wager");
    }

    private static void testMutualBlackjackPush(){
        RoundFixture fixture = fixture(11, 11, 10, 10);
        fixture.game.simulateRound();
        PlaySessionRecord record = fixture.player.getCurrentSession();
        check(fixture.player.getBalance() == 100,
                "Mutual blackjack must push");
        check(fixture.game.getProfit() == 0,
                "Push must leave table profit unchanged");
        check(record.getTableWins() == 0 && record.getTableLosses() == 0,
                "Push must not be recorded as a win or loss");
    }

    private static void testNormalWinAndPush(){
        RoundFixture win = fixture(10, 9, 10, 8);
        win.game.simulateRound();
        check(win.player.getBalance() == 110,
                "Player 20 must beat dealer 17 and profit ten chips");

        RoundFixture push = fixture(10, 9, 7, 8);
        push.game.simulateRound();
        check(push.player.getBalance() == 100,
                "Equal 17 hands must push");
        check(push.player.getCurrentSession().getTableWins() == 0,
                "Normal push must not count as a win");
    }

    private static void testDoubleDown(){
        RoundFixture win = fixture(5, 6, 6, 10, 10, 10);
        win.game.simulateRound();
        check(win.player.getBalance() == 120,
                "Winning a ten-chip double must produce twenty chips of profit");
        check(win.game.getProfit() == -20,
                "Table must pay a winning doubled twenty-chip wager");
        check(win.game.getTotalWagered() == 20,
                "A double must count both ten-chip wagers");
        check(win.player.getCurrentSession().getTableWins() == 1
                        && win.player.getCurrentSession().getTableLosses() == 0,
                "A winning double must be recorded as one winning hand");

        RoundFixture loss = fixture(5, 10, 6, 7, 5);
        loss.game.simulateRound();
        check(loss.player.getBalance() == 80,
                "A losing ten-chip double must lose twenty chips");
        check(loss.game.getProfit() == 20,
                "Table must retain both parts of a losing doubled wager");
        check(loss.player.getCurrentSession().getTableLosses() == 1,
                "A losing double must be recorded as one losing hand");
    }

    private static void testSingleSplitLimit(){
        // The first split hand receives another eight. It must not be split
        // again, so the round still contains exactly two twenty-chip returns.
        RoundFixture fixture = fixture(8, 6, 8, 10, 8, 10, 10);
        fixture.game.simulateRound();
        check(fixture.game.getTotalWagered() == 20,
                "One-split rule must limit the player to two hands");
        check(fixture.game.getTotalHandsSettled() == 2,
                "A single split must settle exactly two hands");
        check(fixture.player.getBalance() == 120,
                "Both split hands must settle independently");
        check(fixture.player.getCurrentSession().getTableWins() == 2,
                "Two winning split hands must record two wins");
    }

    private static void testSplitAces(){
        RoundFixture fixture = fixture(11, 6, 11, 10, 5, 9, 10);
        fixture.game.simulateRound();
        check(fixture.game.getTotalWagered() == 20,
                "Split aces must receive one card each without doubling or hitting");
        check(fixture.player.getBalance() == 120,
                "A two-card 21 after splitting aces must pay even money, not 3:2");
        check(fixture.game.getProfit() == -20,
                "Split-ace winnings must be reflected in table profit");
    }

    private static void testDoubleAfterSplit(){
        RoundFixture fixture = fixture(2, 6, 2, 10, 9, 10, 10, 10);
        fixture.game.simulateRound();
        check(fixture.game.getTotalWagered() == 30,
                "Doubling after a split must collect one additional hand wager");
        check(fixture.player.getBalance() == 130,
                "A doubled split hand and normal split hand must pay independently");
        check(fixture.game.getProfit() == -30,
                "Table accounting must include doubles after splits");
        check(fixture.player.getCurrentSession().getTableWins() == 2
                        && fixture.player.getCurrentSession().getTableLosses() == 0,
                "A doubled split win is still one resolved-hand win");
    }

    private static void testSimpleHitToFifteenStrategy(){
        Blackjack game = new Blackjack(23, 1, 10, 10, 1L);
        game.setShoeForTesting(10, 10, 5, 7, 2);
        FixedBetPlayer player = new FixedBetPlayer(23, "Simple", 1_000_000.0,
                100, 10, new LowStakesStyle());
        player.joinTable(game);
        game.simulateRound();
        check(player.getBalance() == 100,
                "Simple strategy must hit fifteen and push after reaching seventeen");

        Blackjack pairGame = new Blackjack(24, 1, 10, 10, 1L);
        pairGame.setShoeForTesting(8, 6, 8, 10, 10);
        FixedBetPlayer pairPlayer = new FixedBetPlayer(24, "Simple Pair",
                1_000_000.0, 100, 10, new LowStakesStyle());
        pairPlayer.joinTable(pairGame);
        pairGame.simulateRound();
        check(pairGame.getTotalWagered() == 10
                        && pairGame.getTotalHandsSettled() == 1,
                "Simple strategy must not split or double");
    }

    private static void testGrinderFlatBetsRegardlessOfCount(){
        Blackjack game = new Blackjack(26, 1, 5, 500, 1L);
        int[] shoe = new int[104];
        Arrays.fill(shoe, 7);
        shoe[0] = 2;
        shoe[1] = 3;
        shoe[2] = 4;
        shoe[3] = 5;
        game.setShoeForTesting(shoe);

        Player grinder = new Player(26, "Grinder", 1_000,
                PlayerProfile.forStyles(BankrollStyle.EVEN_STEVEN,
                        new GrinderStyle()));
        grinder.joinTable(game);
        int ordinaryBet = grinder.calculateBetAmount(5, 500);
        check(grinder.calculateBlackjackBet(game) == ordinaryBet,
                "A Grinder must bet the ordinary amount at a low true count");
        for(int i = 0; i < 4; i++){
            game.dispellCard();
        }
        check(game.getTrueCount() > 2
                        && grinder.calculateBlackjackBet(game) == ordinaryBet,
                "A Grinder must not change its bet as the true count rises");
    }

    private static void testSharedDealerHand(){
        Blackjack game = new Blackjack(25, 2, 10, 10, 1L);
        // Deal order: P1, P2, dealer up-card, P1, P2, dealer hole-card.
        game.setShoeForTesting(10, 9, 10, 8, 8, 7);
        FixedBetPlayer first = new FixedBetPlayer(25, "First", 1_000_000.0, 100, 10);
        FixedBetPlayer second = new FixedBetPlayer(26, "Second", 1_000_000.0, 100, 10);
        first.joinTable(game);
        second.joinTable(game);

        game.simulateRound();

        check(first.getBalance() == 110,
                "Player 18 must beat the shared dealer 17");
        check(second.getBalance() == 100,
                "Player 17 must push against the same dealer 17");
        check(game.getProfit() == -10,
                "Shared dealer round accounting must include both players");
    }

    private static void testAccountingConservation(){
        Blackjack game = new Blackjack(30, 1, 10, 100, 24680L);
        FixedBetPlayer player = new FixedBetPlayer(30, "Accounting", 1_000_000.0, 1_000_000, 10);
        player.joinTable(game);
        int startingBalance = player.getBalance();
        for(int i = 0; i < 100_000; i++){
            game.simulateRound();
        }
        int playerChange = player.getBalance() - startingBalance;
        check(playerChange + game.getProfit() == 0,
                "Player change and table profit must conserve chips");
    }

    private static void runReturnSimulation(int hands){
        int bet = 10;
        Blackjack game = new Blackjack(40, 1, bet, bet, 987654321L);
        FixedBetPlayer player = new FixedBetPlayer(
                40, "ROI Player", 1_000_000_000_000.0, 200_000_000, bet);
        player.joinTable(game);

        int startingBalance = player.getBalance();
        for(int i = 0; i < hands; i++){
            game.simulateRound();
        }

        long wagered = game.getTotalWagered();
        long playerChange = (long)player.getBalance() - startingBalance;
        long returned = wagered + playerChange;
        double rtp = returned / (double)wagered;
        double playerRoi = playerChange / (double)wagered;
        double houseEdge = game.getProfit() / (double)wagered;
        PlaySessionRecord record = player.getCurrentSession();
        long pushes = game.getTotalPushes();

        check(playerChange + game.getProfit() == 0,
                "ROI simulation accounting must conserve chips");

        System.out.println("Blackjack return simulation");
        System.out.println("---------------------------");
        System.out.println("Rounds: " + hands);
        System.out.println("Resolved hands: " + game.getTotalHandsSettled());
        System.out.println("Base bet: $" + bet);
        System.out.println("Total wagered: $" + wagered);
        System.out.println("Wins: " + record.getTableWins());
        System.out.println("Losses: " + record.getTableLosses());
        System.out.println("Pushes: " + pushes);
        System.out.printf("Player RTP: %.4f%%%n", rtp * 100);
        System.out.printf("Player ROI: %.4f%%%n", playerRoi * 100);
        System.out.printf("Observed house edge: %.4f%%%n", houseEdge * 100);
        System.out.println("Accounting net: " + (playerChange + game.getProfit()));
    }

    private static Blackjack.Hand hand(int... cards){
        Blackjack.Hand hand = new Blackjack.Hand();
        for(int card : cards){
            hand.add(card);
        }
        return hand;
    }

    private static RoundFixture fixture(int... shoe){
        Blackjack game = new Blackjack(20, 1, 10, 10, 1L);
        game.setShoeForTesting(shoe);
        FixedBetPlayer player = new FixedBetPlayer(20, "Rule Test",
                1_000_000.0, 100, 10, new ObnoxiousStyle());
        player.joinTable(game);
        return new RoundFixture(game, player);
    }

    private static void check(boolean condition, String message){
        assertions++;
        if(!condition){
            throw new AssertionError(message);
        }
    }

    private static final class RoundFixture {
        private final Blackjack game;
        private final FixedBetPlayer player;

        private RoundFixture(Blackjack game, FixedBetPlayer player){
            this.game = game;
            this.player = player;
        }
    }

    private static final class FixedBetPlayer extends Player {
        private final int fixedBet;

        private FixedBetPlayer(int id, String name, double unusedBalance, int balance, int fixedBet){
            this(id, name, unusedBalance, balance, fixedBet, new ObnoxiousStyle());
        }

        private FixedBetPlayer(int id, String name, double unusedBalance, int balance,
                               int fixedBet, BehaviorStyle behaviorStyle){
            super(id, name, balance, PlayerProfile.forStyles(
                    BankrollStyle.LOW_ROLLER, behaviorStyle));
            this.fixedBet = fixedBet;
        }

        public int chooseBlackjack(Blackjack game){ return 0; }
        public int chooseCraps(Craps game){ return Craps.PASS_LINE; }
        public int chooseRoulette(Roulette game){ return 0; }
        public int chooseSlots(Slots game){ return 0; }
        public int calculateBetAmount(int minimum, int maximum){ return fixedBet; }
    }
}
