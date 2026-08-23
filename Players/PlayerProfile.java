/**
 * Player configuration and its associated emotional state. Stable traits
 * control behavior; PlayerEmotionalStatus is updated after every game round.
 */
public final class PlayerProfile {
    private final BankrollStyle bankrollStyle;
    private final BehaviorStyle behaviorStyle;
    private final double socialSensitivity;
    private final double winSensitivity;
    private final double lossSensitivity;
    private final DecisionThresholds decisionThresholds;
    private final PlayerFactualStatus emotionalStatus;

    public PlayerProfile(BankrollStyle bankrollStyle, BehaviorStyle behaviorStyle,
                         double socialSensitivity, double winSensitivity,
                         double lossSensitivity){
        this(bankrollStyle, behaviorStyle, socialSensitivity, winSensitivity,
                lossSensitivity, defaultThresholds(behaviorStyle),
                new PlayerFactualStatus());
    }

    public PlayerProfile(BankrollStyle bankrollStyle, BehaviorStyle behaviorStyle,
                         double socialSensitivity, double winSensitivity,
                         double lossSensitivity,
                         DecisionThresholds decisionThresholds){
        this(bankrollStyle, behaviorStyle, socialSensitivity, winSensitivity,
                lossSensitivity, decisionThresholds,
                new PlayerFactualStatus());
    }

    public PlayerProfile(BankrollStyle bankrollStyle, BehaviorStyle behaviorStyle,
                         double socialSensitivity, double winSensitivity,
                         double lossSensitivity,
                         DecisionThresholds decisionThresholds,
                         PlayerFactualStatus emotionalStatus){
        if(bankrollStyle == null || behaviorStyle == null){
            throw new IllegalArgumentException("Player profile styles cannot be null");
        }
        if(decisionThresholds == null){
            throw new IllegalArgumentException("Decision thresholds cannot be null");
        }
        if(emotionalStatus == null){
            throw new IllegalArgumentException("Player emotional status cannot be null");
        }
        if(!BehaviorStyleFactory.isCompatible(behaviorStyle.getType(), bankrollStyle)){
            throw new IllegalArgumentException(behaviorStyle.getDisplayName()
                    + " is not compatible with " + bankrollStyle.getDisplayName());
        }
        this.bankrollStyle = bankrollStyle;
        this.behaviorStyle = behaviorStyle;
        this.socialSensitivity = validateTrait(
                socialSensitivity, "social sensitivity");
        this.winSensitivity = validateTrait(winSensitivity, "win sensitivity");
        this.lossSensitivity = validateTrait(lossSensitivity, "loss sensitivity");
        this.decisionThresholds = decisionThresholds;
        this.emotionalStatus = emotionalStatus;
    }

    public static PlayerProfile defaultProfile(BankrollStyle bankrollStyle){
        return forStyles(bankrollStyle,
                BehaviorStyleFactory.defaultForBankroll(bankrollStyle));
    }

    public static PlayerProfile forStyles(BankrollStyle bankrollStyle,
                                          BehaviorStyle behaviorStyle){
        switch(behaviorStyle.getType()){
            case WHALE:
                return new PlayerProfile(bankrollStyle, behaviorStyle,
                        0.90, 0.80, 0.80);
            case GRINDER:
                return new PlayerProfile(bankrollStyle, behaviorStyle,
                        0.35, 0.25, 0.25);
            case LOW_STAKES:
                return new PlayerProfile(bankrollStyle, behaviorStyle,
                        0.75, 0.45, 0.45);
            case OBNOXIOUS:
                return new PlayerProfile(bankrollStyle, behaviorStyle,
                        0.65, 0.70, 0.70);
            default:
                throw new IllegalArgumentException("Unknown behavior profile");
        }
    }

    public BankrollStyle getBankrollStyle(){ return bankrollStyle; }
    public BehaviorStyle getBehaviorStyle(){ return behaviorStyle; }
    public double getSocialSensitivity(){ return socialSensitivity; }
    public double getWinSensitivity(){ return winSensitivity; }
    public double getLossSensitivity(){ return lossSensitivity; }
    public DecisionThresholds getDecisionThresholds(){ return decisionThresholds; }
    public PlayerEmotionalStatus getEmotionalStatus(){ return emotionalStatus; }
    public PlayerFactualStatus getFactualStatus(){ return emotionalStatus; }

    public PlayerProfile withBehaviorStyle(BehaviorStyle behaviorStyle){
        return new PlayerProfile(bankrollStyle, behaviorStyle, socialSensitivity,
                winSensitivity, lossSensitivity, defaultThresholds(behaviorStyle),
                emotionalStatus);
    }

    private static DecisionThresholds defaultThresholds(BehaviorStyle behaviorStyle){
        if(behaviorStyle == null){
            throw new IllegalArgumentException("Behavior style cannot be null");
        }
        switch(behaviorStyle.getType()){
            case WHALE:
                return new DecisionThresholds(0.80, 1.5, 0.80, 120);
            case GRINDER:
                return new DecisionThresholds(0.55, 1.5, 0.35, 80);
            case LOW_STAKES:
                return new DecisionThresholds(0.40, 1.5, 0.25, 40);
            case OBNOXIOUS:
                return new DecisionThresholds(0.45, 1.5, 0.60, 70);
            default:
                throw new IllegalArgumentException("Unknown behavior profile");
        }
    }

    private static double validateTrait(double value, String name){
        if(Double.isNaN(value) || value < 0 || value > 1){
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}
