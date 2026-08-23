import java.util.Arrays;

/** Dependency-free behavior-style and bankroll-composition tests. */
public class BehaviorStyleTestRunner {
    private static int assertions;

    public static void main(String[] args){
        testPokerProfiles();
        testBehaviorCompatibilityChart();
        testPlayerProfileComposition();
        testGamePreferences();
        testCompatibilityRules();
        testDefaultCombinations();
        testBankrollBetScale();
        testStyleDecisions();
        testRouletteDecisionRatios();
        testPokerRakeMultipliers();
        testWeightedGameSelection();
        System.out.println("Behavior style assertions passed: " + assertions);
    }

    private static void testPlayerProfileComposition(){
        PlayerProfile profile = new PlayerProfile(BankrollStyle.HIGH_ROLLER,
                new GrinderStyle(), 0.35, 0.90, 0.25);
        Player player = new Player(1, "Profile", 1_000, profile);

        check(player.getProfile() == profile,
                "Player must own the supplied profile");
        check(player.getBankrollStyle() == BankrollStyle.HIGH_ROLLER,
                "Profile must expose bankroll style through Player");
        check(player.getBehaviorStyle().getType() == BehaviorType.GRINDER,
                "Profile must expose behavior style through Player");
        check(profile.getSocialSensitivity() == 0.35,
                "Profile must retain stable social sensitivity");
        check(profile.getWinSensitivity() == 0.90,
                "Profile must retain stable win sensitivity");
        check(profile.getLossSensitivity() == 0.25,
                "Profile must retain stable loss sensitivity");

        player.setMood(0.8);
        check(player.getMood() == 0.8 && profile.getWinSensitivity() == 0.90
                        && profile.getLossSensitivity() == 0.25,
                "Emotional status must not change stable profile sensitivities");
        check(profile.getEmotionalStatus().getMood() == 0.8,
                "PlayerProfile must own the player's emotional status");
        check(profile.getFactualStatus() instanceof PlayerEmotionalStatus,
                "Factual status must extend the core emotional status");
        checkThrows(() -> new PlayerProfile(BankrollStyle.LOW_ROLLER,
                        new LowStakesStyle(), 1.1, 0.5, 0.5),
                "Profile social traits must remain within zero and one");
        checkThrows(() -> new PlayerProfile(BankrollStyle.LOW_ROLLER,
                        new LowStakesStyle(), 0.5, -0.1, 0.5),
                "Win sensitivity must remain within zero and one");
        checkThrows(() -> new PlayerProfile(BankrollStyle.LOW_ROLLER,
                        new LowStakesStyle(), 0.5, 0.5, 1.1),
                "Loss sensitivity must remain within zero and one");
    }

    private static void testPokerProfiles(){
        checkTraits(new WhaleStyle(), 0.85, 0.95, 0.50);
        checkTraits(new GrinderStyle(), 0.55, 0.50, 0.75);
        checkTraits(new LowStakesStyle(), 0.25, 0.15, 0.40);
        checkTraits(new ObnoxiousStyle(), 0.20, 0.45, 0.30);
    }

    private static void testBehaviorCompatibilityChart(){
        BehaviorStyle[] styles = {new WhaleStyle(), new GrinderStyle(),
                new LowStakesStyle(), new ObnoxiousStyle()};
        BehaviorType[] types = {BehaviorType.WHALE, BehaviorType.GRINDER,
                BehaviorType.LOW_STAKES, BehaviorType.OBNOXIOUS};
        double[][] expected = {
                {0.40, 0.10, 0.05, 0.10},
                {0.65, 0.05, 0.05, -0.25},
                {0.25, -0.10, 0.20, -0.45},
                {0.20, -0.15, 0.20, -0.25}
        };
        for(int row = 0; row < styles.length; row++){
            for(int column = 0; column < types.length; column++){
                check(styles[row].affinityFor(types[column]) == expected[row][column],
                        "Behavior compatibility chart value " + row + "," + column);
            }
        }
    }

    private static void testGamePreferences(){
        Game blackjack = new Blackjack(1, 1, 5, 100, 1L);
        Game roulette = new Roulette(2, 1, 5, 100);
        Game slots = new Slots(3, 5);
        Game craps = new Craps(4, 1, 5, 100, 1L);
        Game poker = new Poker(5, 2, 5, 100, 1L);

        BehaviorStyle whale = new WhaleStyle();
        check(whale.preferenceFor(craps) > whale.preferenceFor(poker)
                        && whale.preferenceFor(poker) > whale.preferenceFor(slots),
                "Whale must prioritize craps, then poker, over slots");

        BehaviorStyle grinder = new GrinderStyle();
        check(grinder.preferenceFor(poker) > grinder.preferenceFor(blackjack)
                        && grinder.preferenceFor(blackjack) > grinder.preferenceFor(roulette),
                "Grinder must prioritize poker and blackjack");

        BehaviorStyle lowStakes = new LowStakesStyle();
        check(lowStakes.preferenceFor(blackjack) == lowStakes.preferenceFor(roulette)
                        && lowStakes.preferenceFor(roulette) == lowStakes.preferenceFor(slots),
                "Low-stakes player must have balanced recreational preferences");

        BehaviorStyle obnoxious = new ObnoxiousStyle();
        check(obnoxious.preferenceFor(poker) > obnoxious.preferenceFor(blackjack)
                        && obnoxious.preferenceFor(blackjack) > obnoxious.preferenceFor(craps),
                "Obnoxious player must favor perceived skill games");
    }

    private static void testCompatibilityRules(){
        check(BehaviorStyleFactory.isCompatible(BehaviorType.WHALE, BankrollStyle.HIGH_ROLLER)
                        && !BehaviorStyleFactory.isCompatible(BehaviorType.WHALE, BankrollStyle.EVEN_STEVEN)
                        && !BehaviorStyleFactory.isCompatible(BehaviorType.WHALE, BankrollStyle.LOW_ROLLER),
                "Whale must be high-roller only");
        check(BehaviorStyleFactory.isCompatible(BehaviorType.GRINDER, BankrollStyle.HIGH_ROLLER)
                        && BehaviorStyleFactory.isCompatible(BehaviorType.GRINDER, BankrollStyle.EVEN_STEVEN)
                        && !BehaviorStyleFactory.isCompatible(BehaviorType.GRINDER, BankrollStyle.LOW_ROLLER),
                "Grinder must be high or even bankroll");
        check(!BehaviorStyleFactory.isCompatible(BehaviorType.LOW_STAKES, BankrollStyle.HIGH_ROLLER)
                        && BehaviorStyleFactory.isCompatible(BehaviorType.LOW_STAKES, BankrollStyle.EVEN_STEVEN)
                        && BehaviorStyleFactory.isCompatible(BehaviorType.LOW_STAKES, BankrollStyle.LOW_ROLLER),
                "Low-stakes must be low or even bankroll");
        check(BehaviorStyleFactory.isCompatible(BehaviorType.OBNOXIOUS, BankrollStyle.HIGH_ROLLER)
                        && BehaviorStyleFactory.isCompatible(BehaviorType.OBNOXIOUS, BankrollStyle.EVEN_STEVEN)
                        && BehaviorStyleFactory.isCompatible(BehaviorType.OBNOXIOUS, BankrollStyle.LOW_ROLLER),
                "Obnoxious must support every bankroll tier");

        checkThrows(() -> PlayerProfile.forStyles(
                        BankrollStyle.LOW_ROLLER, new WhaleStyle()),
                "Invalid constructor combination must be rejected");
        Player low = player(2, "Setter", BankrollStyle.LOW_ROLLER,
                new LowStakesStyle(), 100);
        checkThrows(() -> low.setBehaviorStyle(new GrinderStyle()),
                "Invalid behavior reassignment must be rejected");
    }

    private static void testDefaultCombinations(){
        Player low = defaultPlayer(3, BankrollStyle.LOW_ROLLER);
        Player obnoxious = player(4, "Obnoxious", BankrollStyle.LOW_ROLLER,
                new ObnoxiousStyle(), 100);
        check(defaultPlayer(1, BankrollStyle.HIGH_ROLLER).getBehaviorStyle().getType()
                        == BehaviorType.WHALE,
                "HighRoller must default to Whale");
        check(defaultPlayer(2, BankrollStyle.EVEN_STEVEN).getBehaviorStyle().getType()
                        == BehaviorType.GRINDER,
                "EvenSteven must default to Grinder");
        check(low.getBehaviorStyle().getType()
                        == BehaviorType.LOW_STAKES,
                "LowRoller must default to Low-Stakes");
        check(low.getProfile().getSocialSensitivity() == 0.75,
                "Low-Stakes social sensitivity");
        check(obnoxious.getProfile().getSocialSensitivity() == 0.65,
                "Obnoxious social sensitivity");
    }

    private static void testBankrollBetScale(){
        Player low = defaultPlayer(1, BankrollStyle.LOW_ROLLER, 1_000);
        Player even = defaultPlayer(2, BankrollStyle.EVEN_STEVEN, 1_000);
        Player high = defaultPlayer(3, BankrollStyle.HIGH_ROLLER, 1_000);
        check(low.calculateBetAmount(5, 1_000) == 20,
                "Neutral LowRoller must wager two percent");
        check(even.calculateBetAmount(5, 1_000) == 40,
                "Neutral EvenSteven must wager four percent");
        check(high.calculateBetAmount(5, 1_000) == 80,
                "Neutral HighRoller must wager eight percent");
    }

    private static void testStyleDecisions(){
        Craps craps = new Craps(1, 1, 5, 100, 1L);
        Player low = defaultPlayer(1, BankrollStyle.LOW_ROLLER, 1_000);

        check(low.chooseCraps(craps) == Craps.PASS_LINE,
                "Low-stakes craps choice must be Pass Line");

    }

    private static void testRouletteDecisionRatios(){
        checkRouletteWeights(new WhaleStyle(),
                25, 7, 7, 8, 8, 10, 35);
        checkRouletteWeights(new GrinderStyle(),
                50, 10, 6, 5, 4, 5, 20);
        checkRouletteWeights(new LowStakesStyle(),
                45, 10, 6, 5, 4, 5, 25);
        checkRouletteWeights(new ObnoxiousStyle(),
                30, 6, 6, 7, 7, 9, 35);

        Roulette roulette = new Roulette(2, 1, 5, 100);
        Player whale = defaultPlayer(2, BankrollStyle.HIGH_ROLLER, 1_000);
        int halfBets = 0;
        int straightBets = 0;
        int redBlack = 0;
        int parity = 0;
        int range = 0;
        int trials = 100_000;
        for(int i = 0; i < trials; i++){
            int action = whale.chooseRoulette(roulette);
            if(action == 0) halfBets++;
            if(action == 6) straightBets++;

            char halfCode = roulette.chooseHalfCode();
            if(halfCode == 'R' || halfCode == 'B') redBlack++;
            else if(halfCode == 'E' || halfCode == 'O') parity++;
            else range++;
        }
        check(Math.abs(halfBets / (double)trials - 0.25) < 0.01
                        && Math.abs(straightBets / (double)trials - 0.35) < 0.01,
                "Whale roulette sampling must follow half and straight-up weights");
        check(Math.abs(redBlack / (double)trials - 0.70) < 0.01
                        && Math.abs(parity / (double)trials - 0.15) < 0.01
                        && Math.abs(range / (double)trials - 0.15) < 0.01,
                "Half bets must use the 70/15/15 subtype distribution");
    }

    private static void checkRouletteWeights(BehaviorStyle style,
                                             double... expected){
        check(Arrays.equals(style.getRouletteBetWeights(), expected),
                style.getDisplayName() + " roulette weights");
    }

    private static void testWeightedGameSelection(){
        GrinderStyle grinderStyle = new GrinderStyle();
        Player grinder = player(1, "Grinder", BankrollStyle.EVEN_STEVEN,
                grinderStyle, 1_000_000);
        Poker poker = new Poker(1, 2, 5, 100, 1L);
        Slots slots = new Slots(2, 5);
        int pokerSelections = 0;
        int trials = 100_000;
        for(int i = 0; i < trials; i++){
            if(CasinoSimulationEngine.chooseGameForPlayer(
                    grinder, Arrays.asList(poker, slots),
                    TableAssignmentPolicy.PREFERENCE_WEIGHTED) == poker){
                pokerSelections++;
            }
        }
        double rate = pokerSelections / (double)trials;
        check(rate > 0.975 && rate < 0.992,
                "Weighted grinder selection must reflect 6.0 versus 0.1; observed " + rate);
    }

    private static void testPokerRakeMultipliers(){
        BehaviorStyle grinder = new GrinderStyle();
        checkPokerMultiplier(grinder, 0.01, 0.99);
        checkPokerMultiplier(grinder, 0.02, 0.96);
        checkPokerMultiplier(grinder, 0.03, 0.91);
        checkPokerMultiplier(grinder, 0.04, 0.84);
        checkPokerMultiplier(grinder, 0.05, 0.75);
        checkPokerMultiplier(grinder, 0.10, 0);
        checkPokerMultiplier(new ObnoxiousStyle(), 0.10, 0);
        checkPokerMultiplier(new LowStakesStyle(), 0.10, 0.84);
        checkPokerMultiplier(new LowStakesStyle(), 0.25, 0);
        checkPokerMultiplier(new WhaleStyle(), 0.10, 0.75);
        checkPokerMultiplier(new WhaleStyle(), 0.20, 0);

        Slots slots = new Slots(99, 5, 0.10);
        check(grinder.gameChoiceMultiplier(slots) == 1,
                "Slot RTP must not affect game choice");
    }

    private static void checkPokerMultiplier(BehaviorStyle style,
                                              double rake,
                                              double expected){
        Poker poker = new Poker(98, 2, 5, 100, rake, 100, 98L);
        double actual = style.gameChoiceMultiplier(poker);
        check(Math.abs(actual - expected) < 0.000_001,
                style.getDisplayName() + " poker multiplier at " + rake
                        + " rake; observed " + actual);
    }

    private static Player defaultPlayer(int id, BankrollStyle bankroll){
        return defaultPlayer(id, bankroll, 100);
    }

    private static Player defaultPlayer(int id, BankrollStyle bankroll, int chips){
        return new Player(id, bankroll.getDisplayName(), chips,
                PlayerProfile.defaultProfile(bankroll));
    }

    private static Player player(int id, String name, BankrollStyle bankroll,
                                 BehaviorStyle behavior, int chips){
        return new Player(id, name, chips,
                PlayerProfile.forStyles(bankroll, behavior));
    }

    private static void checkTraits(BehaviorStyle style, double participation,
                                    double aggression, double skill){
        PokerTraits traits = style.getDefaultPokerTraits();
        check(traits.getParticipation() == participation,
                style.getDisplayName() + " participation profile");
        check(traits.getAggression() == aggression,
                style.getDisplayName() + " aggression profile");
        check(traits.getSkill() == skill,
                style.getDisplayName() + " skill profile");
    }

    private static void checkThrows(Runnable operation, String message){
        boolean threw = false;
        try{
            operation.run();
        }
        catch(IllegalArgumentException expected){
            threw = true;
        }
        check(threw, message);
    }

    private static void check(boolean condition, String message){
        assertions++;
        if(!condition){
            throw new AssertionError(message);
        }
    }
}
