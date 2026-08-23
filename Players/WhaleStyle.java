import java.util.concurrent.ThreadLocalRandom;

/** Attention-seeking, large-pot behavior with ordinary underlying skill. */
public final class WhaleStyle implements BehaviorStyle {
    private static final PokerTraits POKER = new PokerTraits(0.85, 0.95, 0.50);

    public BehaviorType getType(){ return BehaviorType.WHALE; }
    public String getDisplayName(){ return "Whale"; }
    public double getPokerRakeCutoff(){ return 0.20; }
    public PokerTraits getDefaultPokerTraits(){ return POKER; }
    public double affinityFor(BehaviorType other){
        switch(other){
            case WHALE: return 0.40;
            case GRINDER: return 0.10;
            case LOW_STAKES: return 0.05;
            case OBNOXIOUS: return 0.10;
            default: return 0;
        }
    }

    public double preferenceFor(Game game){
        if(game instanceof Craps) return 6.0;
        if(game instanceof Poker) return 5.0;
        if(game instanceof Roulette) return 3.0;
        if(game instanceof Blackjack) return 2.0;
        if(game instanceof Slots) return 1.0;
        return 0;
    }

    public int chooseCraps(Craps game){
        int roll = ThreadLocalRandom.current().nextInt(100);
        if(roll < 25) return Craps.PASS_LINE;
        if(roll < 45) return Craps.FIELD;
        if(roll < 65) return Craps.ANY_CRAPS;
        if(roll < 82) return Craps.ANY_SEVEN;
        return Craps.YO_ELEVEN;
    }

    public double[] getRouletteBetWeights(){
        return new double[]{25, 7, 7, 8, 8, 10, 35};
    }

}
