/** Dependency-free tests for dynamic mood, performance and exit decisions. */
public class PlayerDecisionTestRunner {
    private static int assertions;

    public static void main(String[] args){
        testThresholdsAndRoundCounter();
        testSessionPerformanceTracking();
        testUnifiedEmotionalBetSizing();
        testTiltReducesSkill();
        testSocialEnvironmentAndSwitching();
        testMoodUpdatesOnlyAfterRound();
        testDirectionalMoodSensitivities();
        testExplicitSimulationStages();
        testSlotSpinIsOneGameRound();
        testCasinoControls();
        testCasinoRunnerIntegration();
        System.out.println("Player decision assertions passed: " + assertions);
    }

    private static void testThresholdsAndRoundCounter(){
        Player low = player(1, BankrollStyle.LOW_ROLLER, new LowStakesStyle());
        Blackjack table = new Blackjack(1, 1, 5, 100, 1L);
        PlaySessionRecord record = low.joinTable(table);
        DecisionThresholds thresholds = low.getProfile().getDecisionThresholds();
        check(thresholds.getSwitchThreshold() == 0.40,
                "Low-stakes switch threshold");
        check(thresholds.getCasinoExitThreshold() == 1.5,
                "Low-stakes exit threshold");
        check(thresholds.getPreferredVisitTicks() == 40,
                "Low-stakes preferred visit length");

        DecisionContext initial = DecisionContext.forPlayer(low);
        double initialPressure = low.getBehaviorStyle()
                .casinoExitPressure(initial, thresholds);
        for(int i = 0; i < thresholds.getPreferredVisitTicks(); i++){
            record.completeRound();
            low.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(low, table));
            low.getVisitState().completeSimulationTick();
        }
        double laterPressure = low.getBehaviorStyle().casinoExitPressure(
                DecisionContext.forPlayer(low), thresholds);
        check(laterPressure > initialPressure,
                "Round count must progressively increase exit pressure");
        check(DecisionContext.forPlayer(low).getVisitLengthPressure(thresholds) == 1,
                "Preferred visit length must create full visit-length pressure");
        check(low.getRoundsAtTable() == 40 && low.getRoundsPlayed() == 0,
                "Table exposure and actual wagered rounds must remain distinct");
        check(low.getBehaviorStyle().decide(DecisionContext.forPlayer(low))
                        != PlayerDecision.LEAVE_CASINO,
                "With casinoExitThreshold raised above the pressure formula's max (1.0), "
                        + "a neutral player at the preferred visit length must no longer leave via pressure");
        low.setMood(0.8);
        double positiveMoodPressure = low.getBehaviorStyle().casinoExitPressure(
                DecisionContext.forPlayer(low), thresholds);
        low.setMood(-0.8);
        double negativeMoodPressure = low.getBehaviorStyle().casinoExitPressure(
                DecisionContext.forPlayer(low), thresholds);
        check(positiveMoodPressure < laterPressure
                        && negativeMoodPressure == 1,
                "Good mood may justify an extended stay while bad mood keeps exit pressure full");
        low.setMood(0);

        for(int i = thresholds.getPreferredVisitTicks();
                i < thresholds.getPreferredVisitTicks() * 3; i++){
            record.completeRound();
            low.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(low, table));
            low.getVisitState().completeSimulationTick();
        }
        check(low.getBehaviorStyle().decide(DecisionContext.forPlayer(low))
                        == PlayerDecision.LEAVE_CASINO,
                "Three times the preferred visit must trigger fatigue exit");
    }

    private static void testSessionPerformanceTracking(){
        Player player = player(2, BankrollStyle.EVEN_STEVEN, new GrinderStyle());
        Blackjack table = new Blackjack(2, 1, 5, 100, 2L);
        PlaySessionRecord record = player.joinTable(table);
        table.collectBet(player, 100);
        record.completeRound();
        check(record.getTotalWagered() == 100 && record.getLastRoundNet() == -100,
                "A loss must record wager and net result");
        check(record.getMomentum() == -0.25 && record.getLossStreak() == 1,
                "A loss must lower momentum and start a losing streak");

        table.collectBet(player, 100);
        table.distributeWinnings(player, 200);
        record.completeRound();
        check(record.getTotalWagered() == 200 && record.getLastRoundNet() == 100,
                "A winning round must use checkpointed net money flow");
        check(record.getWinStreak() == 1 && record.getLossStreak() == 0,
                "A win must replace the losing streak");
        check(record.getRoundsPlayed() == 2,
                "Session round counter must increment once per completed round");
    }

    private static void testUnifiedEmotionalBetSizing(){
        Player low = seatedPlayer(5, BankrollStyle.LOW_ROLLER,
                new LowStakesStyle());
        check(low.calculateBetAmount(5, 1_000) == 20,
                "Neutral seated wager must equal the bankroll-style base bet");
        low.setMood(1);
        check(low.calculateBetAmount(5, 1_000) == 36,
                "Maximum positive mood must strongly increase the base bet");
        low.setMood(-1);
        check(low.calculateBetAmount(5, 1_000) == 10,
                "Maximum negative mood must reach the half-base wager floor");
    }

    private static void testTiltReducesSkill(){
        Player grinder = player(7, BankrollStyle.EVEN_STEVEN, new GrinderStyle());
        Blackjack table = new Blackjack(7, 1, 5, 100, 7L);
        PlaySessionRecord record = grinder.joinTable(table);
        double neutralSkill = grinder.getEffectivePokerSkill();
        table.collectBet(grinder, 100);
        record.completeRound();
        grinder.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(grinder, table));
        double firstTilt = grinder.getTiltLevel();
        check(Math.abs(firstTilt - 0.02) < 0.000_001
                        && grinder.getEffectivePokerSkill() < neutralSkill,
                "A ten-percent bankroll loss must create smoothed tilt");

        table.collectBet(grinder, 100);
        record.completeRound();
        grinder.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(grinder, table));
        check(grinder.getTiltLevel() > firstTilt,
                "A repeated material loss must increase tilt through the loss streak");
        double tiltBeforeWin = grinder.getTiltLevel();

        table.collectBet(grinder, 100);
        table.distributeWinnings(grinder, 200);
        record.completeRound();
        grinder.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(grinder, table));
        check(grinder.getTiltLevel() < tiltBeforeWin,
                "A material win must reduce accumulated tilt");

        double tiltBeforeNoWager = grinder.getTiltLevel();
        record.completeRound();
        grinder.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(grinder, table));
        check(Math.abs(grinder.getTiltLevel() - 0.95 * tiltBeforeNoWager) < 0.000_001,
                "A no-participation round must retain ninety-five percent tilt");
        DecisionContext tiltedContext = DecisionContext.forPlayer(grinder);
        DecisionThresholds thresholds = grinder.getProfile().getDecisionThresholds();
        double expectedBetMultiplier = Math.max(0.50, 1
                + 0.80 * tiltedContext.getMood()
                + 1.20 * tiltedContext.getMomentum()
                + 0.80 * tiltedContext.getSocialInfluence()
                + 2.50 * tiltedContext.getTilt());
        check(Math.abs(grinder.getBehaviorStyle().betMultiplier(tiltedContext)
                        - expectedBetMultiplier) < 0.000_001,
                "Bet multiplier must combine mood, momentum, social influence, and tilt");
        double pressureWithoutTilt = Math.max(0, Math.min(1,
                -1.20 * tiltedContext.getMood()
                        + 0.10 * tiltedContext.getVisitLossPressure(thresholds)
                        + 1.00 * tiltedContext.getVisitLengthPressure(thresholds)
                        + 0.05 * tiltedContext.getSwitchHistoryPressure()
                        - 0.45 * tiltedContext.getMomentum()));
        check(grinder.getBehaviorStyle().casinoExitPressure(tiltedContext, thresholds)
                        < pressureWithoutTilt,
                "Positive tilt must reduce casino-exit pressure and encourage chasing");

        grinder.setMood(-1);
        check(grinder.getBehaviorStyle().casinoExitPressure(
                        DecisionContext.forPlayer(grinder), thresholds) == 1,
                "The worst mood must still max out casino-exit pressure...");
        check(grinder.getBehaviorStyle().decide(DecisionContext.forPlayer(grinder))
                        != PlayerDecision.LEAVE_CASINO,
                "...but with casinoExitThreshold raised above the pressure formula's max (1.0), "
                        + "even maxed-out pressure no longer forces an exit");
    }

    private static void testSocialEnvironmentAndSwitching(){
        PlayerProfile grinderProfile = new PlayerProfile(BankrollStyle.EVEN_STEVEN,
                new GrinderStyle(), 0.35, 0.25, 0.25,
                new DecisionThresholds(0.14, 1.0, 0.35, 80));
        Player grinder = new Player(8, "P8", 1_000, grinderProfile);
        Player obnoxious = player(9, BankrollStyle.EVEN_STEVEN, new ObnoxiousStyle());
        Poker table = new Poker(9, 2, 5, 100, 9L);
        PlaySessionRecord grinderRecord = grinder.joinTable(table);
        obnoxious.joinTable(table);
        check(TableEnvironment.evaluate(grinder, table).getSocialScore() < 0,
                "A grinder must dislike a table dominated by an obnoxious player");

        grinder.setMood(-0.30);
        for(int i = 0; i < 1; i++){
            table.collectBet(grinder, 100);
            grinderRecord.completeRound();
            grinder.updateEmotionalStatusAfterRound(
                    TableEnvironment.evaluate(grinder, table));
        }
        check(grinder.getFactualStatus().getSocialSurrounding() < 0
                        && grinder.getEmotionalStatus().getSocialScore() < 0,
                "Social surroundings must feed the player's emotional social score");
        grinder.setMood(-0.30);
        DecisionContext context = DecisionContext.forPlayer(grinder);
        double multiplierWithoutSocial = Math.max(0.50, 1
                + 0.80 * context.getMood()
                + 1.20 * context.getMomentum()
                + 2.50 * context.getTilt());
        check(grinder.getBehaviorStyle().betMultiplier(context)
                        < multiplierWithoutSocial,
                "Negative social influence must reduce the bet multiplier");
        check(grinder.getBehaviorStyle().decide(context)
                        == PlayerDecision.SWITCH_TABLE,
                "Bad mood, momentum and company must cause a table switch; pressure "
                        + grinder.getBehaviorStyle().switchPressure(context));
    }

    private static void testMoodUpdatesOnlyAfterRound(){
        Player low = player(10, BankrollStyle.LOW_ROLLER, new LowStakesStyle());
        Blackjack table = new Blackjack(10, 1, 5, 100, 10L);
        PlaySessionRecord record = low.joinTable(table);
        double beforeBet = low.getMood();
        check(low.getFactualStatus().getStartingBankroll() == 1_000
                        && low.getFactualStatus().getCurrentBankroll() == 1_000,
                "Factual status must initialize both bankroll snapshots");
        table.collectBet(low, 10);
        check(low.getMood() == beforeBet,
                "Collecting a bet must not directly alter mood");
        check(low.getRoundsInCasino() == 0,
                "In-game betting must not advance emotional status");
        record.completeRound();
        low.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(low, table));
        check(low.getMood() < beforeBet,
                "A settled losing round must lower mood");
        check(low.getRoundsPlayed() == 1
                        && low.getEmotionalStatus().getMomentum() < 0,
                "Post-game must update rounds and emotional momentum together");
        check(low.getFactualStatus().getLastRoundNet() == -10
                        && low.getFactualStatus().getLossStreak() == 1
                        && low.getFactualStatus().getCurrentBankroll() == 990,
                "Factual status must record results separately from mood and momentum");
    }

    private static void testDirectionalMoodSensitivities(){
        PlayerProfile winnerProfile = new PlayerProfile(BankrollStyle.LOW_ROLLER,
                new LowStakesStyle(), 0, 1.0, 0.10);
        PlayerProfile loserProfile = new PlayerProfile(BankrollStyle.LOW_ROLLER,
                new LowStakesStyle(), 0, 1.0, 0.10);

        Player winner = new Player(20, "Winner", 1_000, winnerProfile);
        Blackjack winTable = new Blackjack(20, 1, 5, 100, 20L);
        PlaySessionRecord winRecord = winner.joinTable(winTable);
        winTable.collectBet(winner, 10);
        winTable.distributeWinnings(winner, 20);
        winRecord.completeRound();
        winner.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(winner, winTable));

        Player loser = new Player(21, "Loser", 1_000, loserProfile);
        Blackjack lossTable = new Blackjack(21, 1, 5, 100, 21L);
        PlaySessionRecord lossRecord = loser.joinTable(lossTable);
        lossTable.collectBet(loser, 10);
        lossRecord.completeRound();
        loser.updateEmotionalStatusAfterRound(TableEnvironment.evaluate(loser, lossTable));

        check(Math.abs(winner.getMood() - 0.0018) < 0.000_001,
                "A win must use win sensitivity in the mood formula");
        check(Math.abs(loser.getMood() + 0.00018) < 0.000_001,
                "A loss must use loss sensitivity in the mood formula");
        double tiltAfterLoss = loser.getTiltLevel();
        check(Math.abs(tiltAfterLoss - 0.002) < 0.000_001,
                "One-percent loss must contribute two-tenths of one percent tilt");
        loser.setMood(-1);
        check(loser.getTiltLevel() == tiltAfterLoss,
                "Direct mood changes must not directly rewrite independent tilt");
    }

    private static void testExplicitSimulationStages(){
        Casino casino = new Casino();
        Player player = player(22, BankrollStyle.LOW_ROLLER, new LowStakesStyle());
        Blackjack table = new Blackjack(22, 1, 5, 5, 22L);
        casino.addPlayer(player);
        casino.addGame(table);

        CasinoSimulationEngine.runPreGameStage(casino);
        check(player.getCurrentSession() != null && player.getRoundsPlayed() == 0,
                "Pre-game must select a table without changing emotional status");

        java.util.IdentityHashMap<Player, TableEnvironment> completed =
                CasinoSimulationEngine.runInGameStage(casino);
        check(player.getCurrentSession().getRoundsPlayed() == 0
                        && player.getRoundsPlayed() == 0,
                "In-game must simulate money flow without settling player status");

        CasinoSimulationEngine.runPostGameStage(completed);
        check(player.getCurrentSession().getRoundsPlayed() == 1
                        && player.getRoundsPlayed() == 1,
                "Post-game must settle the session and emotional status together");
    }

    private static void testCasinoRunnerIntegration(){
        Casino casino = new Casino();
        Player grinder = new Player(11, "Integration", 1_000_000,
                PlayerProfile.forStyles(BankrollStyle.EVEN_STEVEN,
                        new GrinderStyle()));
        casino.addPlayer(grinder);
        casino.addGame(new Blackjack(11, 1, 5, 5, 11L));

        CasinoSimulationEngine.runRound(casino);
        check(grinder.getRoundsInCasino() == 1,
                "CasinoRunner must increment the visit counter exactly once");
        check(grinder.toString().contains("Rounds Played: 1"),
                "Player display must include the current visit round count");
        check(grinder.getPlayHistory().get(0).getRoundsPlayed() == 1,
                "CasinoRunner must settle one table-performance round");

        int safetyLimit = grinder.getProfile().getDecisionThresholds()
                .getPreferredVisitTicks() * 3;
        for(int i = 1; i <= safetyLimit && grinder.isInCasino(); i++){
            CasinoSimulationEngine.runRound(casino);
        }
        check(!grinder.isInCasino(),
                "CasinoRunner must eventually remove a fatigued long-session player");
        check(grinder.getCurrentSession() == null,
                "A casino exit must also remove the player from the table");
    }

    private static void testCasinoControls(){
        Casino casino = new Casino();
        casino.setControls(new CasinoControls(0.07, 25, 0.90, 50,
                TableAssignmentPolicy.PREFERENCE_WEIGHTED));
        Player player = player(24, BankrollStyle.LOW_ROLLER,
                new LowStakesStyle());
        casino.addPlayer(player);
        player.addWinnings(50);

        CasinoSimulationEngine.applyRemovalPolicies(casino);
        check(!player.isInCasino()
                        && player.getVisitState().wasRemovedByCasino(),
                "Casino must remove a player who reaches the configured profit limit");

        Slots slots = new Slots(24, 5, casino.getControls().getSlotRtp());
        check(Math.abs(slots.getTargetRtp() - 0.90) < 0.000_001,
                "Slots must retain the configured target RTP");
        Poker poker = new Poker(25, 2, 5, 100,
                casino.getControls().getPokerRakeRate(),
                casino.getControls().getPokerRakeCap(), 25L);
        check(poker.getRakeRate() == 0.07 && poker.getRakeCap() == 25,
                "Poker must retain the configured rake rate and cap");
    }

    private static void testSlotSpinIsOneGameRound(){
        Casino casino = new Casino();
        Player player = player(23, BankrollStyle.LOW_ROLLER,
                new LowStakesStyle());
        Slots machine = new Slots(23, 5);
        casino.addPlayer(player);
        casino.addGame(machine);

        CasinoSimulationEngine.runRound(casino);
        PlaySessionRecord session = player.getCurrentSession();
        check(session != null && session.getTotalWagered() == 5,
                "One simulation tick at Slots must wager for exactly one spin");
        check(player.getRoundsAtTable() == 1
                        && player.getRoundsPlayed() == 1
                        && player.getVisitState().getTicksInCasino() == 1,
                "One slot spin must equal one table round, played round, and visit tick");
        int endingBalance = player.getBalance();
        player.leaveTable();
        check(session.getStartingBalance() == 1_000
                        && session.getEndingBalance() == endingBalance,
                "A session must retain its starting and ending balances");
    }

    private static Player seatedPlayer(int id, BankrollStyle bankroll,
                                       BehaviorStyle behavior){
        Player player = player(id, bankroll, behavior);
        player.joinTable(new Blackjack(id, 1, 5, 1_000, id));
        return player;
    }

    private static Player player(int id, BankrollStyle bankroll,
                                 BehaviorStyle behavior){
        return new Player(id, "P" + id, 1_000,
                PlayerProfile.forStyles(bankroll, behavior));
    }

    private static void check(boolean condition, String message){
        assertions++;
        if(!condition){
            throw new AssertionError(message);
        }
    }
}
