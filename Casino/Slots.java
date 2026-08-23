/*                                                                           *
 * Slots.java                                                                *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-10                                                                *
 * ------------------------------------------------------------------------- *
 * Simulates a classic three-reel slot machine with three horizontal         *
 * paylines, weighted symbols, a paytable, and a rare jackpot.               *
 */

import java.util.concurrent.ThreadLocalRandom;

public class Slots extends Game {

    private static final int ROWS = 3;
    private static final int COLS = 3;
    private static final int JACKPOT_PRIZE = 1_000_000;

    // Reel weights total 1000. Common symbols appear more frequently.
    private static final char[] SYMBOLS = {'-', 'C', 'L', 'B', 'A', '7'};
    private static final int[] SYMBOL_WEIGHTS = {560, 205, 120, 70, 40, 5};
    private static final int TOTAL_SYMBOL_WEIGHT = 1000;

    private static final int TWO_CHERRIES = 0;
    private static final int THREE_CHERRIES = 1;
    private static final int THREE_LEMONS = 2;
    private static final int THREE_BELLS = 3;
    private static final int THREE_BARS = 4;
    private static final int JACKPOT = 5;

    private final char[][] screen = new char[ROWS][COLS];
    private final double naturalRtp;
    private double targetRtp;
    private double rtpScale;

    public Slots(int id, int bet){
        this(id, bet, naturalRtp(bet));
    }

    public Slots(int id, int bet, double targetRtp){
        super(id, 1, bet, bet);
        naturalRtp = naturalRtp(bet);
        setTargetRtp(targetRtp);
        initializePaytable();
    }

    // Constructor used when loading a previously saved simulation.
    public Slots(int id, int profit, int bet){
        this(id, profit, bet, naturalRtp(bet));
    }

    public Slots(int id, int profit, int bet, double targetRtp){
        super(id, profit, 1, bet, bet);
        naturalRtp = naturalRtp(bet);
        setTargetRtp(targetRtp);
        initializePaytable();
    }

    /** Theoretical return before whole-dollar payout rounding. */
    public static double naturalRtp(int bet){
        if(bet <= 0){
            throw new IllegalArgumentException("Slot bet must be positive");
        }
        double perPayline = 0.100229625 * 1.5
                + 0.008615125 * 5
                + 0.001728 * 25
                + 0.000343 * 75
                + 0.000064 * 400
                + 0.000000125 * (JACKPOT_PRIZE / (double)bet);
        return ROWS * perPayline;
    }

    private void initializePaytable(){
        numActions = 6;
        actions = new Action[numActions];

        // Odds are per payline. Payouts are multiples of the spin's bet.
        actions[TWO_CHERRIES] = new Action(TWO_CHERRIES, "Two Cherries", 0.100229625, 1.5);
        actions[THREE_CHERRIES] = new Action(THREE_CHERRIES, "Three Cherries", 0.008615125, 5);
        actions[THREE_LEMONS] = new Action(THREE_LEMONS, "Three Lemons", 0.001728, 25);
        actions[THREE_BELLS] = new Action(THREE_BELLS, "Three Bells", 0.000343, 75);
        actions[THREE_BARS] = new Action(THREE_BARS, "Three Bars", 0.000064, 400);
        actions[JACKPOT] = new Action(
            JACKPOT,
            "Three Sevens Jackpot",
            0.000000125,
            JACKPOT_PRIZE / (double)getMaxBet()
        );
    }

    public double getTargetRtp(){
        return targetRtp;
    }

    public double getNaturalRtp(){
        return naturalRtp;
    }

    public void setTargetRtp(double targetRtp){
        if(Double.isNaN(targetRtp) || targetRtp < 0 || targetRtp > 1){
            throw new IllegalArgumentException("Target RTP must be between 0 and 1");
        }
        this.targetRtp = targetRtp;
        rtpScale = targetRtp / naturalRtp;
    }

    public String toString(){
        return "Slots - " + super.toString();
    }

    /**
     * Collects the fixed spin price from a player.
     */
    public boolean collectBet(Player player){
        int betAmount = getMaxBet();
        return super.collectBet(player, betAmount);
    }

    /**
     * Runs one paid spin and awards the combined payout from all three rows.
     */
    public void spinMachine(Player player){
        if(!collectBet(player)){
            return;
        }

        populateScreen();

        double totalPayoutMultiplier = screenPayoutMultiplier();
        if(totalPayoutMultiplier > 0){
            int payout = (int)Math.round(getMaxBet() * totalPayoutMultiplier);
            distributeWinnings(player, payout);
        }
    }

    /** One slot spin is one game round and therefore one simulation tick. */
    @Override
    public void simulateRound(){
        if(currentTable.isEmpty()){
            return;
        }
        spinMachine(currentTable.get(0));
    }

    private void populateScreen(){
        for(int row = 0; row < ROWS; row++){
            for(int col = 0; col < COLS; col++){
                screen[row][col] = randomSymbol();
            }
        }
    }

    private char randomSymbol(){
        int roll = ThreadLocalRandom.current().nextInt(TOTAL_SYMBOL_WEIGHT);
        int cumulativeWeight = 0;

        for(int i = 0; i < SYMBOLS.length; i++){
            cumulativeWeight += SYMBOL_WEIGHTS[i];
            if(roll < cumulativeWeight){
                return SYMBOLS[i];
            }
        }

        // Unreachable while the weights sum to TOTAL_SYMBOL_WEIGHT.
        return SYMBOLS[0];
    }

    private double screenPayoutMultiplier(){
        double multiplier = 0;
        for(int row = 0; row < ROWS; row++){
            multiplier += payoutMultiplierForRow(row);
        }
        return multiplier;
    }

    private double payoutMultiplierForRow(int row){
        int cherries = 0;
        for(int col = 0; col < COLS; col++){
            if(screen[row][col] == 'C'){
                cherries++;
            }
        }

        if(cherries == 2){
            return acceptedPayout(TWO_CHERRIES);
        }

        char symbol = screen[row][0];
        if(symbol != screen[row][1] || symbol != screen[row][2]){
            return 0;
        }

        switch(symbol){
            case 'C':
                return acceptedPayout(THREE_CHERRIES);
            case 'L':
                return acceptedPayout(THREE_LEMONS);
            case 'B':
                return acceptedPayout(THREE_BELLS);
            case 'A':
                return acceptedPayout(THREE_BARS);
            case '7':
                return acceptedPayout(JACKPOT);
            default:
                return 0;
        }
    }

    private double acceptedPayout(int actionIndex){
        if(rtpScale <= 1){
            return ThreadLocalRandom.current().nextDouble() < rtpScale
                    ? actions[actionIndex].getPayoutRatio() : 0;
        }
        return actions[actionIndex].getPayoutRatio() * rtpScale;
    }
}
