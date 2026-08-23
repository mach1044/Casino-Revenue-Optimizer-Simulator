/**
 * Objective round history attached to a player's emotional status. These
 * values describe what happened; the inherited mood and momentum describe
 * how the player currently feels about it.
 */
public final class PlayerFactualStatus extends PlayerEmotionalStatus {
    private int roundsPlayed;
    private int roundsAtTable;
    private int winStreak;
    private int lossStreak;
    private int lastRoundNet;
    private long lastRoundWagered;
    private double socialSurrounding;
    private int startingBankroll;
    private int currentBankroll;

    public int getRoundsPlayed(){ return roundsPlayed; }
    public int getRoundsAtTable(){ return roundsAtTable; }
    public int getWinStreak(){ return winStreak; }
    public int getLossStreak(){ return lossStreak; }
    public int getLastRoundNet(){ return lastRoundNet; }
    public long getLastRoundWagered(){ return lastRoundWagered; }
    public double getSocialSurrounding(){ return socialSurrounding; }
    public int getStartingBankroll(){ return startingBankroll; }
    public int getCurrentBankroll(){ return currentBankroll; }

    void initializeBankroll(int bankroll){
        startingBankroll = Math.max(0, bankroll);
        currentBankroll = startingBankroll;
    }

    double calculateBankrollPosition(int bankroll){
        return startingBankroll == 0 ? 0
                : clamp((bankroll - startingBankroll)
                / (double)startingBankroll, -1, 1);
    }

    double getLatestLossFraction(){
        if(lastRoundNet >= 0){
            return 0;
        }
        long bankrollBeforeRound = getBankrollBeforeLatestRound();
        return bankrollBeforeRound <= 0 ? 0
                : clamp(-lastRoundNet / (double)bankrollBeforeRound, 0, 1);
    }

    double getLatestWinFraction(){
        if(lastRoundNet <= 0){
            return 0;
        }
        long bankrollBeforeRound = getBankrollBeforeLatestRound();
        return bankrollBeforeRound <= 0 ? 0
                : clamp(lastRoundNet / (double)bankrollBeforeRound, 0, 1);
    }

    private long getBankrollBeforeLatestRound(){
        return currentBankroll - (long)lastRoundNet;
    }

    @Override
    public void update(Player player, PlaySessionRecord session,
                       TableEnvironment environment){
        if(player == null || session == null || environment == null){
            throw new IllegalArgumentException(
                    "Post-game status updates require player, session, and environment");
        }

        roundsAtTable++;
        lastRoundNet = session.getLastRoundNet();
        lastRoundWagered = session.getLastRoundWagered();
        socialSurrounding = environment.getSocialScore();
        currentBankroll = Math.max(0, player.getBalance());

        if(lastRoundWagered > 0){
            roundsPlayed++;
        }

        if(lastRoundWagered == 0 || lastRoundNet == 0){
            winStreak = 0;
            lossStreak = 0;
        }
        else if(lastRoundNet > 0){
            winStreak++;
            lossStreak = 0;
        }
        else{
            lossStreak++;
            winStreak = 0;
        }

        // Emotional tilt depends on the newly updated factual loss streak and
        // loss size, so the parent update intentionally runs last.
        super.update(player, session, environment);
    }
}
