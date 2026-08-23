/*                                                                           *
 * Casino.java                                                               *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-10                                                                *
 * ------------------------------------------------------------------------- *
 * Class containing all player info and possible actions                     *
 *                                                                           */


import java.util.*;

public class Player {
    
    private int id;
    private String name;

    /** The player's single bankroll, measured in whole dollars. */
    private int balance;

    private int numGamesPlayed;
    private ArrayList<PlaySessionRecord> playHistory;


    private PlaySessionRecord currentSession = null;

    private int wins;
    private int profit;

    private int yearStarted;
    private boolean blacklisted;
    private PlayerProfile profile;
    private CasinoVisitState visitState;

    // Independent heuristic-poker traits grouped into one player-owned value.
    private PokerTraits pokerTraits = new PokerTraits(0.5, 0.5, 0.5);
    
    /*
     * Constructs a new Player with the specified name and dollar balance.
     *
     * Parameters:
     * - name: The player's name.
     * - balance: The player's initial bankroll in dollars.
     */
    public Player(int id, String name, int balance){
        this(id, name, balance,
                PlayerProfile.defaultProfile(BankrollStyle.LOW_ROLLER));
    }

    public Player(int id, String name, int balance,
                  PlayerProfile profile){
        this.id = id;
        this.name = name;
        this.balance = balance;
        numGamesPlayed = 0;
        wins = 0;
        profit = 0;
        yearStarted = Casino.CURR_YEAR;
        blacklisted = false;
        playHistory = new ArrayList<PlaySessionRecord>();
        setProfile(profile);
        getFactualStatus().initializeBankroll(balance);
        visitState = new CasinoVisitState(balance);
    }


    /*
     * Constructs a previously existing Player with the specified bankroll and other attributes.
     *
     * Parameters:
     * - name: The player's name.
     * - balance: The player's bankroll in dollars.
     * - numGamesPlayed: The number of games the player has played.
     * - wins: The number of games the player has won.
     * - profit: The player's total profit.
     * - yearStarted: The year the player started playing in the casino.
     * - mood: The player's mood rating.
     * - blacklist: Whether the player is blacklisted.
     */
    public Player(int id, String name, int balance, int profit,
                  int yearStarted, double mood, boolean blacklist){
        this(id, name, balance, profit, yearStarted, mood, blacklist,
                PlayerProfile.defaultProfile(BankrollStyle.LOW_ROLLER));
    }

    public Player(int id, String name, int balance, int profit,
                  int yearStarted, double mood, boolean blacklist,
                  PlayerProfile profile){
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.numGamesPlayed = 0;
        this.wins = 0;
        this.profit = profit;
        this.yearStarted = yearStarted;
        this.blacklisted = blacklist;
        playHistory = new ArrayList<PlaySessionRecord>();
        setProfile(profile);
        getFactualStatus().initializeBankroll(balance);
        setMood(mood);
        visitState = new CasinoVisitState(balance);
    }

    //Getter and setter for id
    public int getId(){
        return id;
    }

    public void setId(int id){
        this.id = id;
    }  

    //getters setters for name
    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    // Getter and Setter for balance
    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        if(balance < 0){
            throw new IllegalArgumentException("Balance cannot be negative");
        }
        this.balance = balance;
    }

    // Getter and Setter for numGamesPlayed
    public int getNumGamesPlayed() {
        return numGamesPlayed;
    }

    public void setNumGamesPlayed(int numGamesPlayed) {
        this.numGamesPlayed = numGamesPlayed;
    }

    //Getter and setter for numGamesPlayed
    public ArrayList<PlaySessionRecord> getPlayHistory(){
        return playHistory;
    }
    
    public void addRecord(PlaySessionRecord play){
        playHistory.add(play);
        numGamesPlayed++;
    }

    public void setPlayHistory(ArrayList<PlaySessionRecord> playHistory){
        this.playHistory = playHistory;
    }

    //Getter and setter for current session
    public PlaySessionRecord getCurrentSession(){
        return currentSession;
    }
    public void setCurrentSession(PlaySessionRecord currentSession){
        this.currentSession = currentSession;
    }

    // Getter and Setter for wins
    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    // Getter and Setter for profit
    public int getProfit() {
        return profit;
    }

    public void setProfit(int profit) {
        this.profit = profit;
    }

    // Getter and Setter for timePlayed
    public int getYearStarted() {
        return yearStarted;
    }

    public void setYearStarted(int yearStarted) {
        this.yearStarted = yearStarted;
    }

    // Getter and Setter for mood
    public double getMood() {
        return profile.getEmotionalStatus().getMood();
    }

    public void setMood(double mood) {
        profile.getEmotionalStatus().setMood(mood);
    }

    // Getter and Setter for blacklisted
    public boolean isBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(boolean blacklisted) {
        this.blacklisted = blacklisted;
    }

    public BehaviorStyle getBehaviorStyle(){
        return profile.getBehaviorStyle();
    }

    public BankrollStyle getBankrollStyle(){
        return profile.getBankrollStyle();
    }

    public PlayerProfile getProfile(){
        return profile;
    }

    public PlayerEmotionalStatus getEmotionalStatus(){
        return profile.getEmotionalStatus();
    }

    public PlayerFactualStatus getFactualStatus(){
        return profile.getFactualStatus();
    }

    public CasinoVisitState getVisitState(){ return visitState; }
    public boolean isInCasino(){ return visitState.isInCasino(); }
    public int getRoundsPlayed(){
        return getFactualStatus().getRoundsPlayed();
    }
    public int getRoundsAtTable(){
        return getFactualStatus().getRoundsAtTable();
    }
    public int getRoundsInCasino(){
        return visitState.getTicksInCasino();
    }

    public double getTiltLevel(){
        return getEmotionalStatus().getTilt();
    }

    public void setProfile(PlayerProfile profile){
        if(profile == null){
            throw new IllegalArgumentException("Player profile cannot be null");
        }
        PlayerFactualStatus previousStatus = this.profile == null ? null
                : this.profile.getFactualStatus();
        this.profile = profile;
        if(visitState != null && previousStatus != profile.getFactualStatus()){
            profile.getFactualStatus().initializeBankroll(balance);
        }
        pokerTraits = profile.getBehaviorStyle().getDefaultPokerTraits();
    }

    public void setBehaviorStyle(BehaviorStyle behaviorStyle){
        setProfile(profile.withBehaviorStyle(behaviorStyle));
    }

    public double getGamePreference(Game game){
        return profile.getBehaviorStyle().preferenceFor(game);
    }

    public double getGameChoiceWeight(Game game){
        return getGamePreference(game)
                * profile.getBehaviorStyle().gameChoiceMultiplier(game);
    }

    public PokerTraits getPokerTraits(){
        return pokerTraits;
    }

    public double getPokerParticipation(){
        return pokerTraits.getParticipation();
    }

    public void setPokerParticipation(double pokerParticipation){
        pokerTraits = pokerTraits.withParticipation(pokerParticipation);
    }

    public double getPokerAggression(){
        return pokerTraits.getAggression();
    }

    public void setPokerAggression(double pokerAggression){
        pokerTraits = pokerTraits.withAggression(pokerAggression);
    }

    public double getPokerSkill(){
        return pokerTraits.getSkill();
    }

    public double getEffectivePokerSkill(){
        return pokerTraits.getSkill() * (1 - 0.50 * getTiltLevel());
    }

    public void setPokerSkill(double pokerSkill){
        pokerTraits = pokerTraits.withSkill(pokerSkill);
    }

    //toString method
    public String toString(){
        return "Name: " + name + " Balance: $" + balance + "\n" +
               "Number of Games Played: " + numGamesPlayed + " Wins: " + wins + " Year Started: " + yearStarted + "\n" +
               "Mood: " + getMood() + " Momentum: " + getEmotionalStatus().getMomentum()
                        + " Rounds Played: " + getRoundsPlayed()
                        + " Rounds At Table: " + getRoundsAtTable()
                        + " Visit Ticks: " + getRoundsInCasino() + "\n" +
               "Win Streak: " + getFactualStatus().getWinStreak()
                        + " Loss Streak: " + getFactualStatus().getLossStreak()
                        + " Blacklisted: " + blacklisted + "\n" +
               "Bankroll: " + profile.getBankrollStyle().getDisplayName()
                       + " Behavior: " + profile.getBehaviorStyle().getDisplayName() + "\n" +
               "Poker Participation: " + pokerTraits.getParticipation()
                       + " Aggression: " + pokerTraits.getAggression()
                       + " Skill: " + pokerTraits.getSkill();
    }


    /*
     * Places a dollar wager from the player's balance.
     *
     * Parameters:
     * - amount: The number of dollars to wager.
     *
     * Returns:
     * - true if the bet was placed successfully.
     * - false if the player doesn't have enough money.
     */
    public boolean placeBet(int amount){
        if(amount > 0 && balance >= amount){
            balance -= amount;
            //Modify current record
            if(currentSession != null){
                currentSession.addTotalWinnings(-amount);
                currentSession.addTableLosses(1);
            }
            return true;
        }
        else{
            return false;
        }
    }

    /*
     * Adds a dollar payout to the player's balance.
     *
     * Parameters:
     * - amount: The number of dollars to add.
     */
    public void addWinnings(int amount){
        balance += amount;
        //Modify current record
        if(currentSession != null){
            currentSession.addTotalWinnings(amount);
            currentSession.addTableWins(1);
            currentSession.addTableLosses(-1);
        }
    }

    /**
     * Returns a wager after a push without recording the round as a win.
     */
    public void refundBet(int amount){
        balance += amount;
        if(currentSession != null){
            currentSession.addTotalWinnings(amount);
            currentSession.addTableLosses(-1);
        }
    }

    /*
     * Allows the player to join a game table.
     *
     * Parameters:
     * - game: The game the player wants to join.
     *
     * Returns:
     * - true if the player successfully joined the game.
     * - false if the game is unable to accept the player.
     */
    public PlaySessionRecord joinTable(Game game){
        if(game.addPlayer(this)){
            currentSession = new PlaySessionRecord(this, game, 0, 0, 0);
            addRecord(currentSession);
            game.addRecord(currentSession);
            return currentSession;
        }
        return null;
    }


    /*
     * Allows the player to leave the current game table.
     *
     * Returns:
     * - true if the player successfully left the game.
     * - false if the player is not currently in a game.
     */
    public boolean leaveTable(){
        if(currentSession == null){
            return false;
        }
        else{
            currentSession.close();
            boolean success = currentSession.getGame().removePlayer(this);
            currentSession = null;
            return success;
        }
    }

    public boolean switchTable(){
        if(currentSession == null){
            return false;
        }
        Game oldTable = currentSession.getGame();
        if(leaveTable()){
            visitState.recordTableSwitch(oldTable);
            return true;
        }
        return false;
    }

    public void leaveCasino(){
        leaveTable();
        visitState.leaveCasino();
    }

    public void removeFromCasinoByPolicy(){
        leaveTable();
        visitState.removeByCasino();
    }

    /** Applies all post-game emotional updates in one place. */
    public void updateEmotionalStatusAfterRound(TableEnvironment environment){
        if(currentSession == null){
            return;
        }
        profile.getEmotionalStatus().update(this, currentSession, environment);
    }

    /** Compatibility alias for callers using the former mood-only update. */
    public void updateMoodAfterRound(TableEnvironment environment){
        updateEmotionalStatusAfterRound(environment);
    }

    /**
     * Chooses one of the wager identifiers exposed by a Craps table.
     */
    public int chooseCraps(Craps game){
        return profile.getBehaviorStyle().chooseCraps(game);
    }

    /*
     * Abstract method for choosing an action in a Blackjack game.
     */    
    public int chooseRoulette(Roulette game){
        return profile.getBehaviorStyle().chooseRoulette(game);
    }

    /*
     * Abstract method for calculating how much user wants to bet in a game.
     */
    public int calculateBetAmount(int min, int max){
        int base = profile.getBankrollStyle().calculateBaseBet(this, min, max);
        if(currentSession == null || balance < min){
            return base;
        }
        double multiplier = profile.getBehaviorStyle()
                .betMultiplier(DecisionContext.forPlayer(this));
        int adjusted = (int)Math.round(base * multiplier);
        return Math.max(min, Math.min(Math.min(max, balance), adjusted));
    }

    public int calculateBlackjackBet(Blackjack game){
        int bet = calculateBetAmount(game.getMinBet(), game.getMaxBet());
        double countMultiplier = profile.getBehaviorStyle()
                .blackjackCountBetMultiplier(game);
        int adjusted = (int)Math.round(bet * countMultiplier);
        return Math.max(game.getMinBet(), Math.min(
                Math.min(game.getMaxBet(), balance), adjusted));
    }

}
