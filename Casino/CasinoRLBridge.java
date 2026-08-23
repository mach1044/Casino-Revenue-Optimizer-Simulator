import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Round-boundary file exchange with an external RL process. After each
 * simulation round, exports full casino state, waits for a moves file
 * describing player relocations, validates and applies them through
 * Casino.movePlayer, and echoes back what actually happened.
 *
 * Every write is tmp-file-then-atomic-rename, followed by a round-numbered
 * ".done" marker, so a reader polling the directory never observes a
 * partially written or stale-round file.
 */
public final class CasinoRLBridge {
    private final Path stateDirectory;
    private final long pollIntervalMillis;
    private final long timeoutMillis;

    public CasinoRLBridge(Path stateDirectory, long pollIntervalMillis, long timeoutMillis)
            throws IOException {
        this.stateDirectory = stateDirectory;
        this.pollIntervalMillis = pollIntervalMillis;
        this.timeoutMillis = timeoutMillis;
        Files.createDirectories(stateDirectory);
    }

    /** Writes each player's fixed preference weight for every table, once, before round 0. */
    public void writeStaticPreferences(Casino casino) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("playerId,gameId,gameType,preferenceWeight\n");
        for(Player player : casino.getPlayers()){
            for(Game game : casino.getCasinoFloor()){
                content.append(player.getId()).append(',')
                        .append(game.getId()).append(',')
                        .append(game.getClass().getSimpleName()).append(',')
                        .append(decimal(player.getGameChoiceWeight(game)))
                        .append('\n');
            }
        }
        writeAtomic(stateDirectory.resolve("static_preferences.csv"), content.toString());
    }

    /**
     * Exports round-boundary state, waits for the RL process's move
     * decisions, applies them, and writes back what actually happened.
     */
    public void exchange(Casino casino, int round) throws IOException {
        writeRoundState(casino, round);
        Path movesDone = movesDoneFile(round);
        waitForFile(movesDone, round);
        List<RequestedMove> moves = readMoves(round);
        writeMovesResult(round, applyMoves(casino, moves));
        cleanupRound(round - 1);
    }

    private void writeRoundState(Casino casino, int round) throws IOException {
        writeAtomic(stateDirectory.resolve("tables_state.csv"), buildTablesState(casino));
        writeAtomic(stateDirectory.resolve("players_state.csv"), buildPlayersState(casino));
        // Zero-byte marker; existence alone is the signal, created only after
        // both CSVs above have already been atomically renamed into place.
        // No tmp+rename needed here (unlike writeAtomic): an empty file has
        // no content to observe half-written, so a direct create is safe.
        Files.createFile(stateDoneFile(round));
    }

    private String buildTablesState(Casino casino){
        StringBuilder content = new StringBuilder();
        content.append("gameId,gameType,capacity,minBet,maxBet,numPlayers,profit,pokerRakeRate,pokerRakeCap,slotRtp\n");
        for(Game game : casino.getCasinoFloor()){
            content.append(game.getId()).append(',')
                    .append(game.getClass().getSimpleName()).append(',')
                    .append(game.getMaxTablePopulation()).append(',')
                    .append(game.getMinBet()).append(',')
                    .append(game.getMaxBet()).append(',')
                    .append(game.getNumPlayers()).append(',')
                    .append(game.getProfit()).append(',')
                    .append(game instanceof Poker ? decimal(((Poker)game).getRakeRate()) : "").append(',')
                    .append(game instanceof Poker ? Integer.toString(((Poker)game).getRakeCap()) : "").append(',')
                    .append(game instanceof Slots ? decimal(((Slots)game).getTargetRtp()) : "")
                    .append('\n');
        }
        return content.toString();
    }

    private String buildPlayersState(Casino casino){
        StringBuilder content = new StringBuilder();
        content.append("playerId,name,bankrollStyle,behaviorStyle,currentGameId,currentGameType,balance,")
                .append("mood,momentum,socialScore,tilt,winStreak,lossStreak,roundsAtTable,roundsPlayed,")
                .append("ticksInCasino,tableSwitches,startingBalance,sessionStartingBalance,")
                .append("sessionTotalWagered,sessionTotalWinnings,pokerParticipation,pokerAggression,pokerSkill\n");
        for(Player player : casino.getPlayers()){
            if(!player.isInCasino()){
                continue;
            }
            PlaySessionRecord session = player.getCurrentSession();
            content.append(player.getId()).append(',')
                    .append(csv(player.getName())).append(',')
                    .append(player.getBankrollStyle().name()).append(',')
                    .append(player.getBehaviorStyle().getType().name()).append(',')
                    .append(session == null ? "" : Integer.toString(session.getGame().getId())).append(',')
                    .append(session == null ? "" : session.getGame().getClass().getSimpleName()).append(',')
                    .append(player.getBalance()).append(',')
                    .append(decimal(player.getMood())).append(',')
                    .append(decimal(player.getEmotionalStatus().getMomentum())).append(',')
                    .append(decimal(player.getEmotionalStatus().getSocialScore())).append(',')
                    .append(decimal(player.getTiltLevel())).append(',')
                    .append(player.getFactualStatus().getWinStreak()).append(',')
                    .append(player.getFactualStatus().getLossStreak()).append(',')
                    .append(player.getRoundsAtTable()).append(',')
                    .append(player.getRoundsPlayed()).append(',')
                    .append(player.getVisitState().getTicksInCasino()).append(',')
                    .append(player.getVisitState().getTableSwitches()).append(',')
                    .append(player.getVisitState().getStartingBalance()).append(',')
                    .append(session == null ? "" : Integer.toString(session.getStartingBalance())).append(',')
                    .append(session == null ? "" : Long.toString(session.getTotalWagered())).append(',')
                    .append(session == null ? "" : Integer.toString(session.getTotalWinnings())).append(',')
                    .append(decimal(player.getPokerParticipation())).append(',')
                    .append(decimal(player.getPokerAggression())).append(',')
                    .append(decimal(player.getPokerSkill()))
                    .append('\n');
        }
        return content.toString();
    }

    private void waitForFile(Path marker, int round) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while(!Files.exists(marker)){
            if(System.currentTimeMillis() >= deadline){
                throw new IOException("RL bridge timed out waiting for round " + round
                        + " moves - is the Python driver running? Expected: " + marker);
            }
            try{
                Thread.sleep(pollIntervalMillis);
            }
            catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException("RL bridge wait interrupted", interrupted);
            }
        }
    }

    /**
     * Reads move requests in file order; a missing player means "stay".
     * Accepts two formats for backward compatibility: legacy 2-column
     * "playerId,targetGameId" (always a MOVE), or 3-column
     * "playerId,action,targetGameId" where action is MOVE or KICK
     * (targetGameId ignored/blank for KICK).
     */
    private List<RequestedMove> readMoves(int round) throws IOException {
        Path movesFile = movesFile(round);
        List<RequestedMove> moves = new ArrayList<RequestedMove>();
        if(!Files.exists(movesFile)){
            return moves;
        }
        List<String> lines = Files.readAllLines(movesFile, StandardCharsets.UTF_8);
        for(int i = 0; i < lines.size(); i++){
            String line = lines.get(i).trim();
            if(i == 0 || line.isEmpty()){
                continue; // header or blank line
            }
            String[] fields = line.split(",", -1);
            if(fields.length == 2){
                moves.add(new RequestedMove(Integer.parseInt(fields[0].trim()),
                        "MOVE", Integer.parseInt(fields[1].trim())));
            }
            else if(fields.length == 3){
                String action = fields[1].trim().toUpperCase();
                int targetGameId = ("KICK".equals(action) || fields[2].trim().isEmpty())
                        ? -1 : Integer.parseInt(fields[2].trim());
                moves.add(new RequestedMove(Integer.parseInt(fields[0].trim()), action, targetGameId));
            }
            else{
                throw new IOException("Malformed moves line " + (i + 1) + " in " + movesFile);
            }
        }
        return moves;
    }

    /** Applies moves in file order; conflicting moves onto the same table are resolved first-come-first-served. */
    private List<Object[]> applyMoves(Casino casino, List<RequestedMove> moves){
        List<Object[]> results = new ArrayList<Object[]>();
        for(RequestedMove move : moves){
            MoveResult result = "KICK".equals(move.action)
                    ? casino.kickPlayer(move.playerId)
                    : casino.movePlayer(move.playerId, move.targetGameId);
            results.add(new Object[]{move.playerId, move.action, move.targetGameId, result});
        }
        return results;
    }

    private void writeMovesResult(int round, List<Object[]> results) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("playerId,action,targetGameId,result\n");
        for(Object[] result : results){
            boolean isKick = "KICK".equals(result[1]);
            content.append(result[0]).append(',').append(result[1]).append(',')
                    .append(isKick ? "" : result[2].toString()).append(',')
                    .append(((MoveResult)result[3]).name()).append('\n');
        }
        writeAtomic(movesResultFile(round), content.toString());
    }

    private static final class RequestedMove {
        final int playerId;
        final String action;
        final int targetGameId;

        RequestedMove(int playerId, String action, int targetGameId){
            this.playerId = playerId;
            this.action = action;
            this.targetGameId = targetGameId;
        }
    }

    private void cleanupRound(int round){
        if(round < 0){
            return;
        }
        deleteQuietly(stateDoneFile(round));
        deleteQuietly(movesFile(round));
        deleteQuietly(movesDoneFile(round));
        deleteQuietly(movesResultFile(round));
    }

    private void deleteQuietly(Path path){
        try{
            Files.deleteIfExists(path);
        }
        catch(IOException ignored){
            // Best-effort cleanup only; a leftover file cannot corrupt a
            // later round because every filename embeds its round number.
        }
    }

    private Path stateDoneFile(int round){
        return stateDirectory.resolve("round_" + round + ".state.done");
    }

    private Path movesFile(int round){
        return stateDirectory.resolve("moves_" + round + ".csv");
    }

    private Path movesDoneFile(int round){
        return stateDirectory.resolve("round_" + round + ".moves.done");
    }

    private Path movesResultFile(int round){
        return stateDirectory.resolve("round_" + round + ".moves.result.csv");
    }

    private void writeAtomic(Path finalPath, String content) throws IOException {
        Path tmp = finalPath.resolveSibling(finalPath.getFileName().toString() + ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
        try{
            Files.move(tmp, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch(AtomicMoveNotSupportedException notSupported){
            Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String csv(String value){
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String decimal(double value){
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
