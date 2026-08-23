/**
 * Mutable emotional state for one player across games and table sessions.
 * A PlayerProfile owns this object, while stable profile traits determine how
 * strongly each completed round changes it.
 */
public class PlayerEmotionalStatus {
    private static final double SOCIAL_RESPONSE_RATE = 0.30;

    private double mood;
    private double momentum;
    private double socialScore;
    private double tilt;

    public double getMood(){ return mood; }
    public double getMomentum(){ return momentum; }
    public double getSocialScore(){ return socialScore; }
    public double getTilt(){ return tilt; }

    public void setMood(double mood){
        if(Double.isNaN(mood)){
            throw new IllegalArgumentException("Mood must be a number");
        }
        this.mood = clamp(mood, -1, 1);
    }

    /**
     * Updates the player's live emotional signals for one completed round.
     * PlayerFactualStatus extends this method with objective round history.
     */
    public void update(Player player, PlaySessionRecord session,
                       TableEnvironment environment){
        if(player == null || session == null || environment == null){
            throw new IllegalArgumentException(
                    "Post-game status updates require player, session, and environment");
        }

        double socialSurrounding = environment.getSocialScore();
        socialScore = clamp((1 - SOCIAL_RESPONSE_RATE) * socialScore
                + SOCIAL_RESPONSE_RATE * socialSurrounding, -1, 1);
        double bankrollImpact = player.getFactualStatus()
                .calculateBankrollPosition(player.getBalance());

        if(session.getLastRoundWagered() > 0){
            PlayerFactualStatus factualStatus = player.getFactualStatus();
            double streakImpact = clamp(
                    factualStatus.getWinStreak()
                            * factualStatus.getLatestWinFraction()
                            - factualStatus.getLossStreak()
                            * factualStatus.getLatestLossFraction(), -1, 1);
            double momentumSignal = 0.60 * bankrollImpact
                    + 0.40 * streakImpact;
            momentum = clamp(0.75 * momentum + 0.25 * momentumSignal, -1, 1);
        }
        else{
            momentum *= 0.90;
        }

        PlayerProfile profile = player.getProfile();
        double financialSensitivity = bankrollImpact >= 0
                ? profile.getWinSensitivity()
                : profile.getLossSensitivity();
        setMood(mood * 0.88
                + 0.18 * bankrollImpact * financialSensitivity
                + 0.08 * socialScore * profile.getSocialSensitivity());
        updateTilt(player.getFactualStatus());
    }

    private void updateTilt(PlayerFactualStatus factualStatus){
        if(factualStatus.getLastRoundWagered() == 0){
            tilt = clamp(0.95 * tilt, 0, 1);
            return;
        }

        double currentLossPressure = clamp(
                factualStatus.getLossStreak()
                        * factualStatus.getLatestLossFraction(), 0, 1);
        double currentWinRelief = clamp(
                factualStatus.getWinStreak()
                        * factualStatus.getLatestWinFraction(), 0, 1);
        tilt = clamp(tilt + 0.20 * currentLossPressure
                - 0.20 * currentWinRelief, 0, 1);
    }

    protected static double clamp(double value, double minimum, double maximum){
        return Math.max(minimum, Math.min(maximum, value));
    }
}
