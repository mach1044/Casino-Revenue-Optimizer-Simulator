/*                                                                           *
 * PlaySessionRecord.java                                                    *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-10                                                                *
 * ------------------------------------------------------------------------- *
 * Represents a record of a player's session in a specific game, tracking    *
 * their total winnings, table wins, and table losses, and providing methods *
 * for statistical analysis                                                  */
public class PlaySessionRecord {
    
    private Player player;
    private Game game;
    private int totalWinnings;
    private int tableWins;
    private int tableLosses;
    private long totalWagered;
    private long evaluatedWagered;
    private int evaluatedWinnings;
    private int roundsPlayed;
    private int roundsAtTable;
    private int winStreak;
    private int lossStreak;
    private int lastRoundNet;
    private long lastRoundWagered;
    private double momentum;
    private final int startingBalance;
    private int endingBalance;

    // Constructor to initialize a new PlaySessionRecord with player, game, and statistics
    public PlaySessionRecord(Player player, Game game, int totalWinnings, int tableWins, int tableLosses){
        this(player, game, totalWinnings, tableWins, tableLosses,
                0, 0, 0, 0, 0, 0, 0, 0,
                player.getBalance(), player.getBalance());
    }

    /** Rebuilds a completed session from a simulation save. */
    public PlaySessionRecord(Player player, Game game, int totalWinnings,
                             int tableWins, int tableLosses, long totalWagered,
                             int roundsPlayed, int roundsAtTable, int winStreak,
                             int lossStreak, int lastRoundNet,
                             long lastRoundWagered, double momentum,
                             int startingBalance, int endingBalance){
        this.player = player;
        this.game = game;
        this.totalWinnings = totalWinnings;
        this.tableWins = tableWins;
        this.tableLosses = tableLosses;
        this.totalWagered = totalWagered;
        this.evaluatedWagered = totalWagered;
        this.evaluatedWinnings = totalWinnings;
        this.roundsPlayed = roundsPlayed;
        this.roundsAtTable = roundsAtTable;
        this.winStreak = winStreak;
        this.lossStreak = lossStreak;
        this.lastRoundNet = lastRoundNet;
        this.lastRoundWagered = lastRoundWagered;
        this.momentum = momentum;
        this.startingBalance = startingBalance;
        this.endingBalance = endingBalance;
    } 

    // Getter for player
    public Player getPlayer() {
        return player;
    }

    // Setter for player
    public void setPlayer(Player player) {
        this.player = player;
    }

    // Getter for game
    public Game getGame() {
        return game;
    }

    // Setter for game
    public void setGame(Game game) {
        this.game = game;
    }

    // Getter for totalWinnings
    public int getTotalWinnings() {
        return totalWinnings;
    }

    // Setter for totalWinnings
    public void setTotalWinnings(int totalWinnings) {
        this.totalWinnings = totalWinnings;
    }
    public void addTotalWinnings(int totalWinnings){
        this.totalWinnings += totalWinnings;
    }

    // Getter for tableWins
    public int getTableWins() {
        return tableWins;
    }
    public void addTableWins(int tableWins){
        this.tableWins += tableWins;
    }

    // Setter for tableWins
    public void setTableWins(int tableWins) {
        this.tableWins = tableWins;
    }

    // Getter for tableLosses
    public int getTableLosses() {
        return tableLosses;
    }

    // Setter for tableLosses
    public void setTableLosses(int tableLosses) {
        this.tableLosses = tableLosses;
    }

    /* method to add losses to the tableLosses
     * 
     * Parameters 
     * - tableLosses --> the amount to add
     */
    public void addTableLosses(int tableLosses){
        this.tableLosses += tableLosses;
    }

    public long getTotalWagered(){ return totalWagered; }
    public int getRoundsPlayed(){ return roundsPlayed; }
    public int getRoundsAtTable(){ return roundsAtTable; }
    public int getWinStreak(){ return winStreak; }
    public int getLossStreak(){ return lossStreak; }
    public int getLastRoundNet(){ return lastRoundNet; }
    public long getLastRoundWagered(){ return lastRoundWagered; }
    public double getMomentum(){ return momentum; }
    public int getStartingBalance(){ return startingBalance; }
    public int getEndingBalance(){ return endingBalance; }

    /** Captures the player's balance when this table session ends. */
    public void close(){
        endingBalance = player.getBalance();
    }

    public double getReturnOnWagers(){
        return totalWagered == 0 ? 0 : totalWinnings / (double)totalWagered;
    }

    /** Records money put at risk; pushes remain wagers even when refunded. */
    public void recordWager(int amount){
        if(amount > 0){
            totalWagered += amount;
        }
    }

    /**
     * Closes one simulation round and updates bounded recent performance.
     * This checkpoint design also supports games that make several bets in a
     * single round, such as slots or a split blackjack hand.
     */
    public void completeRound(){
        lastRoundNet = totalWinnings - evaluatedWinnings;
        lastRoundWagered = totalWagered - evaluatedWagered;
        evaluatedWinnings = totalWinnings;
        evaluatedWagered = totalWagered;
        endingBalance = player.getBalance();
        roundsAtTable++;

        if(lastRoundWagered > 0){
            roundsPlayed++;
            double result = Math.max(-1, Math.min(1,
                    lastRoundNet / (double)lastRoundWagered));
            momentum = 0.75 * momentum + 0.25 * result;
            if(lastRoundNet > 0){
                winStreak++;
                lossStreak = 0;
            }
            else if(lastRoundNet < 0){
                lossStreak++;
                winStreak = 0;
            }
            else{
                winStreak = 0;
                lossStreak = 0;
            }
        }
        else{
            momentum *= 0.90;
        }
    }

    /* Calculates the win rate based on the number of table wins and table losses.
    * The win rate is calculated as the ratio of tableWins to the total of tableWins + tableLosses.
    * 
    * Returns:
    * - double: The win rate
    */    
    public double getWinRate(){
        int settled = tableWins + tableLosses;
        return settled == 0 ? 0 : (double)tableWins / settled;
    }

    //prints a string representation of the object
    @Override
    public String toString() {
        return "Player: " + player.getName() + ", Game: " + game.getClass().getSimpleName() + "\nWinnings: " + totalWinnings
                + ", Wins: " + tableWins + ", Losses: " + tableLosses
                + ", Wagered: " + totalWagered
                + ", Rounds At Table: " + roundsAtTable
                + ", Rounds Played: " + roundsPlayed
                + ", Momentum: " + String.format("%.2f", momentum);
    }
}
