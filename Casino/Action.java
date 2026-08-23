/*                                                                           *
 * Action.java                                                               *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-10                                                                *
 * ------------------------------------------------------------------------- *
 * A class which represents an action which can be taken by a player during  *
 * one of the casino games                                                   */

 public class Action {

    private int id;
    private String name;
    private double standardOdds; //Odds of occurence visible to a player
    private double actualOdds;   //Odds for calculation - can be modified by staff
    private double payoutRatio;

    // Getter for id
    public int getId() {return id;}

    // Getter for name
    public String getName() {return name;}

    // Getter  for standardOdds
    public double getStandardOdds() {return standardOdds;}
    public void setStandardOdds(double standardOdds) {this.standardOdds = standardOdds;}

    // Getter and Setter for actualOdds
    public double getActualOdds() {return actualOdds;}
    public void setActualOdds(double actualOdds) {this.actualOdds = actualOdds;}

    // Getter for payoutRatio
    public double getPayoutRatio() {return payoutRatio;}

    //Default constructor
    public Action(int id, String name, double odds, double payout){
        this.id = id;
        this.name = name;
        this.standardOdds = odds;
        this.actualOdds = odds;
        this.payoutRatio = payout;
    }

    /* Compares the standard odds of this Action with another.
     * 
     * >param other The other Action instance to compare against.
     * >return The difference between this Action's standardOdds and the other.
     */
    public double compareToStandardOdds(Action other){
        return standardOdds - other.standardOdds;
    }

    /* Compares the payout of this Action with another.
     * 
     * >param other The other Action to compare against.
     * >return The difference between this Action's payoutRatio and the other.
     */
    public double compareToPayout(Action other){
        return payoutRatio - other.payoutRatio;
    }

}
