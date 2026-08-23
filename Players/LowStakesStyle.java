/** Recreational, tight, small-pot behavior focused on inexpensive fun. */
public final class LowStakesStyle implements BehaviorStyle {
    private static final PokerTraits POKER = new PokerTraits(0.25, 0.15, 0.40);

    public BehaviorType getType(){ return BehaviorType.LOW_STAKES; }
    public String getDisplayName(){ return "Low-Stakes"; }
    public double getPokerRakeCutoff(){ return 0.25; }
    public PokerTraits getDefaultPokerTraits(){ return POKER; }
    public double[] getRouletteBetWeights(){
        return new double[]{45, 10, 6, 5, 4, 5, 25};
    }
    public double affinityFor(BehaviorType other){
        switch(other){
            case WHALE: return 0.25;
            case GRINDER: return -0.10;
            case LOW_STAKES: return 0.20;
            case OBNOXIOUS: return -0.45;
            default: return 0;
        }
    }

    public double preferenceFor(Game game){
        if(game instanceof Blackjack) return 3.0;
        if(game instanceof Roulette) return 3.0;
        if(game instanceof Slots) return 3.0;
        if(game instanceof Craps) return 2.0;
        if(game instanceof Poker) return 2.0;
        return 0;
    }

}
