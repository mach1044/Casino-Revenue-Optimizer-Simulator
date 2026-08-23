/*                                                                           *
 * Game.java                                                                 *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-10                                                                *
 * ------------------------------------------------------------------------- *
 * Abstract Class responsible for facilitating all basic functions in games  *
 * (child classes)                                                           */

import java.util.ArrayList;

public abstract class Game{
    private int id;
    private int maxTablePopulation;
    private int minBet;
    private int maxBet;
    private int profit;

    protected int numPlayers;
    protected ArrayList<Player> currentTable;

    //These will be assigned in the children
    protected int numActions;
    protected Action[] actions;

    private int numSessions;
    private ArrayList<PlaySessionRecord> playerHistory;

    // Getter for id
    public int getId() {return id;}
    // Getter for numActions
    public int getNumActions() {return numActions;}
    // Getter for numPlayers
    public int getNumPlayers() {return numPlayers;}
    //Getter for numSessions
    public int getNumSessions() {return numSessions;}
    // Getter for maxTablePopulation
    public int getMaxTablePopulation() {return maxTablePopulation;}
    // Getter for minBet
    public int getMinBet() {return minBet;}
    // Getter for maxBet
    public int getMaxBet() {return maxBet;}
    //Getter for profit
    public int getProfit() {return profit;}
    //Getter for currentTable  
    public ArrayList<Player> getCurrentTable() {return currentTable;}
    //Getter for actions
    public Action[] getActions() {return actions;}
    //Getter for playerHistory
    public ArrayList<PlaySessionRecord> getPlayerHistory() {return playerHistory;}
    
    public void addRecord(PlaySessionRecord record){
        playerHistory.add(record);
        numSessions++;
    }

    /*
     * Adds the specified amount to the game's profit.
     * 
     * Parameters:
     * - num: The amount to add to the profit.
     * 
     * Returns:
     * - true if the amount was added successfully, false if the amount is invalid (<= 0).
     */
    public boolean addRevenue(int num){
        if(num > 0){
            profit += num;
            return true;
        }

        //revenue is invalid if it is negative
        return false;
    }

    /*
     * Subtracts the specified amount from the game's profit.
     * 
     * Parameters:
     * - num: The amount to subtract from the profit.
     * 
     * Returns:
     * - true if the amount was subtracted successfully, false if the amount is invalid (<= 0).
     */
    public boolean substractLoss(int num){
        if(num > 0){
            profit -= num;
            return true;
        }
        return false;
    }


    /*
     * Constructs a new Game with the specified game parameters.
     * 
     * Parameters:
     * - id: The unique ID for the game.
     * - tablePop: The maximum number of players allowed at the game table.
     * - minBet: The minimum bet that can be placed in the game.
     * - maxBet: The maximum bet that can be placed in the game.
     */
    public Game(int id, int tablePop, int minBet, int maxBet){
        this.id = id;
        this.maxTablePopulation = tablePop;
        this.minBet = minBet;
        this.maxBet = maxBet;

        numPlayers = 0;
        numSessions = 0;
        currentTable = new ArrayList<Player>();
        playerHistory = new ArrayList<PlaySessionRecord>();
    }

    public Game(int id, int profit, int tablePop, int minBet, int maxBet){
        this.id = id;
        this.profit = profit;
        this.maxTablePopulation = tablePop;
        this.minBet = minBet;
        this.maxBet = maxBet;

        numPlayers = 0;
        numSessions = 0;
        currentTable = new ArrayList<Player>();
        playerHistory = new ArrayList<PlaySessionRecord>();
    }

    public String toString(){
        return "Game ID: " + id + "\nTable Space: " + maxTablePopulation + "\nProfit: " + profit + "\nMinimum Bet: " + minBet + "\nMaximum Bet: " + maxBet;
    }
    
    /*
     * Adds a player to the game table if there is space.
     * 
     * Parameters:
     * - player: The player to add to the table.
     * 
     * Returns:
     * - true if the player was added successfully, false if the table is full.
     */
    public boolean addPlayer(Player player){
        if(numPlayers < maxTablePopulation){
            //add player to table
            currentTable.add(player);
            numPlayers++;
            return true;
        }
        return false;
    }

    /*
     * Removes a player from the game table.
     * 
     * Parameters:
     * - player: The player to remove from the table.
     * 
     * Returns:
     * - true if the player was successfully removed, false if the player is not found at the table.
     */
    public boolean removePlayer(Player player){
        for(int i = 0; i < numPlayers; i++){
            if(currentTable.get(i) == player){
                //remove player from table
                currentTable.remove(i);
                numPlayers--;
                return true;
            }
        }
        return false;
    }

    /* Determines the Action with the highest standard odds.
     * 
     * >return The Action instance with the highest standard odds.
     */
    public Action mostLikelyWin(){
        Action bestOccurence = actions[0];
        for(int i = 0; i < numActions; i++){
            if(bestOccurence.compareToStandardOdds(actions[i]) < 0){
                bestOccurence = actions[i];
            }
        }
        return bestOccurence;
    }

    /* Determines the Action with the highest payout ratio.
     * 
     * >return The Action instance with the highest payout ratio.
     */
    public Action highestPayoutRatio(){
        Action highestPayout = actions[0];
        for(int i = 0; i < numActions; i++){
            if(highestPayout.compareToPayout(actions[i]) < 0){
                highestPayout = actions[i];
            }
        }
        return highestPayout;
    }

    /*
     * Sorts the available actions by their standard odds in descending order.
     * 
     * Returns:
     * - A sorted array of actions, with the action with the highest odds appearing first.
     */
    public Action[] sortByOdds () {
        
        Action[] actionsCopy = actions.clone();
        
        int blank;
        Action toSwitch;
        for(int ub = 1; ub < numActions; ub++){
            blank = ub;
            toSwitch = actionsCopy[ub];
            while(blank > 0 && toSwitch.compareToStandardOdds(actionsCopy[blank-1]) > 0){
                actionsCopy[blank] = actionsCopy[blank-1];
                blank--;
            }
            actionsCopy[blank] = toSwitch;
        }

        return actionsCopy;
    }

    /*
     * Sorts the available actions by their payout ratio in descending order.
     * 
     * Returns:
     * - A sorted array of actions, with the action with the highest payout ratio appearing first.
     */
    public Action[] sortByPayout () {
        Action[] actionsCopy = actions.clone();
        
        int blank;
        Action toSwitch;
        for(int ub = 1; ub < numActions; ub++){
            blank = ub;
            toSwitch = actionsCopy[ub];
            //insertion sort
            while(blank > 0 && toSwitch.compareToPayout(actionsCopy[blank-1]) > 0){
                actionsCopy[blank] = actionsCopy[blank-1];
                blank--;
            }
            actionsCopy[blank] = toSwitch;
        }

        return actionsCopy;
    }

    /*
     * Collects a bet from the player and adds it to the game's revenue.
     * The player's dollar balance is reduced by the bet amount.
     * 
     * Parameters:
     * - p: The player placing the bet.
     * - amnt: The dollar amount to bet.
     * 
     * Returns:
     * - true if the bet was successfully placed and collected.
     * - false if the player does not have enough money to place the bet.
     */
    public boolean collectBet(Player p, int amnt){
        //check if player can afford bet
        if(amnt <= 0 || amnt > p.getBalance()){
            return false;
        }
        //adjust stats
        p.placeBet(amnt);
        if(p.getCurrentSession() != null){
            p.getCurrentSession().recordWager(amnt);
        }
        addRevenue(amnt);
        return true;
    }

    /*
     * Distributes winnings to the player and adjusts the game's profit accordingly.
     * The player's dollar balance is increased by the winnings amount.
     * 
     * Parameters:
     * - p: The player receiving the winnings.
     * - amnt: The amount of winnings to distribute.
     */

    public void distributeWinnings(Player p, int amnt){
        p.addWinnings(amnt);
        substractLoss(amnt);
    }

    /**
     * Returns the original wager after a push. Unlike a payout, this does not
     * record a player win.
     */
    public void returnBet(Player p, int amnt){
        p.refundBet(amnt);
        substractLoss(amnt);
    }

    /*
     * Simulates a round of the game. This method will be implemented by each specific game
     */
    public abstract void simulateRound ();

}
