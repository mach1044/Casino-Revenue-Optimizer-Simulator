/*                                                                           *
 * Staff.java                                                                *
 * ------------------------------------------------------------------------- *
 * Max Chen                                                                  *
 * 2025-01-08                                                                *
 * ------------------------------------------------------------------------- *
 * This class represents a staff member of the casino. It includes details   *
 * like the staff's name and the number of times they have blacklisted a     *
 * player. The class also provides functionality to blacklist a player.      *
 *                                                                           */

public class Staff{

    final static int BLACKLIST_THRESHOLD = 2; //The win ratio of a player before they get blacklisted  

    private int blacklistCount;
    private String name;
    
    /* 
     * Constructor to create a Staff object with only the name.
     *
     * Parameters:
     * - name: The name of the staff member.
     */
    Staff(String name){this.name = name;}
    
    /* 
     * Constructor to create a Staff object with both the name and blacklist count.
     *
     * Parameters:
     * - name: The name of the staff member.
     * - blacklist: The number of times the staff has blacklisted a player.
     */
    Staff(String name, int blacklist){
        this.name = name;
        blacklistCount = blacklist;
    }

    //getters and setters
    public int getBlacklistCount(){
        return blacklistCount;
    }

    public void setBlacklistCount(int cnt){
        blacklistCount = cnt;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    /*
    * Blacklists a player by setting their blacklisted status to true.
    *
    * Parameters:
    * - toBlacklist: The Player object that is to be blacklisted.
    *
    * Returns:
    * - true if the player was successfully blacklisted (assuming player exists and operation is valid).
    */
    public boolean blacklistPlayer(Player toBlacklist) {
        toBlacklist.setBlacklisted(true);  // Set the player’s blacklisted status to true
        blacklistCount++;  // Increment the blacklist count
        return true;  // Return true indicating success
    }

    //toString method --> prints name and blacklistCount
    public String toString(){
        return "name: " + name + "\nblacklistCount "+ blacklistCount;
    }

}
