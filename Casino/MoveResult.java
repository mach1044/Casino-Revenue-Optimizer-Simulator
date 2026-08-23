/** Outcome of an attempted casino-initiated player relocation. */
public enum MoveResult {
    SUCCESS, PLAYER_NOT_FOUND, TARGET_NOT_FOUND, PLAYER_NOT_SEATED,
    SAME_TABLE, TARGET_FULL, INSUFFICIENT_BALANCE, LEAVE_FAILED, JOIN_FAILED,
    PLAYER_NOT_IN_CASINO
}
