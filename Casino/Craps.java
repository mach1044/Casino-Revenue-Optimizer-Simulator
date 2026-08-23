/*
 * Craps.java
 * -------------------------------------------------------------------------
 * Simulates a shared craps table. Each simulation round begins with a
 * come-out roll and, when necessary, continues until the point or seven is
 * rolled. All players at the table see the same dice.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Craps extends Game {

    public static final int PASS_LINE = 0;
    public static final int DONT_PASS = 1;
    public static final int FIELD = 2;
    public static final int ANY_SEVEN = 3;
    public static final int ANY_CRAPS = 4;
    public static final int YO_ELEVEN = 5;

    private final Random random;
    private int[] testRolls;
    private int nextTestRoll;
    private long totalWagered;
    private long totalDiceRolls;

    public Craps(int id, int tablePop, int minBet, int maxBet){
        this(id, tablePop, minBet, maxBet, new Random());
    }

    public Craps(int id, int tablePop, int minBet, int maxBet, long seed){
        this(id, tablePop, minBet, maxBet, new Random(seed));
    }

    private Craps(int id, int tablePop, int minBet, int maxBet, Random random){
        super(id, tablePop, minBet, maxBet);
        this.random = random;
        initializeActions();
    }

    public Craps(int id, int profit, int tablePop, int minBet, int maxBet){
        super(id, profit, tablePop, minBet, maxBet);
        random = new Random();
        initializeActions();
    }

    private void initializeActions(){
        numActions = 6;
        actions = new Action[numActions];
        actions[PASS_LINE] = new Action(PASS_LINE, "Pass Line", 244 / 495.0, 1);
        actions[DONT_PASS] = new Action(DONT_PASS, "Don't Pass", 949 / 1980.0, 1);
        // The field normally pays 1:1, with 2 paying 2:1 and 12 paying 3:1.
        actions[FIELD] = new Action(FIELD, "Field", 16 / 36.0, 1);
        actions[ANY_SEVEN] = new Action(ANY_SEVEN, "Any Seven", 6 / 36.0, 4);
        actions[ANY_CRAPS] = new Action(ANY_CRAPS, "Any Craps", 4 / 36.0, 7);
        actions[YO_ELEVEN] = new Action(YO_ELEVEN, "Yo (11)", 2 / 36.0, 15);
    }

    public long getTotalWagered(){
        return totalWagered;
    }

    public long getTotalDiceRolls(){
        return totalDiceRolls;
    }

    @Override
    public void simulateRound(){
        ArrayList<CrapsBet> bets = new ArrayList<CrapsBet>();
        for(Player player : currentTable){
            int action = player.chooseCraps(this);
            if(action < 0 || action >= numActions){
                continue;
            }

            int amount = player.calculateBetAmount(getMinBet(), getMaxBet());
            if(collectBet(player, amount)){
                totalWagered += amount;
                bets.add(new CrapsBet(player, action, amount));
            }
        }

        if(bets.isEmpty()){
            return;
        }

        int comeOutRoll = rollDice();
        ArrayList<CrapsBet> unresolvedLineBets = new ArrayList<CrapsBet>();
        for(CrapsBet bet : bets){
            if(bet.action == PASS_LINE){
                settlePassComeOut(bet, comeOutRoll, unresolvedLineBets);
            }
            else if(bet.action == DONT_PASS){
                settleDontPassComeOut(bet, comeOutRoll, unresolvedLineBets);
            }
            else{
                settleSingleRollBet(bet, comeOutRoll);
            }
        }

        if(unresolvedLineBets.isEmpty()){
            return;
        }

        int point = comeOutRoll;
        int result;
        do{
            result = rollDice();
        }while(result != point && result != 7);

        for(CrapsBet bet : unresolvedLineBets){
            boolean passWins = result == point;
            boolean playerWins = bet.action == PASS_LINE ? passWins : !passWins;
            if(playerWins){
                distributeWinnings(bet.player, bet.amount * 2);
            }
        }
    }

    private void settlePassComeOut(CrapsBet bet, int roll,
                                   ArrayList<CrapsBet> unresolved){
        if(roll == 7 || roll == 11){
            distributeWinnings(bet.player, bet.amount * 2);
        }
        else if(isPoint(roll)){
            unresolved.add(bet);
        }
        // A pass-line wager loses on 2, 3, or 12.
    }

    private void settleDontPassComeOut(CrapsBet bet, int roll,
                                       ArrayList<CrapsBet> unresolved){
        if(roll == 2 || roll == 3){
            distributeWinnings(bet.player, bet.amount * 2);
        }
        else if(roll == 12){
            returnBet(bet.player, bet.amount);
        }
        else if(isPoint(roll)){
            unresolved.add(bet);
        }
        // A don't-pass wager loses on 7 or 11.
    }

    private void settleSingleRollBet(CrapsBet bet, int roll){
        switch(bet.action){
            case FIELD:
                if(roll == 2){
                    distributeWinnings(bet.player, bet.amount * 3);
                }
                else if(roll == 12){
                    distributeWinnings(bet.player, bet.amount * 4);
                }
                else if(roll == 3 || roll == 4 || roll == 9
                        || roll == 10 || roll == 11){
                    distributeWinnings(bet.player, bet.amount * 2);
                }
                break;
            case ANY_SEVEN:
                if(roll == 7){
                    distributeWinnings(bet.player, bet.amount * 5);
                }
                break;
            case ANY_CRAPS:
                if(roll == 2 || roll == 3 || roll == 12){
                    distributeWinnings(bet.player, bet.amount * 8);
                }
                break;
            case YO_ELEVEN:
                if(roll == 11){
                    distributeWinnings(bet.player, bet.amount * 16);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported craps action: " + bet.action);
        }
    }

    private boolean isPoint(int roll){
        return roll == 4 || roll == 5 || roll == 6
                || roll == 8 || roll == 9 || roll == 10;
    }

    int rollDice(){
        totalDiceRolls++;
        if(testRolls != null && nextTestRoll < testRolls.length){
            return testRolls[nextTestRoll++];
        }
        return random.nextInt(6) + 1 + random.nextInt(6) + 1;
    }

    void setRollsForTesting(int... rolls){
        if(rolls == null || rolls.length == 0){
            throw new IllegalArgumentException("Test rolls cannot be empty");
        }
        for(int roll : rolls){
            if(roll < 2 || roll > 12){
                throw new IllegalArgumentException("Dice total must be between 2 and 12");
            }
        }
        testRolls = Arrays.copyOf(rolls, rolls.length);
        nextTestRoll = 0;
    }

    @Override
    public String toString(){
        return "Craps - " + super.toString();
    }

    private static final class CrapsBet {
        private final Player player;
        private final int action;
        private final int amount;

        private CrapsBet(Player player, int action, int amount){
            this.player = player;
            this.action = action;
            this.amount = amount;
        }
    }
}
