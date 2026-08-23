/*                                                                           *
 * Roulette.java                                                             *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-10                                                                *
 * ------------------------------------------------------------------------- *
 * Class responsible for hosting roulette                                    */

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Roulette extends Game {

    /** Payout ratios above this get the reduced highPayoutMaxBet ceiling. */
    private static final double HIGH_PAYOUT_THRESHOLD = 3;

    //Amount of possible numbers which can be rolled
    private final int numbers = 37;

    //Reduced bet ceiling for categories paying more than HIGH_PAYOUT_THRESHOLD to 1
    private final int highPayoutMaxBet;
    private final char [] colours = {'G',
                               'R','B','R', /*1,2,3*/
                               'B','R','B', /*4,5,6*/
                               'R','B','R', /*7,8,9*/
                               'B','B','R', /*10,11,12*/
                               'B','R','B', /*13,14,15*/
                               'R','B','R', /*16,17,18*/
                               'R','B','R', /*19,20,21*/
                               'B','R','B', /*22,23,24*/
                               'R','B','R', /*25,26,27*/
                               'B','B','R', /*28,29,30*/
                               'B','R','B', /*31,32,33*/
                               'R','B','R'  /*34,35,36 */
                              };
    
    /*
     * Constructor to initialize the Roulette game with given parameters.
     * Sets up the actions for different types of bets.
     * 
     * Parameters:
     * - id: The ID of the roulette table.
     * - tablePop: The population of the table (number of players).
     * - minBet: The minimum bet for the game.
     * - maxBet: The maximum bet for the game.
     * 
     * Returns:
     * - None
     */
    public Roulette(int id, int tablePop, int minBet, int maxBet){
        this(id, 0, tablePop, minBet, maxBet, maxBet);
    }

    /*
     * Constructor to initialize the Roulette game with given parameters.
     * Sets up the actions for different types of bets.
     *
     * Parameters:
     * - id: The ID of the roulette table.
     * - profit: The profit of the table.
     * - tablePop: The population of the table (number of players).
     * - minBet: The minimum bet for the game.
     * - maxBet: The maximum bet for the game.
     *
     * Returns:
     * - None
     */
    public Roulette(int id, int profit, int tablePop, int minBet, int maxBet){
        this(id, profit, tablePop, minBet, maxBet, maxBet);
    }

    /*
     * Constructor to initialize the Roulette game with a reduced bet ceiling
     * for high-payout categories (Line-Odds and above).
     *
     * Parameters:
     * - id: The ID of the roulette table.
     * - profit: The profit of the table.
     * - tablePop: The population of the table (number of players).
     * - minBet: The minimum bet for the game.
     * - maxBet: The maximum bet for the game, for categories paying 3-to-1 or less.
     * - highPayoutMaxBet: The maximum bet for categories paying more than 3-to-1.
     */
    public Roulette(int id, int profit, int tablePop, int minBet, int maxBet, int highPayoutMaxBet){
        super(id, profit, tablePop, minBet, maxBet);
        this.highPayoutMaxBet = highPayoutMaxBet;
        numActions = 7;
        actions  = new Action[numActions];
        actions[0] = new Action(0,"Half-Odds",18/37f,1);
        actions[1] = new Action(1,"Third-Odds",12/37f,2);
        actions[2] = new Action(2,"Line-Odds",6/37f,5);
        actions[3] = new Action(3,"Corner-Odds",4/37f,8);
        actions[4] = new Action(4,"Street-Odds",3/37f,11);
        actions[5] = new Action(5,"Double-Odds",2/37f,17);
        actions[6] = new Action(6,"One-Odds",1/37f,35);
    }

    public String toString() {
        return "Roulette - " + super.toString();
    }

    /*
     * Simulates a round of roulette, determining the winning bets based on a randomly 
     * chosen number, and processes each player's bets, updating their balances and the
     * table's revenue accordingly.
     * 
     * Parameters:
     * - None
     * 
     * Returns:
     * - None
     */
    public void simulateRound(){
        
        ArrayList<String> winningBets = new ArrayList<String>(); //The bets that win that round

        int resultingNumber = randInt(0, numbers-1);

        // Zero loses all outside bets in European roulette.
        if(resultingNumber > 0){
            winningBets.add("H-" + colours[resultingNumber]);
            winningBets.add(resultingNumber >= 19 ? "H-H" : "H-L");
            winningBets.add(resultingNumber % 2 == 0 ? "H-E" : "H-O");
            winningBets.add("T-C-" + ((resultingNumber-1) % 3));
            winningBets.add("T-D-" + ((resultingNumber-1) / 12));
            winningBets.add("L-" + ((resultingNumber-1) / 6));
            winningBets.add("C-" + ((resultingNumber-1) / 4));
            winningBets.add("S-" + ((resultingNumber-1) / 3));
            winningBets.add("D-" + ((resultingNumber-1) / 2));
        }
        winningBets.add("O-" + resultingNumber);

        //Character codes which represent half probability and third probability bets
        char[] thirdCodes = {'C','D'};

        //Loops through each player at the table
        for(int i = 0; i < numPlayers; i++){
            
            Player currPlayer = currentTable.get(i);

            //Choose the bet category first so the bet-amount ceiling can
            //reflect that category's payout (high-payout categories are
            //capped lower than the table's regular maximum).
            int betType = currPlayer.chooseRoulette(this);
            int effectiveMaxBet = actions[betType].getPayoutRatio() > HIGH_PAYOUT_THRESHOLD
                    ? highPayoutMaxBet : this.getMaxBet();
            int betAmnt = currPlayer.calculateBetAmount(this.getMinBet(), effectiveMaxBet);

            if(!collectBet(currPlayer, betAmnt)){
                continue;
            }

            //Begin constructing bet code based on what probability bet they chose
            String bet = "";
            bet += actions[betType].getName().charAt(0);
            //Based on probability code
            //Add a random kind of bet to the end
            switch(bet.charAt(0)){      
                case 'H': //Half
                    bet += "-";
                    bet += chooseHalfCode();
                    break;
                case 'T': //Third
                    bet += "-";
                    bet += thirdCodes[randInt(0, thirdCodes.length-1)];
                    bet += "-";
                    bet += randInt(0, 2);
                    break;
                case 'L': //Six line
                    bet += "-";
                    bet += randInt(0, 5);
                    break;
                case 'C': //Corner
                    bet += "-";
                    bet += randInt(0, 8);
                    break;
                case 'S': //Street (3 nums)
                    bet += "-";
                    bet += randInt(0, 11);
                    break;
                case 'D': //Double (split)
                    bet += "-";
                    bet += randInt(0, 17);
                    break;
                case 'O': //One (straight-up)
                    bet += "-";
                    bet += randInt(0, 36);
                    break;
            }
            
            if(winningBets.contains(bet)){
                distributeWinnings(currPlayer, (int)(betAmnt*(actions[betType].getPayoutRatio()+1)));
            }

        }
    }
 
    /*
     * Returns a random integer between the specified min (inclusive) and max (inclusive) values.
     * 
     * Parameters:
     * - min: The minimum value that can be returned (inclusive).
     * - max: The maximum value that can be returned (inclusive).
     * 
     * Returns:
     * - A random integer between min and max (inclusive).
     */
    private int randInt(int min, int max){
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** Red/black dominates half bets; parity and range split the remainder. */
    char chooseHalfCode(){
        double selection = ThreadLocalRandom.current().nextDouble();
        if(selection < 0.70){
            return ThreadLocalRandom.current().nextBoolean() ? 'R' : 'B';
        }
        if(selection < 0.85){
            return ThreadLocalRandom.current().nextBoolean() ? 'E' : 'O';
        }
        return ThreadLocalRandom.current().nextBoolean() ? 'H' : 'L';
    }

}
