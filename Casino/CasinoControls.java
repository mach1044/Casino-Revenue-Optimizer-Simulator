/** Casino-owned settings that can change table economics or remove players. */
public final class CasinoControls {
    private final double pokerRakeRate;
    private final int pokerRakeCap;
    private final double slotRtp;
    private final int playerProfitLimit;
    private final TableAssignmentPolicy tableAssignmentPolicy;

    public CasinoControls(double pokerRakeRate, int pokerRakeCap,
                          double slotRtp, int playerProfitLimit,
                          TableAssignmentPolicy tableAssignmentPolicy){
        if(Double.isNaN(pokerRakeRate) || pokerRakeRate < 0 || pokerRakeRate > 1){
            throw new IllegalArgumentException("Poker rake rate must be between 0 and 1");
        }
        if(pokerRakeCap < 0){
            throw new IllegalArgumentException("Poker rake cap cannot be negative");
        }
        if(Double.isNaN(slotRtp) || slotRtp < 0 || slotRtp > 1){
            throw new IllegalArgumentException("Slot RTP must be between 0 and 1");
        }
        if(playerProfitLimit < 0){
            throw new IllegalArgumentException("Player profit limit cannot be negative");
        }
        if(tableAssignmentPolicy == null){
            throw new IllegalArgumentException("Table assignment policy cannot be null");
        }
        this.pokerRakeRate = pokerRakeRate;
        this.pokerRakeCap = pokerRakeCap;
        this.slotRtp = slotRtp;
        this.playerProfitLimit = playerProfitLimit;
        this.tableAssignmentPolicy = tableAssignmentPolicy;
    }

    public static CasinoControls defaults(){
        return new CasinoControls(0.05, 100, 0.90, 0, TableAssignmentPolicy.PREFERENCE_WEIGHTED);
    }

    public double getPokerRakeRate(){ return pokerRakeRate; }
    public int getPokerRakeCap(){ return pokerRakeCap; }
    public double getSlotRtp(){ return slotRtp; }
    public int getPlayerProfitLimit(){ return playerProfitLimit; }
    public TableAssignmentPolicy getTableAssignmentPolicy(){ return tableAssignmentPolicy; }

    public boolean shouldRemoveForProfit(Player player){
        return playerProfitLimit > 0
                && player.getBalance() - player.getVisitState().getStartingBalance()
                >= playerProfitLimit;
    }
}
