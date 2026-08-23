/** All mutable information used for a post-round player decision. */
public final class DecisionContext {
    private final Player player;
    private final PlaySessionRecord session;
    private final CasinoVisitState visit;
    private final PlayerEmotionalStatus emotionalStatus;
    private final PlayerFactualStatus factualStatus;
    private final TableEnvironment environment;

    private DecisionContext(Player player, PlaySessionRecord session,
                            CasinoVisitState visit, TableEnvironment environment){
        this.player = player;
        this.session = session;
        this.visit = visit;
        this.emotionalStatus = player.getEmotionalStatus();
        this.factualStatus = player.getFactualStatus();
        this.environment = environment;
    }

    public static DecisionContext forPlayer(Player player){
        PlaySessionRecord session = player.getCurrentSession();
        Game game = session == null ? null : session.getGame();
        return new DecisionContext(player, session, player.getVisitState(),
                TableEnvironment.evaluate(player, game));
    }

    public Player getPlayer(){ return player; }
    public PlaySessionRecord getSession(){ return session; }
    public CasinoVisitState getVisit(){ return visit; }
    public PlayerEmotionalStatus getEmotionalStatus(){ return emotionalStatus; }
    public PlayerFactualStatus getFactualStatus(){ return factualStatus; }
    public TableEnvironment getEnvironment(){ return environment; }
    public double getMood(){ return emotionalStatus.getMood(); }
    public double getMomentum(){ return emotionalStatus.getMomentum(); }
    public double getSocialScore(){ return emotionalStatus.getSocialScore(); }
    public double getSocialInfluence(){
        return getSocialScore() * player.getProfile().getSocialSensitivity();
    }
    public double getTilt(){ return emotionalStatus.getTilt(); }

    public double getVisitLossPressure(DecisionThresholds thresholds){
        double loss = Math.max(0, -visit.getReturnFraction(player.getBalance()));
        return clamp01(loss / thresholds.getStopLossFraction());
    }

    public double getVisitLengthPressure(DecisionThresholds thresholds){
        // Reaches full pressure at the preferred visit length and continues
        // rising afterward, so an overtime stay needs a strong counterweight.
        return clamp(visit.getTicksInCasino()
                / (double)thresholds.getPreferredVisitTicks(), 0, 2);
    }

    public double getSwitchHistoryPressure(){
        return clamp01(visit.getTableSwitches() / 3.0);
    }

    private static double clamp01(double value){
        return clamp(value, 0, 1);
    }

    private static double clamp(double value, double minimum, double maximum){
        return Math.max(minimum, Math.min(maximum, value));
    }
}
