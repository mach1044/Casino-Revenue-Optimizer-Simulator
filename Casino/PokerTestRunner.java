/**
 * Dependency-free deterministic and statistical tests for heuristic Poker.
 */
public class PokerTestRunner {

    private static int assertions;

    public static void main(String[] args){
        int skillHands = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;

        testTraitValidation();
        testInsufficientParticipants();
        testPotAndRakeAccounting();
        testRakeCap();
        testParticipationIndependence();
        testAggressionControlsSharedContribution();
        testAllInSidePots();
        testSkillControlsWinning(skillHands);

        System.out.println("Poker assertions passed: " + assertions);
    }

    private static void testTraitValidation(){
        TestPlayer player = player(1, "Traits", 100);
        player.setPokerParticipation(0.2);
        player.setPokerAggression(0.7);
        player.setPokerSkill(0.9);
        check(player.getPokerParticipation() == 0.2, "Participation must be stored independently");
        check(player.getPokerAggression() == 0.7, "Aggression must be stored independently");
        check(player.getPokerSkill() == 0.9, "Skill must be stored independently");
        check(player.getPokerTraits().getParticipation() == 0.2
                        && player.getPokerTraits().getAggression() == 0.7
                        && player.getPokerTraits().getSkill() == 0.9,
                "PokerTraits must own all three player poker values");

        checkThrows(() -> player.setPokerParticipation(-0.01),
                "Negative participation must be rejected");
        checkThrows(() -> player.setPokerAggression(1.01),
                "Aggression above one must be rejected");
        checkThrows(() -> player.setPokerSkill(Double.NaN),
                "NaN skill must be rejected");
    }

    private static void testInsufficientParticipants(){
        Poker game = new Poker(2, 2, 10, 10, 5L);
        TestPlayer active = player(2, "Active", 100);
        TestPlayer inactive = player(3, "Inactive", 100);
        active.setPokerParticipation(1);
        inactive.setPokerParticipation(0);
        active.joinTable(game);
        inactive.joinTable(game);

        for(int i = 0; i < 1_000; i++){
            game.simulateRound();
        }

        check(game.getAttemptedHands() == 1_000, "Attempted poker hands must be tracked");
        check(game.getPlayedHands() == 0, "A poker hand requires at least two participants");
        check(game.getTotalPotVolume() == 0 && game.getProfit() == 0,
                "Cancelled hands must not move chips");
        check(active.getBalance() == 100 && inactive.getBalance() == 100,
                "A lone interested player must not be charged");
    }

    private static void testPotAndRakeAccounting(){
        Poker game = new Poker(3, 3, 10, 10, 1L);
        TestPlayer[] players = threePlayers(game, 10_000);
        long starting = totalChips(players);

        game.simulateRound();

        long ending = totalChips(players);
        check(game.getPlayedHands() == 1, "Three willing players must produce one hand");
        check(game.getTotalPotVolume() == 30, "Three ten-chip contributions must create a 30-chip pot");
        check(game.getTotalRake() == 2, "Five percent of 30 chips must round to two chips");
        check(game.getProfit() == 2, "Poker table profit must equal its rake");
        check(starting - ending == 2, "Players collectively lose exactly the casino rake");

        int wins = 0;
        int losses = 0;
        for(TestPlayer player : players){
            wins += player.getCurrentSession().getTableWins();
            losses += player.getCurrentSession().getTableLosses();
        }
        check(wins == 1 && losses == 2,
                "Each played hand must have one winner and all other participants lose");
    }

    private static void testRakeCap(){
        Poker game = new Poker(4, 2, 100, 100, 0.10, 5, 2L);
        TestPlayer first = player(4, "First", 10_000);
        TestPlayer second = player(5, "Second", 10_000);
        first.setPokerParticipation(1);
        second.setPokerParticipation(1);
        first.joinTable(game);
        second.joinTable(game);

        game.simulateRound();

        check(game.getTotalPotVolume() == 200, "Two 100-chip entries must make a 200-chip pot");
        check(game.getTotalRake() == 5 && game.getProfit() == 5,
                "Rake must never exceed its configured cap");
    }

    private static void testParticipationIndependence(){
        Poker game = new Poker(5, 3, 10, 10, 0, 0, 987654L);
        TestPlayer never = player(6, "Never", 1_000_000_000);
        TestPlayer sometimes = player(7, "Sometimes", 1_000_000_000);
        TestPlayer always = player(8, "Always", 1_000_000_000);
        never.setPokerParticipation(0);
        sometimes.setPokerParticipation(0.25);
        always.setPokerParticipation(1);
        // Deliberately give all three different aggression and skill values;
        // neither may alter the independent participation roll.
        never.setPokerAggression(1);
        never.setPokerSkill(1);
        sometimes.setPokerAggression(0);
        sometimes.setPokerSkill(0);
        never.joinTable(game);
        sometimes.joinTable(game);
        always.joinTable(game);

        int attempts = 200_000;
        for(int i = 0; i < attempts; i++){
            game.simulateRound();
        }

        double observed = game.getHandsEntered(sometimes) / (double)attempts;
        check(game.getHandsEntered(never) == 0,
                "Zero participation must remain zero regardless of skill/aggression");
        check(Math.abs(observed - 0.25) < 0.005,
                "Participation must converge to its own probability; observed " + observed);
        check(game.getHandsEntered(always) == game.getPlayedHands(),
                "Always-participating player must enter every hand that actually plays");
    }

    private static void testAggressionControlsSharedContribution(){
        Poker passiveGame = new Poker(6, 2, 10, 110, 0, 0, 456789L);
        TestPlayer passiveOne = player(9, "Passive-1", 1_000_000_000);
        TestPlayer passiveTwo = player(10, "Passive-2", 1_000_000_000);
        configurePokerPlayer(passiveOne, 0, 0.5);
        configurePokerPlayer(passiveTwo, 0, 0.5);
        passiveOne.joinTable(passiveGame);
        passiveTwo.joinTable(passiveGame);

        Poker aggressiveGame = new Poker(7, 2, 10, 110, 0, 0, 456789L);
        TestPlayer aggressiveOne = player(11, "Aggressive-1", 1_000_000_000);
        TestPlayer aggressiveTwo = player(12, "Aggressive-2", 1_000_000_000);
        configurePokerPlayer(aggressiveOne, 1, 0.5);
        configurePokerPlayer(aggressiveTwo, 1, 0.5);
        aggressiveOne.joinTable(aggressiveGame);
        aggressiveTwo.joinTable(aggressiveGame);

        int hands = 100_000;
        for(int i = 0; i < hands; i++){
            passiveGame.simulateRound();
            aggressiveGame.simulateRound();
        }

        long passiveOneTotal = passiveGame.getTotalContributed(passiveOne);
        long passiveTwoTotal = passiveGame.getTotalContributed(passiveTwo);
        long aggressiveOneTotal = aggressiveGame.getTotalContributed(aggressiveOne);
        long aggressiveTwoTotal = aggressiveGame.getTotalContributed(aggressiveTwo);
        double passiveAverage = passiveOneTotal / (double)hands;
        double aggressiveAverage = aggressiveOneTotal / (double)hands;

        check(passiveOneTotal == passiveTwoTotal,
                "Players in a passive hand must make the same contribution");
        check(aggressiveOneTotal == aggressiveTwoTotal,
                "Players in an aggressive hand must make the same contribution");
        check(aggressiveAverage > passiveAverage * 5,
                "Aggressive tables must create much larger matched wagers; passive "
                        + passiveAverage + ", aggressive " + aggressiveAverage);
        check(passiveOne.getCurrentSession().getTableWins()
                        + passiveTwo.getCurrentSession().getTableWins() == hands,
                "Every passive hand must still have exactly one winner");
        check(aggressiveOne.getCurrentSession().getTableWins()
                        + aggressiveTwo.getCurrentSession().getTableWins() == hands,
                "Every aggressive hand must still have exactly one winner");
    }

    private static void testAllInSidePots(){
        boolean exercised = false;

        // Find a deterministic hand in which the ten-chip player wins the
        // showdown while the common target reaches the table maximum.
        for(long seed = 0; seed < 1_000 && !exercised; seed++){
            Poker game = new Poker(8, 4, 10, 50, 0, 0, seed);
            TestPlayer shortStack = player(30, "Short", 10);
            TestPlayer middleStack = player(31, "Middle", 30);
            TestPlayer deepOne = player(32, "Deep-1", 50);
            TestPlayer deepTwo = player(33, "Deep-2", 100);
            TestPlayer[] players = {shortStack, middleStack, deepOne, deepTwo};

            for(TestPlayer player : players){
                configurePokerPlayer(player, 1, player == shortStack ? 1 : 0);
                player.joinTable(game);
            }

            game.simulateRound();
            boolean fullTarget = game.getTotalContributed(shortStack) == 10
                    && game.getTotalContributed(middleStack) == 30
                    && game.getTotalContributed(deepOne) == 50
                    && game.getTotalContributed(deepTwo) == 50;
            if(fullTarget && shortStack.getCurrentSession().getTableWins() == 1){
                exercised = true;
                check(game.getTotalPotVolume() == 140,
                        "All-in contributions must all remain in the total pot");
                check(game.getTotalContributed(deepTwo) == 50,
                        "A deepest stack must not contribute chips no opponent can match");
                check(shortStack.getBalance() == 40,
                        "Short-stack winner must receive only the forty-chip main pot");
                check(middleStack.getBalance() + deepOne.getBalance()
                                + deepTwo.getBalance() == 150,
                        "Side pots and unmatched chips must remain with eligible deeper stacks");
                check(totalChips(players) == 190 && game.getProfit() == 0,
                        "Side-pot payouts must conserve every chip when rake is disabled");
            }
        }

        check(exercised, "A deterministic short-stack side-pot fixture must be reachable");
    }

    private static void testSkillControlsWinning(int hands){
        Poker game = new Poker(9, 2, 10, 10, 1357911L);
        TestPlayer novice = player(40, "Novice", 1_000_000_000);
        TestPlayer expert = player(41, "Expert", 1_000_000_000);
        novice.setPokerParticipation(1);
        expert.setPokerParticipation(1);
        novice.setPokerSkill(0);
        expert.setPokerSkill(1);
        novice.joinTable(game);
        expert.joinTable(game);
        int noviceStart = novice.getBalance();
        int expertStart = expert.getBalance();

        for(int i = 0; i < hands; i++){
            game.simulateRound();
        }

        double expertWinRate = expert.getCurrentSession().getTableWins() / (double)hands;
        long noviceNet = (long)novice.getBalance() - noviceStart;
        long expertNet = (long)expert.getBalance() - expertStart;
        check(Math.abs(expertWinRate - 0.75) < 0.005,
                "Skill weights 1.5 versus 0.5 must give expert about 75% wins; observed "
                        + expertWinRate);
        check(expertNet > 0 && noviceNet < 0,
                "Higher skill must win chips over a sufficiently long equal-stake simulation");
        check(game.getTotalRake() == hands,
                "A 20-chip pot at five-percent rake must retain one chip per hand");
        check(noviceNet + expertNet + game.getProfit() == 0,
                "Player changes plus poker rake must conserve every chip");

        System.out.println("Poker skill simulation");
        System.out.println("----------------------");
        System.out.println("Hands: " + hands);
        System.out.printf("Expert win rate: %.4f%% (expected 75.0000%%)%n", expertWinRate * 100);
        System.out.printf("Expert ROI: %+,.4f%%%n", expertNet * 100.0 / game.getTotalContributed(expert));
        System.out.printf("Novice ROI: %+,.4f%%%n", noviceNet * 100.0 / game.getTotalContributed(novice));
        System.out.println("Casino rake: $" + game.getTotalRake());
        System.out.println("Accounting net: " + (noviceNet + expertNet + game.getProfit()));
    }

    private static TestPlayer[] threePlayers(Poker game, int chips){
        TestPlayer[] players = new TestPlayer[3];
        for(int i = 0; i < players.length; i++){
            players[i] = player(20 + i, "Player-" + i, chips);
            players[i].setPokerParticipation(1);
            players[i].joinTable(game);
        }
        return players;
    }

    private static TestPlayer player(int id, String name, int chips){
        return new TestPlayer(id, name, 1_000_000_000.0, chips);
    }

    private static void configurePokerPlayer(TestPlayer player,
                                             double aggression, double skill){
        player.setPokerParticipation(1);
        player.setPokerAggression(aggression);
        player.setPokerSkill(skill);
    }

    private static long totalChips(TestPlayer[] players){
        long total = 0;
        for(TestPlayer player : players){
            total += player.getBalance();
        }
        return total;
    }

    private static void checkThrows(Runnable operation, String message){
        boolean threw = false;
        try{
            operation.run();
        }
        catch(IllegalArgumentException expected){
            threw = true;
        }
        check(threw, message);
    }

    private static void check(boolean condition, String message){
        assertions++;
        if(!condition){
            throw new AssertionError(message);
        }
    }

    private static final class TestPlayer extends Player {
        private TestPlayer(int id, String name, double balance, int chips){
            super(id, name, chips);
        }

        public int calculateBetAmount(int minimum, int maximum){ return minimum; }
        public int chooseBlackjack(Blackjack game){ return 0; }
        public int chooseCraps(Craps game){ return Craps.PASS_LINE; }
        public int chooseRoulette(Roulette game){ return 0; }
        public int chooseSlots(Slots game){ return 0; }
    }
}
