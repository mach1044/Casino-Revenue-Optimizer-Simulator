/** Creates stereotypes and owns population compatibility rules. */
public final class BehaviorStyleFactory {
    private BehaviorStyleFactory(){ }

    public static BehaviorStyle create(BehaviorType type){
        switch(type){
            case WHALE: return new WhaleStyle();
            case GRINDER: return new GrinderStyle();
            case LOW_STAKES: return new LowStakesStyle();
            case OBNOXIOUS: return new ObnoxiousStyle();
            default: throw new IllegalArgumentException("Unsupported behavior type: " + type);
        }
    }

    public static boolean isCompatible(BehaviorType behavior, BankrollStyle bankrollType){
        switch(behavior){
            case WHALE:
                return bankrollType == BankrollStyle.HIGH_ROLLER;
            case GRINDER:
                return bankrollType == BankrollStyle.HIGH_ROLLER
                        || bankrollType == BankrollStyle.EVEN_STEVEN;
            case LOW_STAKES:
                return bankrollType == BankrollStyle.LOW_ROLLER
                        || bankrollType == BankrollStyle.EVEN_STEVEN;
            case OBNOXIOUS:
                return true;
            default:
                return false;
        }
    }

    public static BehaviorStyle defaultForBankroll(BankrollStyle bankrollType){
        switch(bankrollType){
            case HIGH_ROLLER: return new WhaleStyle();
            case EVEN_STEVEN: return new GrinderStyle();
            case LOW_ROLLER: return new LowStakesStyle();
            default: throw new IllegalArgumentException("Unknown bankroll type: " + bankrollType);
        }
    }
}
