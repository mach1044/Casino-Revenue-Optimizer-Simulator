/** Stable limits controlling when a stereotype switches tables or leaves. */
public final class DecisionThresholds {
    private final double switchThreshold;
    private final double casinoExitThreshold;
    private final double stopLossFraction;
    private final int preferredVisitTicks;

    public DecisionThresholds(double switchThreshold, double casinoExitThreshold,
                              double stopLossFraction, int preferredVisitTicks){
        this.switchThreshold = probability(switchThreshold, "switch threshold");
        // Not clamped to a probability like switchThreshold: casinoExitPressure
        // (BehaviorStyle.casinoExitPressure) is itself clamped to [0, 1], so a
        // threshold above 1.0 is a legitimate way to say "never trigger this
        // exit path" rather than an error.
        this.casinoExitThreshold = nonNegative(casinoExitThreshold, "casino exit threshold");
        this.stopLossFraction = positive(stopLossFraction, "stop-loss fraction");
        if(preferredVisitTicks <= 0){
            throw new IllegalArgumentException("Preferred visit ticks must be positive");
        }
        this.preferredVisitTicks = preferredVisitTicks;
    }

    public double getSwitchThreshold(){ return switchThreshold; }
    public double getCasinoExitThreshold(){ return casinoExitThreshold; }
    public double getStopLossFraction(){ return stopLossFraction; }
    public int getPreferredVisitTicks(){ return preferredVisitTicks; }

    private static double probability(double value, String name){
        if(Double.isNaN(value) || value < 0 || value > 1){
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static double positive(double value, String name){
        if(Double.isNaN(value) || value <= 0){
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static double nonNegative(double value, String name){
        if(Double.isNaN(value) || value < 0){
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
