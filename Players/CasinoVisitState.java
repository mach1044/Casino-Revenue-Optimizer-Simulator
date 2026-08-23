/** Mutable state covering one player's current visit to the casino. */
public final class CasinoVisitState {
    private final int startingBalance;
    private int ticksInCasino;
    private int tableSwitches;
    private boolean inCasino = true;
    private boolean removedByCasino;
    private Game lastLeftTable;

    public CasinoVisitState(int startingBalance){
        this.startingBalance = Math.max(0, startingBalance);
    }

    public int getStartingBalance(){ return startingBalance; }
    public int getTicksInCasino(){ return ticksInCasino; }
    public int getTableSwitches(){ return tableSwitches; }
    public boolean isInCasino(){ return inCasino; }
    public Game getLastLeftTable(){ return lastLeftTable; }
    public boolean wasRemovedByCasino(){ return removedByCasino; }

    public void recordTableSwitch(Game table){
        tableSwitches++;
        lastLeftTable = table;
    }

    public void clearLastLeftTable(){
        lastLeftTable = null;
    }

    public void leaveCasino(){
        inCasino = false;
    }

    public void removeByCasino(){
        removedByCasino = true;
        inCasino = false;
    }

    public void completeSimulationTick(){
        if(inCasino){
            ticksInCasino++;
        }
    }

    public double getReturnFraction(int currentBalance){
        if(startingBalance == 0){
            return 0;
        }
        return (currentBalance - startingBalance) / (double)startingBalance;
    }
}
