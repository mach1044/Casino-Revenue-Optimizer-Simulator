import java.util.concurrent.ThreadLocalRandom;

/** Disciplined poker/blackjack regular focused on repeatable long-run play. */
public final class GrinderStyle implements BehaviorStyle {
    private static final PokerTraits POKER = new PokerTraits(0.55, 0.50, 0.75);

    public BehaviorType getType(){ return BehaviorType.GRINDER; }
    public String getDisplayName(){ return "Grinder"; }
    public double getPokerRakeCutoff(){ return 0.10; }
    public PokerTraits getDefaultPokerTraits(){ return POKER; }
    public BlackjackStrategy getBlackjackStrategy(){
        return BlackjackStrategy.BASIC;
    }
    public double[] getRouletteBetWeights(){
        return new double[]{50, 10, 6, 5, 4, 5, 20};
    }
    public double affinityFor(BehaviorType other){
        switch(other){
            case WHALE: return 0.65;
            case GRINDER: return 0.05;
            case LOW_STAKES: return 0.05;
            case OBNOXIOUS: return -0.25;
            default: return 0;
        }
    }

    public double preferenceFor(Game game){
        if(game instanceof Poker) return 6.0;
        if(game instanceof Blackjack) return 5.0;
        if(game instanceof Craps) return 0.75;
        if(game instanceof Roulette) return 0.25;
        if(game instanceof Slots) return 0.10;
        return 0;
    }

    public int chooseCraps(Craps game){
        return ThreadLocalRandom.current().nextBoolean()
                ? Craps.PASS_LINE : Craps.DONT_PASS;
    }
}
