/**
 * Interchangeable decisions and game preferences for a Player. Bankroll
 * subclasses still determine the scale of the player's wagers.
 */
public interface BehaviorStyle {
    BehaviorType getType();
    String getDisplayName();
    double preferenceFor(Game game);
    double getPokerRakeCutoff();
    PokerTraits getDefaultPokerTraits();

    /**
     * Poker preference falls quadratically to zero at this style's rake cutoff.
     * Other games are unaffected.
     */
    default double gameChoiceMultiplier(Game game){
        if(!(game instanceof Poker)){
            return 1;
        }
        double rakeRatio = ((Poker)game).getRakeRate() / getPokerRakeCutoff();
        return clamp(1 - rakeRatio * rakeRatio, 0, 1);
    }

    default double betMultiplier(DecisionContext context){
        return Math.max(0.50, 1
                + 0.80 * context.getMood()
                + 1.20 * context.getMomentum()
                + 0.80 * context.getSocialInfluence()
                + 2.50 * context.getTilt());
    }

    default PlayerDecision decide(DecisionContext context){
        DecisionThresholds thresholds = context.getPlayer().getProfile()
                .getDecisionThresholds();
        if(context.getSession() != null
                && context.getPlayer().getBalance()
                < context.getSession().getGame().getMinBet()){
            return PlayerDecision.LEAVE_CASINO;
        }

        // Every stereotype eventually tires. Before that hard ceiling, the
        // round count steadily raises exit pressure instead of forcing a fixed stay.
        if(context.getVisit().getTicksInCasino()
                >= thresholds.getPreferredVisitTicks() * 3
                || casinoExitPressure(context, thresholds)
                >= thresholds.getCasinoExitThreshold()){
            return PlayerDecision.LEAVE_CASINO;
        }
        if(switchPressure(context) >= thresholds.getSwitchThreshold()){
            return PlayerDecision.SWITCH_TABLE;
        }
        return PlayerDecision.STAY_AT_TABLE;
    }

    default double switchPressure(DecisionContext context){
        return clamp(-0.50 * context.getMood()
                - 0.45 * context.getMomentum()
                - 0.45 * context.getSocialInfluence(), 0, 1);
    }

    default double casinoExitPressure(DecisionContext context,
                                      DecisionThresholds thresholds){
        return clamp(-1.20 * context.getMood()
                + 0.10 * context.getVisitLossPressure(thresholds)
                + 1.00 * context.getVisitLengthPressure(thresholds)
                + 0.05 * context.getSwitchHistoryPressure()
                - 1.00 * context.getTilt()
                - 0.45 * context.getMomentum(), 0, 1);
    }

    default BlackjackStrategy getBlackjackStrategy(){
        return BlackjackStrategy.HIT_TO_15;
    }

    /** Neutral unless a behavior explicitly knows how to exploit the count. */
    default double blackjackCountBetMultiplier(Blackjack game){
        return 1;
    }

    default double affinityFor(BehaviorType other){
        return other == getType() ? 0.40 : 0;
    }

    default int chooseCraps(Craps game){
        return Craps.PASS_LINE;
    }

    default int chooseRoulette(Roulette game){
        double[] weights = getRouletteBetWeights();
        double total = 0;
        for(double weight : weights){
            total += Math.max(0, weight);
        }
        if(total <= 0){
            return game.mostLikelyWin().getId();
        }

        double selection = java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble(total);
        for(int action = 0; action < weights.length; action++){
            selection -= Math.max(0, weights[action]);
            if(selection < 0){
                return action;
            }
        }
        return weights.length - 1;
    }

    default double[] getRouletteBetWeights(){
        return new double[]{1, 0, 0, 0, 0, 0, 0};
    }

    static double clamp(double value, double minimum, double maximum){
        return Math.max(minimum, Math.min(maximum, value));
    }
}
