/*                                                                          *
* Security.java                                                             *
* ------------------------------------------------------------------------- *
 * Max Chen                                                                 *
* 2025-01-10                                                                *
* ------------------------------------------------------------------------- *
* Class which facilitates the functions of a security guard in the casino   *
*                                                                           */

public class Security extends Staff{
    private int playerKickCount;
    
    /*
     * Constructor for creating a Security object
     *
     * Parameters:
     * - name: The name of the security staff member.
     * - blacklistCount: The number of players the security staff member has blacklisted.
     * - playerKickCount: The number of players the security staff member has kicked.
     */
    public Security(String name, int blacklistCount, int playerKickCount){
        super(name, blacklistCount);
        this.playerKickCount = playerKickCount;
    }

    /*
     * Constructor for creating a Security object
     *
     * Parameters:
     * - name: The name of the security staff member.
     */
    public Security(String name){
        super(name);
        playerKickCount = 0;
    }

    //getter for playerKickCount
    public int getKickCount(){
        return playerKickCount;
    }

    /*
     * Kicks a player from the table, removing them from the current game or activity.
     *
     * Parameters:
     * - toKick: The Player object representing the player to be kicked.
     *
     * Returns:
     * - true if the player was successfully removed from the table (via leaveTable method).
     * - false if the player could not be kicked for any reason.
     */
    public boolean kickPlayer(Player toKick){
        boolean success = toKick.leaveTable();
        if(success){
            playerKickCount++;
            return true;
        }
        else{
            return false;
        }
    }

    /*
     * Returns a string representation of the Security object, including the name, blacklist count, and player kick count.
     *
     * Returns:
     * - A string describing the Security staff member, including inherited properties from the Staff class and the specific player kick count.
     */
    public String toString(){
        return super.toString() + "\n playerKickCount: " + playerKickCount;
    }
}
