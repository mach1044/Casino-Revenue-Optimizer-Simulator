/*                                                                          *
* Casino.java                                                               *
* ------------------------------------------------------------------------- *
 * Max Chen                                                                 *
* 2025-01-10                                                                *
* ------------------------------------------------------------------------- *
* Class where casino is being run                                           *
*                                                                           */

import java.io.*;
import java.util.ArrayList;

public class Casino {
    private static final String SENSITIVITY_FORMAT_MARKER = "SENSITIVITY_V2";
    private static final String SESSION_RECORDS_FORMAT_MARKER = "SESSION_RECORDS_V2";
    private static final String BALANCE_ONLY_FORMAT_MARKER = "BALANCE_ONLY_V3";

    public static final int CURR_YEAR = 2025;
    private static final String CONFIG_PATH = ".config.txt";

    private double revenue;
    private ArrayList<Player> players;
    private int playerCnt;
    private ArrayList<Staff> staffs;
    private int staffCnt;
    private ArrayList<Game> casinoFloor;
    private int gameCnt;
    private ArrayList<PlaySessionRecord> playRecords;
    private int recordCnt;
    private int simCode;
    private int lastSimCodeUsed;
    private boolean simChanged;
    private CasinoControls controls = CasinoControls.defaults();

    //Constructor for Casino
    public Casino(){
        //initialize arrays to hold previous players, staff, game, and playSession records
        players = new ArrayList<Player>();
        staffs = new ArrayList<Staff>();
        casinoFloor = new ArrayList<Game>();
        playRecords = new ArrayList<PlaySessionRecord>();
        try{
            BufferedReader in = new BufferedReader(new FileReader(CONFIG_PATH));
            lastSimCodeUsed = Integer.parseInt(in.readLine());
            in.close();
        }
        catch(IOException e){
            System.out.println("Error reading config file" + e);
        }      
        simCode = lastSimCodeUsed;
    }

    // Getters and Setters for revenue
    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    public CasinoControls getControls(){ return controls; }

    public void setControls(CasinoControls controls){
        if(controls == null){
            throw new IllegalArgumentException("Casino controls cannot be null");
        }
        this.controls = controls;
    }

    //Getters and Setters for player information
    public ArrayList<Player> getPlayers() {
        return players;
    }

    public void addPlayer(String name, int balance, int type){
        BankrollStyle bankroll = BankrollStyle.fromMenuCode(type);
        addPlayer(name, balance,
                PlayerProfile.defaultProfile(bankroll));
    }

    public void addPlayer(String name, int balance, int type,
                          BehaviorStyle behavior){
        BankrollStyle bankroll = BankrollStyle.fromMenuCode(type);
        addPlayer(name, balance,
                PlayerProfile.forStyles(bankroll, behavior));
    }

    public void addPlayer(String name, int balance,
                          PlayerProfile profile){
        int id = playerCnt;
        players.add(new Player(id, name, balance, profile));
        playerCnt++;
        simChanged = true;
    }
    
    public void addPlayer(Player player){
        players.add(player);
        simChanged = true;
    }

    public void setPlayers(ArrayList<Player> players) {
        this.players = players;
    }
    
    public int getPlayerCnt() {
        return playerCnt;
    }

    public void setPlayerCnt(int playerCnt) {
        this.playerCnt = playerCnt;
    }

    private BankrollStyle parseSavedBankroll(String value){
        try{
            return BankrollStyle.valueOf(value);
        }
        catch(IllegalArgumentException exception){
            // Compatibility with the previous save format: 0=High,
            // 1=Low, and 2=Even.
            int legacyType = Integer.parseInt(value);
            if(legacyType == 0) return BankrollStyle.HIGH_ROLLER;
            if(legacyType == 1) return BankrollStyle.LOW_ROLLER;
            if(legacyType == 2) return BankrollStyle.EVEN_STEVEN;
            throw new IllegalArgumentException("Unknown saved bankroll style: " + value);
        }
    }

    //Getters and setters for staff information
    public ArrayList<Staff> getStaffs() {
        return staffs;
    }

    public void addStaff(String name, int type){
        staffCnt++;
        switch(type){
            case 0:
                staffs.add(new Security(name));
                break;
            case 1:
                staffs.add(new FloorManager(name));
                break;
            default:
            break;    
        }
        simChanged = true;
    }

    public void addStaff(Staff staff){
        staffs.add(staff);
        simChanged = true;
    }

    public void setStaffs(ArrayList<Staff> staffs) {
        this.staffs = staffs;
    }

    public int getStaffCnt() {
        return staffCnt;
    }

    public void setStaffCnt(int staffCnt) {
        this.staffCnt = staffCnt;
    }

    //Getters and setters for game information
    public ArrayList<Game> getCasinoFloor() {
        return casinoFloor;
    }

    public void setCasinoFloor(ArrayList<Game> casinoFloor) {
        this.casinoFloor = casinoFloor;
    }

    public void addGame(int tablePop, int minBet, int maxBet, int type){
        int id = gameCnt++;
        switch(type){
            case 0:
                casinoFloor.add(new Blackjack(id, tablePop, minBet, maxBet));
                break;
            case 1:
                casinoFloor.add(new Roulette(id, tablePop, minBet, maxBet));
                break;
            case 2:
                casinoFloor.add(new Slots(id, minBet));
                break;
            case 3:
                casinoFloor.add(new Craps(id, tablePop, minBet, maxBet));
                break;
            case 4:
                casinoFloor.add(new Poker(id, tablePop, minBet, maxBet));
                break;
            default:
            break;    
        }
        simChanged = true;
    }
    
    public void addGame(Game game){
        casinoFloor.add(game);
        gameCnt++;
        simChanged = true;
    }

    public int getGameCnt() {
        return gameCnt;
    }

    public void setGameCnt(int gameCnt) {
        this.gameCnt = gameCnt;
    }

    public ArrayList<PlaySessionRecord> getPlayRecords() {
        return playRecords;
    }

    public void setPlayRecords(ArrayList<PlaySessionRecord> playRecords) {
        this.playRecords = playRecords;
    }
    public void addRecord(PlaySessionRecord record){
        if(record != null){
            playRecords.add(record);
            recordCnt++;
        }
    }

    public int getRecordCnt() {
        return recordCnt;
    }

    public void setRecordCnt(int recordCnt) {
        this.recordCnt = recordCnt;
    }

    /*method to load simulation from textfile
     * 
     * Parameters: path - file that contains the game
     */
    public boolean loadSimFromFile(String path){
        try{
            BufferedReader in = new BufferedReader(new FileReader(path));

            simCode = Integer.parseInt(in.readLine());
            simChanged = false;
            
            players.clear();
            playerCnt = 0;
            staffs.clear();
            staffCnt = 0;
            casinoFloor.clear();
            gameCnt = 0;
            playRecords.clear();
            recordCnt = 0;

            String playerCountLine = in.readLine();
            boolean balanceOnlyFormat = BALANCE_ONLY_FORMAT_MARKER.equals(playerCountLine);
            playerCnt = Integer.parseInt(balanceOnlyFormat ? in.readLine() : playerCountLine);
            for(int i = 0; i < playerCnt; i++){
                // can be highroller, lowroller, or even steven (0, 1, 2)
                BankrollStyle bankrollStyle = parseSavedBankroll(in.readLine());
                // name
                int id = Integer.parseInt(in.readLine());
                String name = in.readLine();
                int balance;
                if(balanceOnlyFormat){
                    balance = Integer.parseInt(in.readLine());
                }
                else{
                    // Legacy saves stored unused cash followed by the active chip stack.
                    in.readLine();
                    balance = Integer.parseInt(in.readLine());
                }
                int profit = Integer.parseInt(in.readLine());
                int yearStarted = Integer.parseInt(in.readLine());
                double mood = Double.parseDouble(in.readLine());
                // if 1 then user is banned, if 0, user is not banned
                int blacklisted = Integer.parseInt(in.readLine());
                boolean bl;
                if(blacklisted == 1){
                    bl = true;
                }
                else{
                    bl = false;
                }
                BehaviorStyle behavior = BehaviorStyleFactory.create(
                        BehaviorType.valueOf(in.readLine()));
                double socialSensitivity = Double.parseDouble(in.readLine());
                String sensitivityLine = in.readLine();
                double winSensitivity;
                double lossSensitivity;
                if(SENSITIVITY_FORMAT_MARKER.equals(sensitivityLine)){
                    winSensitivity = Double.parseDouble(in.readLine());
                    lossSensitivity = Double.parseDouble(in.readLine());
                }
                else{
                    // Legacy saves stored one value; preserve their behavior by
                    // applying it equally to wins and losses.
                    winSensitivity = Double.parseDouble(sensitivityLine);
                    lossSensitivity = winSensitivity;
                }
                double pokerParticipation = Double.parseDouble(in.readLine());
                double pokerAggression = Double.parseDouble(in.readLine());
                double pokerSkill = Double.parseDouble(in.readLine());

                PlayerProfile profile = new PlayerProfile(bankrollStyle, behavior,
                        socialSensitivity, winSensitivity, lossSensitivity);
                players.add(new Player(id, name, balance, profit,
                        yearStarted, mood, bl, profile));

                if(!players.isEmpty()){
                    Player loadedPlayer = players.get(players.size() - 1);
                    loadedPlayer.setPokerParticipation(pokerParticipation);
                    loadedPlayer.setPokerAggression(pokerAggression);
                    loadedPlayer.setPokerSkill(pokerSkill);
                }

            }

            //now read staff
            staffCnt = Integer.parseInt(in.readLine());
            for(int i = 0; i < staffCnt; i++){
                // type 0 is security, type 1 is Floor manager
                int type = Integer.parseInt(in.readLine());
                if(type == 0){
                    String name = in.readLine();
                    int blacklistCount = Integer.parseInt(in.readLine());
                    int kickCount = Integer.parseInt(in.readLine());
                    staffs.add(new Security(name, blacklistCount, kickCount));
                }
                else if(type == 1){
                    String name = in.readLine();
                    int blacklistCount = Integer.parseInt(in.readLine());
                    staffs.add(new FloorManager(name, blacklistCount));
                }
            }

            // 0 is blackjack, 1 is Roulette, 2 is slots
            gameCnt = Integer.parseInt(in.readLine());
            for(int i = 0; i < gameCnt; i++){
                int type = Integer.parseInt(in.readLine());
                int id = Integer.parseInt(in.readLine());
                int profit = Integer.parseInt(in.readLine());
                int maxTablePopulation = Integer.parseInt(in.readLine());
                int minBet = Integer.parseInt(in.readLine());
                int maxBet = Integer.parseInt(in.readLine());

                //initialize object based on type
                switch(type){
                    case 0:
                        casinoFloor.add(new Blackjack(id, profit, maxTablePopulation, minBet, maxBet));
                        break;
                    case 1:
                        casinoFloor.add(new Roulette(id, profit, maxTablePopulation, minBet, maxBet));
                        break;
                    case 2:
                        casinoFloor.add(new Slots(id, profit, minBet));
                        break;
                    case 3:
                        casinoFloor.add(new Craps(id, profit, maxTablePopulation, minBet, maxBet));
                        break;
                    case 4:
                        casinoFloor.add(new Poker(id, profit, maxTablePopulation, minBet, maxBet));
                        break;
                    default:
                    break;    

                }
            }
            loadEmbeddedRecords(in);
            in.close();
            return true;
        }
        catch(IOException | RuntimeException e){
            System.out.println("Error reading file " + e);
            return false;
        }

    }

    /** Loads session history appended by the current save format. */
    private void loadEmbeddedRecords(BufferedReader in) throws IOException {
        String marker = in.readLine();
        if(marker == null){
            return;
        }
        if(!SESSION_RECORDS_FORMAT_MARKER.equals(marker)){
            throw new IOException("Unsupported session record format: " + marker);
        }

        int savedRecordCount = Integer.parseInt(in.readLine());
        for(int i = 0; i < savedRecordCount; i++){
            Player player = searchPlayer(Integer.parseInt(in.readLine()));
            Game game = searchGame(Integer.parseInt(in.readLine()));
            if(player == null || game == null){
                throw new IOException("Session record references an unknown player or game");
            }

            PlaySessionRecord record = new PlaySessionRecord(
                    player,
                    game,
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Long.parseLong(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Long.parseLong(in.readLine()),
                    Double.parseDouble(in.readLine()),
                    Integer.parseInt(in.readLine()),
                    Integer.parseInt(in.readLine()));
            addRecord(record);
            player.addRecord(record);
            game.addRecord(record);
        }
    }

    //method to save simulation to a textfile
    public boolean saveSimToFile(String path){
        try{
            File saveFile = new File(path);
            File parent = saveFile.getParentFile();
            if(parent != null && !parent.exists() && !parent.mkdirs()){
                throw new IOException("Could not create save folder: " + parent);
            }

            BufferedWriter config = new BufferedWriter(new FileWriter(CONFIG_PATH,false));

            if(simChanged){
                lastSimCodeUsed++;
                simCode = lastSimCodeUsed;
            }
            config.write(lastSimCodeUsed + "\n");
            config.close();
            
            BufferedWriter out = new BufferedWriter(new FileWriter(saveFile));
            
            out.write(Integer.toString(simCode)+"\n");
            
            out.write(BALANCE_ONLY_FORMAT_MARKER + "\n");
            out.write(playerCnt + "\n");
            for(int i = 0; i < playerCnt; i++){
                Player temp = players.get(i);
                out.write(temp.getBankrollStyle().name() + "\n");
                out.write(temp.getId() + "\n");
                out.write(temp.getName() + "\n");
                out.write(temp.getBalance() + "\n");
                out.write(temp.getProfit() + "\n");
                out.write(temp.getYearStarted() + "\n");
                out.write(temp.getMood() + "\n");
                if(temp.isBlacklisted()){
                    out.write("1\n");
                }
                else{
                    out.write("0\n");
                }
                out.write(temp.getBehaviorStyle().getType().name() + "\n");
                out.write(temp.getProfile().getSocialSensitivity() + "\n");
                out.write(SENSITIVITY_FORMAT_MARKER + "\n");
                out.write(temp.getProfile().getWinSensitivity() + "\n");
                out.write(temp.getProfile().getLossSensitivity() + "\n");
                out.write(temp.getPokerParticipation() + "\n");
                out.write(temp.getPokerAggression() + "\n");
                out.write(temp.getPokerSkill() + "\n");
            }

            out.write(Integer.toString(staffCnt) + "\n");
            for(int i = 0; i < staffCnt; i++){
                Staff temp = staffs.get(i);
                if(temp instanceof Security){
                    out.write("0\n");
                    out.write(temp.getName() + "\n");
                    out.write(temp.getBlacklistCount() + "\n");
                    out.write(((Security)temp).getKickCount() + "\n");
                }
                else if(temp instanceof FloorManager){
                    out.write("1\n");
                    out.write(temp.getName() + "\n");
                    out.write(temp.getBlacklistCount() + "\n");
                }
            }

            out.write(gameCnt + "\n");
            for(int i = 0; i < gameCnt; i++){
                Game temp = casinoFloor.get(i);
                if(temp instanceof Blackjack){
                    out.write("0\n");
                }
                else if(temp instanceof Roulette){
                    out.write("1\n");
                }
                else if(temp instanceof Slots){
                    out.write("2\n");
                }
                else if(temp instanceof Craps){
                    out.write("3\n");
                }
                else if(temp instanceof Poker){
                    out.write("4\n");
                }
                out.write(temp.getId() + "\n");
                out.write(temp.getProfit() + "\n");
                out.write(temp.getMaxTablePopulation() + "\n");
                out.write(temp.getMinBet() + "\n");
                out.write(temp.getMaxBet() + "\n");
            }

            saveEmbeddedRecords(out);

            out.close();
            simChanged = false;
            return true;
        }
        catch(IOException e){
            System.out.println("Error writing to file " + e);
            return false;
        }
    }

    /** Appends completed session results to the same simulation save file. */
    private void saveEmbeddedRecords(BufferedWriter out) throws IOException {
        out.write(SESSION_RECORDS_FORMAT_MARKER + "\n");
        out.write(playRecords.size() + "\n");
        for(PlaySessionRecord record : playRecords){
            out.write(record.getPlayer().getId() + "\n");
            out.write(record.getGame().getId() + "\n");
            out.write(record.getTotalWinnings() + "\n");
            out.write(record.getTableWins() + "\n");
            out.write(record.getTableLosses() + "\n");
            out.write(record.getTotalWagered() + "\n");
            out.write(record.getRoundsPlayed() + "\n");
            out.write(record.getRoundsAtTable() + "\n");
            out.write(record.getWinStreak() + "\n");
            out.write(record.getLossStreak() + "\n");
            out.write(record.getLastRoundNet() + "\n");
            out.write(record.getLastRoundWagered() + "\n");
            out.write(record.getMomentum() + "\n");
            out.write(record.getStartingBalance() + "\n");
            out.write(record.getEndingBalance() + "\n");
        }
    }

    /*method to save play session records to textfile
     * 
     * Parameters: path - file that history will be stored
     */
    public void saveRecordsToFile(String path){
        try{
            BufferedWriter out = new BufferedWriter(new FileWriter(path,false));
            out.write(simCode + "\n");
            out.write(recordCnt + "\n");
            for(int i = 0; i < recordCnt; i++){
                PlaySessionRecord temp = playRecords.get(i);
                out.write(temp.getPlayer().getId() + "\n");
                out.write(temp.getGame().getId() + "\n");
                out.write(temp.getTotalWinnings() + "\n");
                out.write(temp.getTableWins() + "\n");
                out.write(temp.getTableLosses() + "\n");
            }
            out.close();
        }
        catch(IOException e){
            System.out.println("Error writing to file " + e);
        }
    }
    
    /*method to load play session records from textfile
     * 
     * Parameters: path - file that contains the play session
     */
    public void loadRecordsFromFile(String path){    
        try{
            BufferedReader in = new BufferedReader(new FileReader(path));
            
            int sourceSimCode = Integer.parseInt(in.readLine());
            if(sourceSimCode != simCode){
                System.out.println("Error: Simulation code does not match");
                in.close();
                return;
            }

            playRecords.clear();

            PlaySessionRecord temp;
            int playerId;
            int gameId;
            int totalWinnings;
            int wins;
            int losses;
            Player player;
            Game game;
            recordCnt = Integer.parseInt(in.readLine());

            for (int i = 0; i < recordCnt; i++) {

                playerId = Integer.parseInt(in.readLine());
                gameId = Integer.parseInt(in.readLine());
                totalWinnings = Integer.parseInt(in.readLine());
                wins = Integer.parseInt(in.readLine());
                losses = Integer.parseInt(in.readLine());
                
                player = searchPlayer(playerId);
                game = searchGame(gameId);

                temp = new PlaySessionRecord(player, game, totalWinnings, wins, losses);
                
                playRecords.add(temp);
                player.addRecord(temp);
                game.addRecord(temp);
            }

            in.close();

        }
        catch(IOException e){
            System.out.println("Error reading file " + e);
        }
    }

    /*
    * Compares two players based on a specified metric to determine if playerAhead 
    * should come before playerBefore in a sorted list.
    *
    * Parameters:
    * - metric: An integer representing the metric used for comparison (0-7). 
    *           Each value corresponds to a specific player attribute (e.g., balance, wins).
    * - playerAhead: The player currently at a higher index.
    * - playerBefore: The player currently at a lower index.
    *
    * Returns:
    * - true if playerAhead should come before playerBefore, based on the comparison.
    * - false otherwise.
    */
    public boolean playerCompareTo(int metric, Player playerAhead, Player playerBefore){
        // Return true if swap is needed (i.e., playerAhead should come before playerBefore)
        // playerAhead and playerBefore is used to indicate if index of player is ahead/before other player
        switch(metric){
            case 0: // Name comparison (alphabetical order)
                return playerAhead.getName().compareTo(playerBefore.getName()) < 0;
    
            case 1: // Balance
                if(playerAhead.getBalance() < playerBefore.getBalance()) {
                    return true;
                } else if(playerAhead.getBalance() == playerBefore.getBalance()) {
                    // If balances are equal, compare by name
                    return playerCompareTo(0, playerAhead, playerBefore);
                }
                return false;
    
            case 2: // NumGames Played
                if(playerAhead.getNumGamesPlayed() < playerBefore.getNumGamesPlayed()) {
                    return true;
                } else if(playerAhead.getNumGamesPlayed() == playerBefore.getNumGamesPlayed()) {
                    // If number of games played is equal, compare by name
                    return playerCompareTo(0, playerAhead, playerBefore);
                }
                return false;
    
            case 3: // Legacy menu option: balance
                return playerCompareTo(1, playerAhead, playerBefore);
    
            case 4: // Wins
                if(playerAhead.getWins() < playerBefore.getWins()) {
                    return true;
                } else if(playerAhead.getWins() == playerBefore.getWins()) {
                    // If wins are equal, compare by name
                    return playerCompareTo(0, playerAhead, playerBefore);
                }
                return false;
    
            case 5: // Profit
                if(playerAhead.getProfit() < playerBefore.getProfit()) {
                    return true;
                } else if(playerAhead.getProfit() == playerBefore.getProfit()) {
                    // If profit is equal, compare by name
                    return playerCompareTo(0, playerAhead, playerBefore);
                }
                return false;
    
            case 6: // Year Started
                if(playerAhead.getYearStarted() < playerBefore.getYearStarted()) {
                    return true;
                } else if(playerAhead.getYearStarted() == playerBefore.getYearStarted()) {
                    // If year started is equal, compare by name
                    return playerCompareTo(0, playerAhead, playerBefore);
                }
                return false;
    
            case 7: // Mood
                if(playerAhead.getMood() < playerBefore.getMood()) {
                    return true;
                } else if(playerAhead.getMood() == playerBefore.getMood()) {
                    // If mood is equal, compare by name
                    return playerCompareTo(0, playerAhead, playerBefore);
                }
                return false;
    
            default: // Default case: compare by name
                return playerAhead.getName().compareTo(playerBefore.getName()) < 0;
        }
    }
    
    /*
    * Sorts the players based on a specified metric.
    *
    * Parameters:
    * - metric: An integer representing the attribute to sort players by (0-7).
    *           Each value corresponds to a specific player attribute (e.g., balance, wins).
    */
    public void sortPlayers(int metric){
        for (int i = 1; i < playerCnt; i++) {
            Player temp = players.get(i);
            int blank = i;
            while (blank > 0 && playerCompareTo(metric, temp, players.get(blank-1))) {
                players.set(blank, players.get(blank-1));
                blank--;
            }
            players.set(blank, temp);
        }
    }

    /*
    * Compares two games based on a specified metric and determines if gameAhead 
    * should come before gameBefore in a sorted list.
    *
    * Parameters:
    * - metric: An integer representing the metric used for comparison (0-6). 
    *           Each value corresponds to a specific game attribute (e.g., profit, number of players).
    * - gameAhead: The game currently at a higher index.
    * - gameBefore: The game currently at a lower index.
    *
    * Returns:
    * - true if gameAhead should come before gameBefore, based on the comparison.
    * - false otherwise.
    */
    public boolean gameCompareTo(int metric, Game gameAhead, Game gameBefore){
        // gameAhead is to indicate if game is ahead in index for the array of games
        // 0 - id, 1 - profit, 2 - numPlayers, 3 - maxTablePopulation, 4 - minBet, 5 - maxBet, 6 - numActions
        switch(metric){
            case 0: // Compare by id
                return gameAhead.getId() < gameBefore.getId();
            
            case 1: // Compare by profit
                return gameAhead.getProfit() < gameBefore.getId();  
                // Return true if profitAhead is less than profitBefore
            
            case 2: // Compare by numPlayers
                return gameAhead.getNumPlayers() < gameBefore.getNumPlayers();
            
            case 3: // Compare by maxTablePopulation
                return gameAhead.getMaxTablePopulation() < gameBefore.getMaxTablePopulation();
            
            case 4: // Compare by minBet
                return gameAhead.getMinBet() < gameBefore.getMinBet();
            
            case 5: // Compare by maxBet
                return gameAhead.getMaxBet() < gameBefore.getMaxBet();
            
            case 6: // Compare by numActions
                return gameAhead.getNumActions() < gameBefore.getNumActions();
            
            default:
                return false;  // If an invalid metric is passed
        }
    }
    

    /*
    * Sorts the games based on a specified metric.
    *
    * Parameters:
    * - metric: An integer representing the attribute to sort games by (0-6).
    *           Each value corresponds to a specific game attribute (e.g., profit, max bet).
    */
    public void sortGames(int metric){
        for(int i = 1; i < gameCnt; i++){
            Game temp;
            //insertion sort
            if(gameCompareTo(metric, casinoFloor.get(i), casinoFloor.get(i-1))){
                temp = casinoFloor.get(i);
                casinoFloor.set(i, casinoFloor.get(i-1));
                int tempIdx = i-1;
                while(tempIdx > 0 && gameCompareTo(metric, temp, casinoFloor.get(tempIdx-1) )){
                    casinoFloor.set(tempIdx, casinoFloor.get(tempIdx-1));
                    tempIdx--;
                }
                casinoFloor.set(tempIdx, temp);
            }
        }
    }
    

    /*
    * Searches for a player by name.
    *
    * Parameters:
    * - name: The name of the player to search for.
    *
    * Returns:
    * - The Player object if a player with the given name is found.
    * - null if no player with the given name is found.
    */
    public Player searchPlayer(int id){
        for(int i = 0; i < playerCnt; i++){
            if(players.get(i).getId() == id){
                return players.get(i);
            }

        }
        return null;
    }


    /*
    * Searches for a game by its ID.
    *
    * Parameters:
    * - id: The ID of the game to search for.
    *
    * Returns:
    * - The Game object if a game with the given ID is found.
    * - null if no game with the given ID is found.
    */
    public Game searchGame(int id){
        for(int i = 0; i < gameCnt; i++){
            if(casinoFloor.get(i).getId() == id){
                return casinoFloor.get(i);
            }
        }
        return null;
    }

    /*
    * Moves a seated player to a different table, e.g. for casino-initiated
    * floor reallocation. Validates eligibility (capacity and affordability,
    * mirroring CasinoSimulationEngine's own table-eligibility rule) before
    * mutating anything, so a rejected move never leaves the player seatless.
    *
    * Parameters:
    * - playerId: id of the player to relocate.
    * - targetGameId: id of the table the player should join.
    *
    * Returns:
    * - a MoveResult describing the outcome; MoveResult.SUCCESS on success.
    */
    public MoveResult movePlayer(int playerId, int targetGameId){
        Player player = searchPlayer(playerId);
        if(player == null){
            return MoveResult.PLAYER_NOT_FOUND;
        }
        Game target = searchGame(targetGameId);
        if(target == null){
            return MoveResult.TARGET_NOT_FOUND;
        }
        if(player.getCurrentSession() == null){
            return MoveResult.PLAYER_NOT_SEATED;
        }
        if(player.getCurrentSession().getGame() == target){
            return MoveResult.SAME_TABLE;
        }
        if(target.getNumPlayers() >= target.getMaxTablePopulation()){
            return MoveResult.TARGET_FULL;
        }
        if(player.getBalance() < target.getMinBet()){
            return MoveResult.INSUFFICIENT_BALANCE;
        }
        if(!player.leaveTable()){
            return MoveResult.LEAVE_FAILED;
        }
        PlaySessionRecord record = player.joinTable(target);
        if(record == null){
            return MoveResult.JOIN_FAILED;
        }
        addRecord(record);
        return MoveResult.SUCCESS;
    }

    /*
    * Removes a player from the casino entirely, e.g. for casino-initiated
    * floor policy. Reuses the same mechanism as profit-limit auto-removal
    * (Player.removeFromCasinoByPolicy) rather than the misleadingly-named
    * Security.kickPlayer, which only bumps a player from their current
    * table, not the casino.
    *
    * Parameters:
    * - playerId: id of the player to remove.
    *
    * Returns:
    * - a MoveResult describing the outcome; MoveResult.SUCCESS on success.
    */
    public MoveResult kickPlayer(int playerId){
        Player player = searchPlayer(playerId);
        if(player == null){
            return MoveResult.PLAYER_NOT_FOUND;
        }
        if(!player.isInCasino()){
            return MoveResult.PLAYER_NOT_IN_CASINO;
        }
        player.removeFromCasinoByPolicy();
        return MoveResult.SUCCESS;
    }


    /*
    * Displays the history of play session records.
    */
    public void displayHistory(){
        for(PlaySessionRecord record : playRecords){
            System.out.println(record);
        }
    }

    /*
    * Displays the list of players.
    */
    public void displayPlayers(){
        for(Player player : players){
            System.out.println(player);
        }
    }

    /*
    * Displays the list of staff.
    */
    public void displayStaff(){
        for(Staff staff : staffs){
            System.out.println(staff);
        }
    } 

    /*
    * Displays the list of games.
    */
    public void displayGames(){
        for(Game game : casinoFloor){
            System.out.println(game);
        }
    }

    /* method to blacklist players
     * 
     * Parameters:
     * member - authority to ban player
     * p - player to ban
     * 
     * return boolean
     * - indicates successful or if player does not exist
     */
    public boolean blacklistPlayer(Staff member, Player p){
        return member.blacklistPlayer(p);
    }

    /* method to blacklist players
     * 
     * Parameters:
     * staffName - name of staff
     * playerName - name of player to ban
     * 
     * return boolean
     * - indicates successful or if player + staff does not exist
     */
    public boolean blacklistPlayer(String staffName, String playerName){
        Staff member = null;
        Player p = null;
        for(Staff staff : staffs){
            if(staff.getName().equals(staffName)){
                System.out.println("running");
                member = staff;
            }
        }
        for(Player player : players){
            if(player.getName().equals(playerName)){
                System.out.println("run");
                p = player;
            }
        }
        if(member == null || p == null){
            return false;
        }
        return member.blacklistPlayer(p);
    }

    /*
     * method to kick player
     * 
     * Parameters:
     * member - authority to kick player
     * p - player to kick
     * 
     * return boolean
     * - indicates successful or if player does not exist
     */
    public boolean kickPlayer(Staff member, Player p){
        if(member instanceof Security){
            return ((Security)member).kickPlayer(p);
        }
        else{
            return false;
        }
    }

    /*
     * method to kick player
     * 
     * Parameters:
     * staffName - name of staff
     * playerName - name of player to kick
     * 
     * return boolean
     * - indicates successful or if player + staff does not exist
     */
    public boolean kickPlayer(String staffName, String playerName){
        Staff member = null;
        Player p = null;
        for(Staff staff : staffs){
            if(staff.getName().equals(staffName)){
                member = staff;
            }
        }
        for(Player player : players){
            if(player.getName().equals(playerName)){
                p = player;
            }
        }
        if(member == null || p == null){
            return false;
        }

        if(member instanceof Security){
            return ((Security)member).kickPlayer(p);
        }
        else{
            return false;
        }
    }

    /*
    * Checks the statistics of a player by their name.
    *
    * Parameters:
    * - name: The name of the player whose statistics are to be checked.
    *
    * Returns:
    * - true if the player exists and their stats are displayed.
    * - false if the player does not exist.
    */
    public boolean checkPlayerStats(int id){
        Player temp = searchPlayer(id);
        if(temp == null){
            return false;
        }
        else{
            System.out.println(temp);
            return true;
        }
    }


    /*
    * Attempts to rig the odds of a game by modifying the odds based on the action.
    *
    * Parameters:
    * - g: The Game object representing the game whose odds are to be modified.
    * - action: An integer representing the action to be performed (e.g., type of manipulation).
    * - odds: The new odds to set for the game.
    * - person: The Staff member attempting to rig the game.
    *
    * Returns:
    * - true if the action was successfully performed by a FloorManager.
    * - false if the person is not a FloorManager or if the operation fails.
    */
    public boolean rigOdds(Game g, int action, int odds, Staff person) {
        // Check if the person is an instance of FloorManager
        if (person instanceof FloorManager) {
            // If the person is a FloorManager, cast them and call the rigGame method
            return (((FloorManager) person).rigGame(g, action, odds));
        } else {
            // If the person is not a FloorManager, return false (operation cannot be performed)
            return false;
        }
    }

    /*
    * Sorts the staffs based on a specified metric.
    *
    * Parameters:
    * - metric: An integer representing the attribute to sort games by (0-6).
    *           Each value corresponds to a specific game attribute (e.g., profit, max bet).
    */
    public void sortStaff(){
        for(int i = 1; i < staffCnt; i++){
            Staff temp;
            if(staffs.get(i).getBlacklistCount() < staffs.get(i-1).getBlacklistCount() || (staffs.get(i).getBlacklistCount() == staffs.get(i-1).getBlacklistCount() && staffs.get(i).getName().compareTo(staffs.get(i-1).getName())> 0)){
                temp = staffs.get(i);
                staffs.set(i, staffs.get(i-1));
                int tempIdx = i-1;
                while(tempIdx > 0 && temp.getBlacklistCount() < staffs.get(tempIdx-1).getBlacklistCount() || (temp.getBlacklistCount() == staffs.get(tempIdx-1).getBlacklistCount()) && temp.getName().compareTo(staffs.get(tempIdx-1).getName()) > 0){
                    staffs.set(tempIdx, staffs.get(tempIdx-1));
                    tempIdx--;
                }
                staffs.set(tempIdx, temp);
            }
        }        
    }
}
