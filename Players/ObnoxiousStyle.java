import java.util.concurrent.ThreadLocalRandom;

/** Overconfident poker/blackjack behavior with tight play and weak skill. */
public final class ObnoxiousStyle implements BehaviorStyle {
    private static final PokerTraits POKER = new PokerTraits(0.20, 0.45, 0.30);

    public BehaviorType getType(){ return BehaviorType.OBNOXIOUS; }
    public String getDisplayName(){ return "Obnoxious"; }
    public double getPokerRakeCutoff(){ return 0.10; }
    public PokerTraits getDefaultPokerTraits(){ return POKER; }
    public BlackjackStrategy getBlackjackStrategy(){
        return BlackjackStrategy.BASIC;
    }
    public double[] getRouletteBetWeights(){
        return new double[]{30, 6, 6, 7, 7, 9, 35};
    }
    public double affinityFor(BehaviorType other){
        switch(other){
            case WHALE: return 0.20;
            case GRINDER: return -0.15;
            case LOW_STAKES: return 0.20;
            case OBNOXIOUS: return -0.25;
            default: return 0;
        }
    }

    public double preferenceFor(Game game){
        if(game instanceof Poker) return 5.0;
        if(game instanceof Blackjack) return 4.0;
        if(game instanceof Craps) return 1.5;
        if(game instanceof Roulette) return 1.0;
        if(game instanceof Slots) return 0.5;
        return 0;
    }

    public int chooseCraps(Craps game){
        return ThreadLocalRandom.current().nextBoolean()
                ? Craps.PASS_LINE : Craps.DONT_PASS;
    }
}
