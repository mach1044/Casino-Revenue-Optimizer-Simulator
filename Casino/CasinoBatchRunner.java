import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Non-interactive casino simulation driven by a properties file.
 */
public final class CasinoBatchRunner {

    private static final String[] FIRST_NAMES = {
        "Alex", "Avery", "Blake", "Cameron", "Casey", "Charlie", "Dakota", "Drew",
        "Emerson", "Frankie", "Harper", "Jamie", "Jordan", "Kai", "Logan", "Morgan",
        "Parker", "Quinn", "Reese", "Riley", "Robin", "Rowan", "Sam", "Taylor"
    };

    private static final String[] LAST_NAMES = {
        "Adams", "Bennett", "Brooks", "Campbell", "Chen", "Diaz", "Evans", "Foster",
        "Garcia", "Green", "Hall", "Harris", "Jackson", "Kim", "Lee", "Martin",
        "Mitchell", "Patel", "Rivera", "Robinson", "Singh", "Smith", "Thompson", "Wilson"
    };

    private CasinoBatchRunner() {
    }

    public static void main(String[] args) {
        if(args.length == 0){
            System.out.println("Usage: java CasinoRunner <scenario.properties>");
            return;
        }

        try{
            run(Paths.get(args[0]).toAbsolutePath().normalize());
        }
        catch(Exception e){
            System.err.println("Batch simulation failed: " + e.getMessage());
        }
    }

    private static void run(Path scenarioPath) throws IOException {
        SimulationConfig config = SimulationConfig.load(scenarioPath);
        Random random = new Random(config.seed);
        Casino casino = new Casino();
        casino.setControls(new CasinoControls(config.pokerRakeRate,
                config.pokerRakeCap, config.slotRtp, config.playerProfitLimit,
                config.tableAssignmentPolicy));
        Map<Player, InitialPlayerState> initialStates = new IdentityHashMap<Player, InitialPlayerState>();

        if(config.hasPlayersFile()){
            loadPlayers(casino, config.resolvePlayersFile(scenarioPath), initialStates);
        }
        else{
            generatePlayers(casino, config, random, initialStates);
        }
        generateStaff(casino, config);
        generateTables(casino, config);

        CasinoRLBridge rlBridge = null;
        if(config.rlBridgeEnabled){
            rlBridge = new CasinoRLBridge(config.resolveRlStateDirectory(scenarioPath),
                    config.rlPollIntervalMillis, config.rlTimeoutMillis);
            rlBridge.writeStaticPreferences(casino);
        }
        for(int round = 0; round < config.rounds; round++){
            CasinoSimulationEngine.runRound(casino);
            if(rlBridge != null && round % config.rlExchangeIntervalRounds == 0){
                rlBridge.exchange(casino, round);
            }
        }

        // Close active sessions without deleting their historical records.
        closeActiveSessions(casino);

        int casinoProfit = 0;
        for(Game game : casino.getCasinoFloor()){
            casinoProfit += game.getProfit();
        }
        casino.setRevenue(casinoProfit);

        Path outputDirectory = config.resolveOutputDirectory(scenarioPath);
        Files.createDirectories(outputDirectory);
        writeSummary(outputDirectory.resolve("summary.txt"), casino, config, initialStates);
        writePlayers(outputDirectory.resolve("players.csv"), casino, initialStates);
        writeTables(outputDirectory.resolve("tables.csv"), casino);
        writeSessions(outputDirectory.resolve("sessions.csv"), casino);

        System.out.println("Batch simulation complete.");
        System.out.println("Rounds: " + config.rounds);
        System.out.println("Players: " + casino.getPlayers().size());
        System.out.println("Tables/machines: " + casino.getCasinoFloor().size());
        System.out.println("Sessions: " + casino.getPlayRecords().size());
        System.out.println("Casino table profit: $" + casinoProfit);
        System.out.println("Results: " + outputDirectory);
    }

    private static void generatePlayers(Casino casino, SimulationConfig config, Random random,
                                        Map<Player, InitialPlayerState> initialStates){
        for(int i = 0; i < config.players; i++){
            String name = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " "
                    + LAST_NAMES[random.nextInt(LAST_NAMES.length)] + " " + (i + 1);
            int balance = randomInt(random, config.minBalance, config.maxBalance);
            int type = choosePlayerType(random, config);
            BehaviorStyle behavior = chooseBehaviorStyle(
                    random, BankrollStyle.fromMenuCode(type));

            casino.addPlayer(name, balance, type, behavior);
            Player player = casino.getPlayers().get(casino.getPlayers().size() - 1);
            player.setMood(randomDouble(random, config.minMood, config.maxMood));
            player.setYearStarted(randomInt(random, config.minYearStarted, Casino.CURR_YEAR));
            player.setBlacklisted(random.nextDouble() * 100.0 < config.blacklistedPercent);
            // Preserve the stereotype profile while preventing every member
            // of a style from having perfectly identical poker traits.
            player.setPokerParticipation(jitterTrait(random, player.getPokerParticipation()));
            player.setPokerAggression(jitterTrait(random, player.getPokerAggression()));
            player.setPokerSkill(jitterTrait(random, player.getPokerSkill()));

            initialStates.put(player, new InitialPlayerState(balance));
        }
    }

    /** Loads a fixed player population for repeatable casino experiments. */
    private static void loadPlayers(Casino casino, Path playersPath,
                                    Map<Player, InitialPlayerState> initialStates)
            throws IOException {
        if(!Files.isRegularFile(playersPath)){
            throw new IOException("Player file not found: " + playersPath);
        }

        try(BufferedReader reader = Files.newBufferedReader(playersPath, StandardCharsets.UTF_8)){
            String header = reader.readLine();
            String expectedHeader = "name,balance,bankrollType,behaviorStyle,mood,blacklisted,pokerParticipation,pokerAggression,pokerSkill";
            if(header == null || !expectedHeader.equalsIgnoreCase(header.trim())){
                throw new IOException("Player file must use this header: " + expectedHeader);
            }

            String line;
            int lineNumber = 1;
            while((line = reader.readLine()) != null){
                lineNumber++;
                if(line.trim().isEmpty()){
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if(fields.size() != 9){
                    throw new IOException("Player file line " + lineNumber
                            + " must contain exactly 9 fields");
                }

                try{
                    String name = required(fields.get(0), "name");
                    int balance = Integer.parseInt(fields.get(1).trim());
                    BankrollStyle bankroll = BankrollStyle.valueOf(
                            fields.get(2).trim().toUpperCase(Locale.ROOT));
                    BehaviorType behaviorType = BehaviorType.valueOf(
                            fields.get(3).trim().toUpperCase(Locale.ROOT));
                    BehaviorStyle behavior = BehaviorStyleFactory.create(behaviorType);
                    double mood = Double.parseDouble(fields.get(4).trim());
                    boolean blacklisted = strictBoolean(fields.get(5));
                    double pokerParticipation = Double.parseDouble(fields.get(6).trim());
                    double pokerAggression = Double.parseDouble(fields.get(7).trim());
                    double pokerSkill = Double.parseDouble(fields.get(8).trim());

                    if(balance < 0 || mood < -1 || mood > 1){
                        throw new IllegalArgumentException(
                                "balance cannot be negative and mood must be between -1 and 1");
                    }

                    casino.addPlayer(name, balance,
                            bankrollMenuCode(bankroll), behavior);
                    Player player = casino.getPlayers().get(casino.getPlayers().size() - 1);
                    player.setMood(mood);
                    player.setBlacklisted(blacklisted);
                    player.setPokerParticipation(pokerParticipation);
                    player.setPokerAggression(pokerAggression);
                    player.setPokerSkill(pokerSkill);
                    initialStates.put(player, new InitialPlayerState(balance));
                }
                catch(IllegalArgumentException exception){
                    throw new IOException("Invalid player file line " + lineNumber
                            + ": " + exception.getMessage(), exception);
                }
            }
        }

        if(casino.getPlayers().isEmpty()){
            throw new IOException("Player file contains no players: " + playersPath);
        }
    }

    private static List<String> parseCsvLine(String line) throws IOException {
        ArrayList<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for(int i = 0; i < line.length(); i++){
            char character = line.charAt(i);
            if(character == '"'){
                if(quoted && i + 1 < line.length() && line.charAt(i + 1) == '"'){
                    field.append('"');
                    i++;
                }
                else{
                    quoted = !quoted;
                }
            }
            else if(character == ',' && !quoted){
                fields.add(field.toString().trim());
                field.setLength(0);
            }
            else{
                field.append(character);
            }
        }
        if(quoted){
            throw new IOException("Player file contains an unterminated quoted field");
        }
        fields.add(field.toString().trim());
        return fields;
    }

    private static String required(String value, String name){
        String trimmed = value.trim();
        if(trimmed.isEmpty()){
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return trimmed;
    }

    private static boolean strictBoolean(String value){
        String normalized = value.trim();
        if("true".equalsIgnoreCase(normalized)) return true;
        if("false".equalsIgnoreCase(normalized)) return false;
        throw new IllegalArgumentException("blacklisted must be true or false");
    }

    private static int bankrollMenuCode(BankrollStyle bankroll){
        switch(bankroll){
            case HIGH_ROLLER: return 0;
            case EVEN_STEVEN: return 1;
            case LOW_ROLLER: return 2;
            default: throw new IllegalArgumentException("Unknown bankroll style: " + bankroll);
        }
    }

    private static void closeActiveSessions(Casino casino){
        for(Player player : casino.getPlayers()){
            if(player.getCurrentSession() != null){
                player.leaveTable();
            }
        }
    }

    private static int choosePlayerType(Random random, SimulationConfig config){
        int totalWeight = config.highRollerWeight + config.evenStevenWeight + config.lowRollerWeight;
        int choice = random.nextInt(totalWeight);
        if(choice < config.highRollerWeight){
            return 0;
        }
        if(choice < config.highRollerWeight + config.evenStevenWeight){
            return 1;
        }
        return 2;
    }

    private static BehaviorStyle chooseBehaviorStyle(Random random,
                                                      BankrollStyle bankrollType){
        int roll = random.nextInt(100);
        if(bankrollType == BankrollStyle.HIGH_ROLLER){
            if(roll < 45) return new WhaleStyle();
            if(roll < 75) return new GrinderStyle();
            return new ObnoxiousStyle();
        }
        if(bankrollType == BankrollStyle.EVEN_STEVEN){
            if(roll < 50) return new GrinderStyle();
            if(roll < 75) return new LowStakesStyle();
            return new ObnoxiousStyle();
        }
        return roll < 70 ? new LowStakesStyle() : new ObnoxiousStyle();
    }

    private static double jitterTrait(Random random, double baseline){
        return Math.max(0, Math.min(1, baseline + random.nextDouble() * 0.10 - 0.05));
    }

    private static void generateStaff(Casino casino, SimulationConfig config){
        for(int i = 0; i < config.securityStaff; i++){
            casino.addStaff("Security-" + (i + 1), 0);
        }
        for(int i = 0; i < config.floorManagers; i++){
            casino.addStaff("FloorManager-" + (i + 1), 1);
        }
    }

    private static void generateTables(Casino casino, SimulationConfig config){
        for(int i = 0; i < config.blackjackTables; i++){
            casino.addGame(config.blackjackSeats, config.blackjackMinBet, config.blackjackMaxBet, 0);
        }
        for(int i = 0; i < config.rouletteTables; i++){
            int id = casino.getGameCnt();
            casino.addGame(new Roulette(id, 0, config.rouletteSeats, config.rouletteMinBet,
                    config.rouletteMaxBet, config.rouletteHighPayoutMaxBet));
        }
        for(int i = 0; i < config.crapsTables; i++){
            casino.addGame(config.crapsSeats, config.crapsMinBet, config.crapsMaxBet, 3);
        }
        for(int i = 0; i < config.pokerTables; i++){
            int id = casino.getGameCnt();
            casino.addGame(new Poker(id, config.pokerSeats, config.pokerMinBet,
                    config.pokerMaxBet, casino.getControls().getPokerRakeRate(),
                    casino.getControls().getPokerRakeCap(), config.seed + id));
        }
        for(int i = 0; i < config.slotMachines; i++){
            int id = casino.getGameCnt();
            casino.addGame(new Slots(id, config.slotBet,
                    casino.getControls().getSlotRtp()));
        }
    }

    private static void writeSummary(Path path, Casino casino, SimulationConfig config,
                                     Map<Player, InitialPlayerState> initialStates) throws IOException {
        int startingBalance = 0;
        int endingBalance = 0;
        int profitablePlayers = 0;
        int brokePlayers = 0;
        int playersStillGambling = 0;
        int voluntaryExits = 0;
        int casinoRemovals = 0;

        for(Player player : casino.getPlayers()){
            InitialPlayerState initial = initialStates.get(player);
            startingBalance += initial.balance;
            endingBalance += player.getBalance();
            if(sessionWinnings(player) > 0){
                profitablePlayers++;
            }
            if(player.getBalance() == 0){
                brokePlayers++;
            }
            if(player.isInCasino()){
                playersStillGambling++;
            }
            else if(player.getVisitState().wasRemovedByCasino()){
                casinoRemovals++;
            }
            else{
                voluntaryExits++;
            }
        }

        try(BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)){
            writer.write("CASINO BATCH SIMULATION RESULTS");
            writer.newLine();
            writer.write("================================");
            writer.newLine();
            writeLine(writer, "Generation seed", config.seed);
            writeLine(writer, "Rounds", config.rounds);
            writeLine(writer, "Players", casino.getPlayers().size());
            writeLine(writer, "Staff", casino.getStaffs().size());
            writeLine(writer, "Tables/machines", casino.getCasinoFloor().size());
            writeLine(writer, "Play sessions", casino.getPlayRecords().size());
            writeLine(writer, "Starting player balance ($)", startingBalance);
            writeLine(writer, "Ending player balance ($)", endingBalance);
            writeLine(writer, "Casino table profit ($)", (int)casino.getRevenue());
            writeLine(writer, "Poker rake rate", decimal(casino.getControls().getPokerRakeRate()));
            writeLine(writer, "Poker rake cap ($)", casino.getControls().getPokerRakeCap());
            writeLine(writer, "Slot target RTP", decimal(casino.getControls().getSlotRtp()));
            writeLine(writer, "Player profit removal limit ($)", casino.getControls().getPlayerProfitLimit());
            writeLine(writer, "Players with net winnings", profitablePlayers);
            writeLine(writer, "Players with zero balance", brokePlayers);
            writeLine(writer, "Players still in casino", playersStillGambling);
            writeLine(writer, "Players who chose to leave", voluntaryExits);
            writeLine(writer, "Players removed at profit limit", casinoRemovals);
        }
    }

    private static void writePlayers(Path path, Casino casino,
                                     Map<Player, InitialPlayerState> initialStates) throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)){
            writer.write("id,name,bankrollType,behaviorStyle,socialSensitivity,winSensitivity,lossSensitivity,startingBalance,endingBalance,netProfit,mood,momentum,winStreak,lossStreak,socialSurrounding,socialScore,tilt,effectivePokerSkill,inCasino,removedByCasino,visitTicks,roundsAtTable,roundsPlayed,tableSwitches,blacklisted,pokerParticipation,pokerAggression,pokerSkill,sessions,netSessionWinnings,wins,losses");
            writer.newLine();
            for(Player player : casino.getPlayers()){
                InitialPlayerState initial = initialStates.get(player);
                int wins = 0;
                int losses = 0;
                for(PlaySessionRecord record : player.getPlayHistory()){
                    wins += record.getTableWins();
                    losses += record.getTableLosses();
                }
                writer.write(player.getId() + "," + csv(player.getName()) + ","
                        + player.getBankrollStyle().getDisplayName() + ","
                        + player.getBehaviorStyle().getType().name() + ","
                        + decimal(player.getProfile().getSocialSensitivity()) + ","
                        + decimal(player.getProfile().getWinSensitivity()) + ","
                        + decimal(player.getProfile().getLossSensitivity()) + ","
                        + initial.balance + ","
                        + player.getBalance() + ","
                        + (player.getBalance() - initial.balance) + ","
                        + decimal(player.getMood()) + ","
                        + decimal(player.getEmotionalStatus().getMomentum()) + ","
                        + player.getFactualStatus().getWinStreak() + ","
                        + player.getFactualStatus().getLossStreak() + ","
                        + decimal(player.getFactualStatus().getSocialSurrounding()) + ","
                        + decimal(player.getEmotionalStatus().getSocialScore()) + ","
                        + decimal(player.getTiltLevel()) + ","
                        + decimal(player.getEffectivePokerSkill()) + ","
                        + player.isInCasino() + ","
                        + player.getVisitState().wasRemovedByCasino() + ","
                        + player.getVisitState().getTicksInCasino() + ","
                        + player.getRoundsAtTable() + ","
                        + player.getRoundsPlayed() + ","
                        + player.getVisitState().getTableSwitches() + ","
                        + player.isBlacklisted() + "," + decimal(player.getPokerParticipation()) + ","
                        + decimal(player.getPokerAggression()) + "," + decimal(player.getPokerSkill()) + ","
                        + player.getPlayHistory().size() + ","
                        + sessionWinnings(player) + "," + wins + "," + losses);
                writer.newLine();
            }
        }
    }

    private static void writeTables(Path path, Casino casino) throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)){
            writer.write("id,type,capacity,minBet,maxBet,profit,sessions,pokerRakeRate,pokerRakeCap,slotRtp");
            writer.newLine();
            for(Game game : casino.getCasinoFloor()){
                writer.write(game.getId() + "," + game.getClass().getSimpleName() + ","
                        + game.getMaxTablePopulation() + "," + game.getMinBet() + ","
                        + game.getMaxBet() + "," + game.getProfit() + "," + game.getNumSessions() + ","
                        + (game instanceof Poker ? decimal(((Poker)game).getRakeRate()) : "") + ","
                        + (game instanceof Poker ? ((Poker)game).getRakeCap() : "") + ","
                        + (game instanceof Slots ? decimal(((Slots)game).getTargetRtp()) : ""));
                writer.newLine();
            }
        }
    }

    private static void writeSessions(Path path, Casino casino) throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)){
            writer.write("playerId,playerName,gameId,gameType,startingBalance,endingBalance,netProfit,totalWagered,roi,roundsAtTable,roundsPlayed,momentum,winStreak,lossStreak,wins,losses,winRate");
            writer.newLine();
            for(PlaySessionRecord record : casino.getPlayRecords()){
                int decisions = record.getTableWins() + record.getTableLosses();
                double winRate = decisions == 0 ? 0.0 : (double)record.getTableWins() / decisions;
                writer.write(record.getPlayer().getId() + "," + csv(record.getPlayer().getName()) + ","
                        + record.getGame().getId() + "," + record.getGame().getClass().getSimpleName() + ","
                        + record.getStartingBalance() + "," + record.getEndingBalance() + ","
                        + record.getTotalWinnings() + "," + record.getTotalWagered() + ","
                        + decimal(record.getReturnOnWagers()) + ","
                        + record.getRoundsAtTable() + "," + record.getRoundsPlayed() + ","
                        + decimal(record.getMomentum()) + "," + record.getWinStreak() + ","
                        + record.getLossStreak() + "," + record.getTableWins() + ","
                        + record.getTableLosses() + "," + decimal(winRate));
                writer.newLine();
            }
        }
    }

    private static int sessionWinnings(Player player){
        int total = 0;
        for(PlaySessionRecord record : player.getPlayHistory()){
            total += record.getTotalWinnings();
        }
        return total;
    }

    private static int randomInt(Random random, int minimum, int maximum){
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static double randomDouble(Random random, double minimum, double maximum){
        return minimum + random.nextDouble() * (maximum - minimum);
    }

    private static String csv(String value){
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String decimal(double value){
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void writeLine(BufferedWriter writer, String label, Object value) throws IOException {
        writer.write(label + ": " + value);
        writer.newLine();
    }

    private static final class InitialPlayerState {
        private final int balance;

        private InitialPlayerState(int balance){
            this.balance = balance;
        }
    }

    private static final class SimulationConfig {
        private long seed;
        private int rounds;
        private int players;
        private int minBalance;
        private int maxBalance;
        private double minMood;
        private double maxMood;
        private int minYearStarted;
        private double blacklistedPercent;
        private int highRollerWeight;
        private int evenStevenWeight;
        private int lowRollerWeight;
        private int blackjackTables;
        private int blackjackSeats;
        private int blackjackMinBet;
        private int blackjackMaxBet;
        private int rouletteTables;
        private int rouletteSeats;
        private int rouletteMinBet;
        private int rouletteMaxBet;
        private int rouletteHighPayoutMaxBet;
        private int crapsTables;
        private int crapsSeats;
        private int crapsMinBet;
        private int crapsMaxBet;
        private int pokerTables;
        private int pokerSeats;
        private int pokerMinBet;
        private int pokerMaxBet;
        private double pokerRakeRate;
        private int pokerRakeCap;
        private int slotMachines;
        private int slotBet;
        private double slotRtp;
        private int playerProfitLimit;
        private int securityStaff;
        private int floorManagers;
        private String playersFile;
        private String outputDirectory;
        private TableAssignmentPolicy tableAssignmentPolicy;
        private boolean rlBridgeEnabled;
        private String rlStateDirectory;
        private long rlPollIntervalMillis;
        private long rlTimeoutMillis;
        private int rlExchangeIntervalRounds;

        private static SimulationConfig load(Path path) throws IOException {
            if(!Files.isRegularFile(path)){
                throw new IOException("Scenario file not found: " + path);
            }

            Properties properties = new Properties();
            try(BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)){
                properties.load(reader);
            }

            SimulationConfig config = new SimulationConfig();
            config.seed = longValue(properties, "seed", System.currentTimeMillis());
            config.rounds = intValue(properties, "rounds", 500);
            config.players = intValue(properties, "players", 100);
            config.minBalance = intValue(properties, "minBalance", 100);
            config.maxBalance = intValue(properties, "maxBalance", 1000);
            config.minMood = doubleValue(properties, "minMood", -0.25);
            config.maxMood = doubleValue(properties, "maxMood", 0.50);
            config.minYearStarted = intValue(properties, "minYearStarted", 2015);
            config.blacklistedPercent = doubleValue(properties, "blacklistedPercent", 2.0);
            config.highRollerWeight = intValue(properties, "highRollerWeight", 20);
            config.evenStevenWeight = intValue(properties, "evenStevenWeight", 50);
            config.lowRollerWeight = intValue(properties, "lowRollerWeight", 30);
            config.blackjackTables = intValue(properties, "blackjackTables", 4);
            config.blackjackSeats = intValue(properties, "blackjackSeats", 5);
            config.blackjackMinBet = intValue(properties, "blackjackMinBet", 10);
            config.blackjackMaxBet = intValue(properties, "blackjackMaxBet", 100);
            config.rouletteTables = intValue(properties, "rouletteTables", 3);
            config.rouletteSeats = intValue(properties, "rouletteSeats", 6);
            config.rouletteMinBet = intValue(properties, "rouletteMinBet", 5);
            config.rouletteMaxBet = intValue(properties, "rouletteMaxBet", 100);
            config.rouletteHighPayoutMaxBet = intValue(properties,
                    "rouletteHighPayoutMaxBet", config.rouletteMaxBet);
            config.crapsTables = intValue(properties, "crapsTables", 2);
            config.crapsSeats = intValue(properties, "crapsSeats", 8);
            config.crapsMinBet = intValue(properties, "crapsMinBet", 5);
            config.crapsMaxBet = intValue(properties, "crapsMaxBet", 100);
            config.pokerTables = intValue(properties, "pokerTables", 2);
            config.pokerSeats = intValue(properties, "pokerSeats", 8);
            config.pokerMinBet = intValue(properties, "pokerMinBet", 5);
            config.pokerMaxBet = intValue(properties, "pokerMaxBet", 100);
            config.pokerRakeRate = doubleValue(properties, "pokerRakeRate", 0.05);
            config.pokerRakeCap = intValue(properties, "pokerRakeCap", 100);
            config.slotMachines = intValue(properties, "slotMachines", 10);
            config.slotBet = intValue(properties, "slotBet", 5);
            config.slotRtp = doubleValue(properties, "slotRtp", 0.90);
            config.playerProfitLimit = intValue(properties, "playerProfitLimit", 0);
            config.securityStaff = intValue(properties, "securityStaff", 2);
            config.floorManagers = intValue(properties, "floorManagers", 2);
            config.playersFile = properties.getProperty("playersFile", "").trim();
            config.outputDirectory = properties.getProperty("outputDirectory", "BatchResults").trim();
            config.tableAssignmentPolicy = TableAssignmentPolicy.valueOf(
                    properties.getProperty("tableAssignmentPolicy", "PREFERENCE_WEIGHTED")
                            .trim().toUpperCase(Locale.ROOT));
            config.rlBridgeEnabled = booleanValue(properties, "rlBridgeEnabled", false);
            config.rlStateDirectory = properties.getProperty("rlStateDirectory", "rl-exchange").trim();
            config.rlPollIntervalMillis = longValue(properties, "rlPollIntervalMillis", 50);
            config.rlTimeoutMillis = longValue(properties, "rlTimeoutMillis", 30000);
            config.rlExchangeIntervalRounds = intValue(properties, "rlExchangeIntervalRounds", 1);
            config.validate();
            return config;
        }

        private Path resolveOutputDirectory(Path scenarioPath){
            Path output = Paths.get(outputDirectory);
            if(output.isAbsolute()){
                return output.normalize();
            }
            Path parent = scenarioPath.getParent();
            return (parent == null ? output : parent.resolve(output)).normalize();
        }

        private Path resolveRlStateDirectory(Path scenarioPath){
            Path stateDir = Paths.get(rlStateDirectory);
            if(stateDir.isAbsolute()){
                return stateDir.normalize();
            }
            Path parent = scenarioPath.getParent();
            return (parent == null ? stateDir : parent.resolve(stateDir)).normalize();
        }

        private boolean hasPlayersFile(){
            return !playersFile.isEmpty();
        }

        private Path resolvePlayersFile(Path scenarioPath){
            Path input = Paths.get(playersFile);
            if(input.isAbsolute()){
                return input.normalize();
            }
            Path parent = scenarioPath.getParent();
            return (parent == null ? input : parent.resolve(input)).normalize();
        }

        private void validate(){
            require(rounds > 0, "rounds must be greater than zero");
            require(hasPlayersFile() || players > 0,
                    "players must be greater than zero when playersFile is not set");
            require(minBalance > 0 && maxBalance >= minBalance, "balance range is invalid");
            require(minMood >= -1 && maxMood <= 1 && maxMood >= minMood, "mood range must be within -1 to 1");
            require(minYearStarted <= Casino.CURR_YEAR, "minYearStarted cannot be after the casino year");
            require(blacklistedPercent >= 0 && blacklistedPercent <= 100, "blacklistedPercent must be 0 to 100");
            require(highRollerWeight >= 0 && evenStevenWeight >= 0 && lowRollerWeight >= 0
                    && highRollerWeight + evenStevenWeight + lowRollerWeight > 0,
                    "player type weights must include at least one positive value");
            require(blackjackTables >= 0 && rouletteTables >= 0 && crapsTables >= 0
                            && pokerTables >= 0 && slotMachines >= 0
                    && blackjackTables + rouletteTables + crapsTables + pokerTables + slotMachines > 0,
                    "at least one table or slot machine is required");
            require(blackjackSeats > 0 && blackjackMinBet > 0 && blackjackMaxBet >= blackjackMinBet,
                    "blackjack settings are invalid");
            require(rouletteSeats > 0 && rouletteMinBet > 0 && rouletteMaxBet >= rouletteMinBet,
                    "roulette settings are invalid");
            require(rouletteHighPayoutMaxBet >= rouletteMinBet
                            && rouletteHighPayoutMaxBet <= rouletteMaxBet,
                    "rouletteHighPayoutMaxBet must be between rouletteMinBet and rouletteMaxBet");
            require(crapsSeats > 0 && crapsMinBet > 0 && crapsMaxBet >= crapsMinBet,
                    "craps settings are invalid");
            require(pokerSeats > 1 && pokerMinBet > 0 && pokerMaxBet >= pokerMinBet,
                    "poker settings are invalid");
            require(pokerRakeRate >= 0 && pokerRakeRate <= 1 && pokerRakeCap >= 0,
                    "poker rake settings are invalid");
            require(slotBet > 0, "slotBet must be greater than zero");
            require(slotRtp >= 0 && slotRtp <= 1, "slotRtp must be between 0 and 1");
            require(playerProfitLimit >= 0, "playerProfitLimit cannot be negative");
            require(securityStaff >= 0 && floorManagers >= 0, "staff counts cannot be negative");
            require(!outputDirectory.isEmpty(), "outputDirectory cannot be empty");
            if(rlBridgeEnabled){
                require(!rlStateDirectory.isEmpty(), "rlStateDirectory cannot be empty");
                require(rlPollIntervalMillis > 0, "rlPollIntervalMillis must be greater than zero");
                require(rlTimeoutMillis > 0, "rlTimeoutMillis must be greater than zero");
                require(rlExchangeIntervalRounds > 0, "rlExchangeIntervalRounds must be greater than zero");
            }
        }

        private static void require(boolean condition, String message){
            if(!condition){
                throw new IllegalArgumentException(message);
            }
        }

        private static int intValue(Properties properties, String key, int defaultValue){
            return Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)).trim());
        }

        private static long longValue(Properties properties, String key, long defaultValue){
            return Long.parseLong(properties.getProperty(key, Long.toString(defaultValue)).trim());
        }

        private static double doubleValue(Properties properties, String key, double defaultValue){
            return Double.parseDouble(properties.getProperty(key, Double.toString(defaultValue)).trim());
        }

        private static boolean booleanValue(Properties properties, String key, boolean defaultValue){
            return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defaultValue)).trim());
        }
    }
}
