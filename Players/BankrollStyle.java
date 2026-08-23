/** Stable financial scale used to calculate a player's neutral base wager. */
public enum BankrollStyle {
    HIGH_ROLLER("HighRoller", 0.08),
    EVEN_STEVEN("EvenSteven", 0.04),
    LOW_ROLLER("LowRoller", 0.02);

    private final String displayName;
    private final double baseBetPercent;

    BankrollStyle(String displayName, double baseBetPercent){
        this.displayName = displayName;
        this.baseBetPercent = baseBetPercent;
    }

    public String getDisplayName(){ return displayName; }
    public double getBaseBetPercent(){ return baseBetPercent; }

    public int calculateBaseBet(Player player, int minimum, int maximum){
        if(minimum <= 0 || maximum < minimum){
            throw new IllegalArgumentException("Invalid table betting limits");
        }
        if(player.getBalance() < minimum){
            return player.getBalance();
        }
        int desired = (int)Math.ceil(player.getBalance() * baseBetPercent);
        return Math.max(minimum, Math.min(maximum, desired));
    }

    public static BankrollStyle fromMenuCode(int code){
        switch(code){
            case 0: return HIGH_ROLLER;
            case 1: return EVEN_STEVEN;
            case 2: return LOW_ROLLER;
            default: throw new IllegalArgumentException("Unknown bankroll code: " + code);
        }
    }
}
