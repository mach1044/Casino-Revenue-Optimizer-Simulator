import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Runs casino rounds independently of the interactive and batch interfaces. */
public final class CasinoSimulationEngine {

    private CasinoSimulationEngine(){
    }

    public static void runRound(Casino casino){
        runPreGameStage(casino);
        IdentityHashMap<Player, TableEnvironment> completedRounds =
                runInGameStage(casino);
        runPostGameStage(completedRounds);
        applyRemovalPolicies(casino);
        for(Player player : casino.getPlayers()){
            player.getVisitState().completeSimulationTick();
        }
    }

    /** Players decide whether to leave, stay, switch, or choose a table. */
    static void runPreGameStage(Casino casino){
        for(Player player : casino.getPlayers()){
            if(!player.isInCasino()){
                continue;
            }
            if(player.isBlacklisted()){
                player.leaveCasino();
                continue;
            }

            PlayerDecision decision = player.getBehaviorStyle()
                    .decide(DecisionContext.forPlayer(player));
            if(decision == PlayerDecision.LEAVE_CASINO){
                player.leaveCasino();
                continue;
            }
            if(decision == PlayerDecision.SWITCH_TABLE
                    && player.getCurrentSession() != null){
                player.switchTable();
            }

            if(player.getCurrentSession() == null){
                ArrayList<Game> eligibleGames = new ArrayList<Game>();
                for(Game game : casino.getCasinoFloor()){
                    if(game.getNumPlayers() < game.getMaxTablePopulation()
                            && player.getBalance() >= game.getMinBet()){
                        eligibleGames.add(game);
                    }
                }

                Game previousTable = player.getVisitState().getLastLeftTable();
                if(previousTable != null && eligibleGames.size() > 1){
                    eligibleGames.remove(previousTable);
                }

                Game selectedGame = chooseGameForPlayer(player, eligibleGames,
                        casino.getControls().getTableAssignmentPolicy());
                if(selectedGame != null){
                    PlaySessionRecord record = player.joinTable(selectedGame);
                    if(record != null){
                        casino.addRecord(record);
                        player.getVisitState().clearLastLeftTable();
                    }
                }
            }
        }
    }

    /** Games simulate one round without directly changing emotional status. */
    static IdentityHashMap<Player, TableEnvironment> runInGameStage(Casino casino){
        IdentityHashMap<Player, TableEnvironment> environments =
                new IdentityHashMap<Player, TableEnvironment>();
        for(Game game : casino.getCasinoFloor()){
            ArrayList<Player> roundPlayers =
                    new ArrayList<Player>(game.getCurrentTable());
            game.simulateRound();

            for(Player player : roundPlayers){
                if(player.getCurrentSession() != null
                        && player.getCurrentSession().getGame() == game){
                    environments.put(player,
                            TableEnvironment.evaluate(player, game));
                }
            }
        }
        return environments;
    }

    /** Settles financial checkpoints, then updates all player emotional state. */
    static void runPostGameStage(
            IdentityHashMap<Player, TableEnvironment> completedRounds){
        for(Player player : completedRounds.keySet()){
            if(player.getCurrentSession() != null){
                player.getCurrentSession().completeRound();
            }
        }
        for(Map.Entry<Player, TableEnvironment> round : completedRounds.entrySet()){
            Player player = round.getKey();
            if(player.getCurrentSession() != null){
                player.updateEmotionalStatusAfterRound(round.getValue());
            }
        }
    }

    /** Applies casino-owned removal rules after all payouts for the round. */
    static void applyRemovalPolicies(Casino casino){
        for(Player player : casino.getPlayers()){
            if(player.isInCasino()
                    && casino.getControls().shouldRemoveForProfit(player)){
                player.removeFromCasinoByPolicy();
            }
        }
    }

    static Game chooseGameForPlayer(Player player, List<Game> eligibleGames,
                                    TableAssignmentPolicy policy){
        if(eligibleGames.isEmpty()){
            return null;
        }
        if(policy == TableAssignmentPolicy.RANDOM){
            return eligibleGames.get(ThreadLocalRandom.current().nextInt(eligibleGames.size()));
        }

        double totalPreference = 0;
        for(Game game : eligibleGames){
            totalPreference += Math.max(0, player.getGameChoiceWeight(game));
        }
        if(totalPreference <= 0){
            return null;
        }

        double selection = ThreadLocalRandom.current().nextDouble(totalPreference);
        for(Game game : eligibleGames){
            selection -= Math.max(0, player.getGameChoiceWeight(game));
            if(selection < 0){
                return game;
            }
        }
        return eligibleGames.get(eligibleGames.size() - 1);
    }
}
