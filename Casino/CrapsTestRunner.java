/**
 * Dependency-free rule, accounting, dice-distribution, and RTP tests for Craps.
 */
public class CrapsTestRunner {

    private static int assertions;

    public static void main(String[] args){
        int roundsPerAction = args.length > 0 ? Integer.parseInt(args[0]) : 2_000_000;

        testDiceDistribution();
        testPassLine();
        testDontPass();
        testField();
        testPropositionBets();
        testSharedDice();
        testAccountingConservation();

        System.out.println("Craps rule assertions passed: " + assertions);
        runReturnSimulations(roundsPerAction);
    }

    private static void testDiceDistribution(){
        Craps game = new Craps(1, 1, 10, 10, 246813579L);
        int rolls = 360_000;
        int[] counts = new int[13];
        for(int i = 0; i < rolls; i++){
            counts[game.rollDice()]++;
        }

        for(int total = 2; total <= 12; total++){
            int combinations = 6 - Math.abs(7 - total);
            double observed = counts[total] / (double)rolls;
            double expected = combinations / 36.0;
            check(Math.abs(observed - expected) < 0.003,
                    "Dice total " + total + " must follow two-dice probability; observed " + observed);
        }
        check(game.getTotalDiceRolls() == rolls, "Every dice roll must be counted");
    }

    private static void testPassLine(){
        Fixture natural = fixture(Craps.PASS_LINE, 7);
        natural.game.simulateRound();
        checkBalanceAndProfit(natural, 110, -10, "Pass line must win on come-out 7");

        Fixture craps = fixture(Craps.PASS_LINE, 2);
        craps.game.simulateRound();
        checkBalanceAndProfit(craps, 90, 10, "Pass line must lose on come-out 2");

        Fixture pointWin = fixture(Craps.PASS_LINE, 6, 5, 6);
        pointWin.game.simulateRound();
        checkBalanceAndProfit(pointWin, 110, -10, "Pass line must win when the point repeats");
        check(pointWin.game.getTotalDiceRolls() == 3,
                "Non-resolving rolls must continue the point cycle");

        Fixture sevenOut = fixture(Craps.PASS_LINE, 8, 7);
        sevenOut.game.simulateRound();
        checkBalanceAndProfit(sevenOut, 90, 10, "Pass line must lose when seven appears before point");
    }

    private static void testDontPass(){
        Fixture push = fixture(Craps.DONT_PASS, 12);
        push.game.simulateRound();
        checkBalanceAndProfit(push, 100, 0, "Don't Pass must push on come-out 12");
        check(push.player.getCurrentSession().getTableWins() == 0
                        && push.player.getCurrentSession().getTableLosses() == 0,
                "Don't Pass push must not count as a win or loss");

        Fixture craps = fixture(Craps.DONT_PASS, 3);
        craps.game.simulateRound();
        checkBalanceAndProfit(craps, 110, -10, "Don't Pass must win on come-out 3");

        Fixture natural = fixture(Craps.DONT_PASS, 11);
        natural.game.simulateRound();
        checkBalanceAndProfit(natural, 90, 10, "Don't Pass must lose on come-out 11");

        Fixture sevenOut = fixture(Craps.DONT_PASS, 5, 7);
        sevenOut.game.simulateRound();
        checkBalanceAndProfit(sevenOut, 110, -10, "Don't Pass must win on seven-out");

        Fixture pointLoss = fixture(Craps.DONT_PASS, 9, 9);
        pointLoss.game.simulateRound();
        checkBalanceAndProfit(pointLoss, 90, 10, "Don't Pass must lose when point repeats");
    }

    private static void testField(){
        Fixture two = fixture(Craps.FIELD, 2);
        two.game.simulateRound();
        checkBalanceAndProfit(two, 120, -20, "Field 2 must pay 2:1");

        Fixture twelve = fixture(Craps.FIELD, 12);
        twelve.game.simulateRound();
        checkBalanceAndProfit(twelve, 130, -30, "Field 12 must pay 3:1");

        Fixture normalWin = fixture(Craps.FIELD, 10);
        normalWin.game.simulateRound();
        checkBalanceAndProfit(normalWin, 110, -10, "Normal field number must pay 1:1");

        Fixture loss = fixture(Craps.FIELD, 8);
        loss.game.simulateRound();
        checkBalanceAndProfit(loss, 90, 10, "Field must lose on 8");
    }

    private static void testPropositionBets(){
        Fixture seven = fixture(Craps.ANY_SEVEN, 7);
        seven.game.simulateRound();
        checkBalanceAndProfit(seven, 140, -40, "Any Seven must pay 4:1");

        Fixture anyCraps = fixture(Craps.ANY_CRAPS, 3);
        anyCraps.game.simulateRound();
        checkBalanceAndProfit(anyCraps, 170, -70, "Any Craps must pay 7:1");

        Fixture yo = fixture(Craps.YO_ELEVEN, 11);
        yo.game.simulateRound();
        checkBalanceAndProfit(yo, 250, -150, "Yo 11 must pay 15:1");

        Fixture losingYo = fixture(Craps.YO_ELEVEN, 12);
        losingYo.game.simulateRound();
        checkBalanceAndProfit(losingYo, 90, 10, "Yo 11 must lose on every other total");
    }

    private static void testSharedDice(){
        Craps game = new Craps(20, 2, 10, 10, 1L);
        game.setRollsForTesting(6, 7);
        FixedCrapsPlayer pass = player(20, "Pass", Craps.PASS_LINE, 100);
        FixedCrapsPlayer dont = player(21, "Don't", Craps.DONT_PASS, 100);
        pass.joinTable(game);
        dont.joinTable(game);

        game.simulateRound();

        check(pass.getBalance() == 90, "Pass player must lose on the shared seven-out");
        check(dont.getBalance() == 110, "Don't Pass player must win on the same seven-out");
        check(game.getProfit() == 0, "Opposing equal line wagers must net to zero");
        check(game.getTotalDiceRolls() == 2, "All table players must share the same dice");
    }

    private static void testAccountingConservation(){
        Craps game = new Craps(30, 6, 10, 10, 99887766L);
        FixedCrapsPlayer[] players = new FixedCrapsPlayer[6];
        long startingChips = 0;
        for(int action = 0; action < players.length; action++){
            players[action] = player(30 + action, "Accounting-" + action, action, 100_000_000);
            players[action].joinTable(game);
            startingChips += players[action].getBalance();
        }

        for(int round = 0; round < 100_000; round++){
            game.simulateRound();
        }

        long endingChips = 0;
        for(FixedCrapsPlayer player : players){
            endingChips += player.getBalance();
        }
        check((endingChips - startingChips) + game.getProfit() == 0,
                "Craps player changes and table profit must conserve chips");
    }

    private static void runReturnSimulations(int rounds){
        System.out.println("Craps return simulation");
        System.out.println("-----------------------");
        simulateReturn(Craps.PASS_LINE, "Pass Line", 488 / 495.0, rounds, 1001L);
        simulateReturn(Craps.DONT_PASS, "Don't Pass", 1953 / 1980.0, rounds, 1002L);
        simulateReturn(Craps.FIELD, "Field", 35 / 36.0, rounds, 1003L);
        simulateReturn(Craps.ANY_SEVEN, "Any Seven", 30 / 36.0, rounds, 1004L);
        simulateReturn(Craps.ANY_CRAPS, "Any Craps", 32 / 36.0, rounds, 1005L);
        simulateReturn(Craps.YO_ELEVEN, "Yo (11)", 32 / 36.0, rounds, 1006L);
    }

    private static void simulateReturn(int action, String label, double expectedRtp,
                                       int rounds, long seed){
        Craps game = new Craps(40 + action, 1, 10, 10, seed);
        FixedCrapsPlayer player = player(40 + action, label, action, 500_000_000);
        player.joinTable(game);
        int startingChips = player.getBalance();

        for(int i = 0; i < rounds; i++){
            game.simulateRound();
        }

        long change = (long)player.getBalance() - startingChips;
        double rtp = (game.getTotalWagered() + change) / (double)game.getTotalWagered();
        check(change + game.getProfit() == 0, label + " accounting must conserve chips");
        check(Math.abs(rtp - expectedRtp) < 0.01,
                label + " simulated RTP must approach mathematical RTP");
        System.out.printf("%-11s observed RTP=%8.4f%%  expected=%8.4f%%  house edge=%7.4f%%%n",
                label, rtp * 100, expectedRtp * 100, (1 - rtp) * 100);
    }

    private static Fixture fixture(int action, int... rolls){
        Craps game = new Craps(10, 1, 10, 10, 1L);
        game.setRollsForTesting(rolls);
        FixedCrapsPlayer player = player(10, "Rule Player", action, 100);
        player.joinTable(game);
        return new Fixture(game, player);
    }

    private static FixedCrapsPlayer player(int id, String name, int action, int chips){
        return new FixedCrapsPlayer(id, name, 1_000_000_000.0, chips, 10, action);
    }

    private static void checkBalanceAndProfit(Fixture fixture, int balance, int profit,
                                              String message){
        check(fixture.player.getBalance() == balance, message + " (player balance)");
        check(fixture.game.getProfit() == profit, message + " (table profit)");
        check(fixture.game.getTotalWagered() == 10, message + " (wager tracking)");
    }

    private static void check(boolean condition, String message){
        assertions++;
        if(!condition){
            throw new AssertionError(message);
        }
    }

    private static final class Fixture {
        private final Craps game;
        private final FixedCrapsPlayer player;

        private Fixture(Craps game, FixedCrapsPlayer player){
            this.game = game;
            this.player = player;
        }
    }

    private static final class FixedCrapsPlayer extends Player {
        private final int fixedBet;
        private final int action;

        private FixedCrapsPlayer(int id, String name, double balance, int chips,
                                 int fixedBet, int action){
            super(id, name, chips);
            this.fixedBet = fixedBet;
            this.action = action;
        }

        public int calculateBetAmount(int minimum, int maximum){ return fixedBet; }
        public int chooseCraps(Craps game){ return action; }
        public int chooseBlackjack(Blackjack game){ return 0; }
        public int chooseRoulette(Roulette game){ return 0; }
        public int chooseSlots(Slots game){ return 0; }
    }
}
