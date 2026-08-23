/** How a player's own voluntary table choice picks among eligible tables. */
public enum TableAssignmentPolicy {
    /** Weighted by the player's behavior-style game preference (default, prior behavior). */
    PREFERENCE_WEIGHTED,
    /** Uniformly random among eligible tables, ignoring preference entirely. */
    RANDOM
}
