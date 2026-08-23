/*                                                                          *
* FloorManager.java                                                         *
* ------------------------------------------------------------------------- *
 * Max Chen                                                                 *
* 2025-01-10                                                                *
* ------------------------------------------------------------------------- *
* Class which facilitates the functions of a security guard in the casino   *
*                                                                           */

public class FloorManager extends Staff{
    //constructor with only name
    FloorManager(String name){super(name);};

    //constructor with name and blacklistCount
    FloorManager(String name, int blacklistCount){
        super(name, blacklistCount);
    }
    

    /*
     * Checks the statistics of a given game.
     *
     * Parameters:
     * - gameToCheck: The Game object whose statistics are to be checked.
     *
     * Returns:
     * - true if the game exists and its statistics are printed.
     * - false if the game is null.
     */    
    public boolean checkGameStats(Game gameToCheck){
        if(gameToCheck == null){return false;}
        System.out.println(gameToCheck);
        return true;
    }

    /*
    * Checks the statistics of a given player.
    * Prints the player's details if the player exists, otherwise returns false.
    *
    * Parameters:
    * - playerToCheck: The Player object whose statistics are to be checked.
    *
    * Returns:
    * - true if the player exists and their statistics are printed.
    * - false if the player is null.
    */    
    public boolean checkPlayerStats(Player playerToCheck){
        if(playerToCheck == null){return false;}
        System.out.println(playerToCheck);
        return true;
    }   

    /*
    * Modifies the odds of a game action, potentially rigging the game.
    * Validates the chance to win and the action index before applying the change.
    *
    * Parameters:
    * - g: The Game object whose action's odds are to be modified.
    * - actionIndex: The index of the action to modify.
    * - chanceToWin: The new chance to win (must be between 0 and 1).
    *
    * Returns:
    * - true if the odds were successfully changed.
    * - false if the chance to win is invalid or the action index is out of bounds.
    */
    public boolean rigGame(Game g, int actionIndex, double chanceToWin){
        if(chanceToWin > 1 || chanceToWin < 0){
            return false;
        }
        else if(g.getNumActions() <= actionIndex){
            return false;
        }
        else{
            g.getActions()[actionIndex].setActualOdds(chanceToWin);
            return true;
        }
    }
}
