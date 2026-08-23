import java.util.*;

public class CasinoRunner {

    public static void main(String[] args) {
        if(args.length > 0){
            CasinoBatchRunner.main(args);
            return;
        }
        
        final String SIMULATION_FOLDER = "Simulations/";
        final String RECORDS_FOLDER = "Records/";

        boolean running = true;
        int userAction = -1;
        String input = null;
        String activeSavePath = null;

        Scanner sc = new Scanner(System.in);
        Casino casino = new Casino();

        while(running){
            
            userAction = -1;

            System.out.println("Welcome to your Casino!");
            
            System.out.println("------------------------------------------------------------");
            System.out.println(casino.getRevenue());
            System.out.println(casino.getPlayerCnt() + " players are currently in the casino.");
            System.out.println(casino.getStaffCnt() + " staff members are currently in the casino.");
            System.out.println(casino.getGameCnt() + " games are currently established in the casino.");

            System.out.println("------------------------------------------------------------");

            System.out.println("What would you like to do?");
            System.out.println("1. View Players");
            System.out.println("2. View Staff");
            System.out.println("3. View Games");
            System.out.println("4. Add Player");
            System.out.println("5. Add Staff");
            System.out.println("6. Add Game");
            System.out.println("7. Run Simulation");
            System.out.println("8. View Records");
            System.out.println("10. Save Casino State");
            System.out.println("11. Load Casino State");
            System.out.println("12. Save Records");
            System.out.println("13. Load Records");
            System.out.println("14. Blacklist Player");
            System.out.println("15. Kick Player");
            System.out.println("16. Exit");

            while(userAction == -1 || userAction < 1 || userAction > 16){
                try{
                    userAction = Integer.parseInt(sc.nextLine());
                }catch(NumberFormatException e){
                    System.out.println("Invalid input. Please enter a number between 1 and 16.");
                }
            }

            switch(userAction){
                case 1:  //--------------------------------------------------------------View Players--------------------------------------------------------------
                    System.out.println("Viewing players...");
                    casino.sortPlayers(0);
                    casino.displayPlayers();
                    
                    do{
                        System.out.println("Sort players by:");
                        System.out.println("1. Name");
                        System.out.println("2. Balance");
                        System.out.println("3. Number of Games Played");
                        System.out.println("4. Wins");
                        System.out.println("5. Profit");
                        System.out.println("6. Year Started");
                        System.out.println("7. Mood");
                        System.out.println("8. Exit");

                        do{
                            try{
                                userAction = Integer.parseInt(sc.nextLine());
                            }catch(NumberFormatException e){
                                System.out.println("Invalid input. Please enter a number between 1 and 8.");
                                userAction = -1;
                            }
                        }while(userAction < 1 || userAction > 8);
                        
                        if(userAction != 8){
                          int[] sortMetrics = {0, 1, 2, 4, 5, 6, 7};
                          casino.sortPlayers(sortMetrics[userAction - 1]);
                          casino.displayPlayers();
                        }
                        
                    }
                    while(userAction != 8);

                    break;
                case 2://--------------------------------------------------------------View Staff--------------------------------------------------------------
                    System.out.println("Viewing staff...");
                    casino.displayStaff();
                    break;
                case 3://--------------------------------------------------------------View Games--------------------------------------------------------------
                    System.out.println("Viewing games...");
                    casino.displayGames();

                    do{
                        System.out.println("Sort games by:");
                        System.out.println("1. ID");
                        System.out.println("2. Profit");
                        System.out.println("3. Table Population");
                        System.out.println("4. Minimum Bet");
                        System.out.println("5. Maximum Bet");
                        System.out.println("6. Exit");

                        do{
                            try{
                                userAction = Integer.parseInt(sc.nextLine());
                            }catch(NumberFormatException e){
                                System.out.println("Invalid input. Please enter a number between 1 and 6.");
                                userAction = -1;
                            }
                        }while(userAction < 1 || userAction > 6);
                        
                        if(userAction != 6){
                          casino.sortGames(userAction-1);
                          casino.displayGames();
                        }
                        
                    }while(userAction != 6);
                    break;
                case 4://--------------------------------------------------------------Add Player--------------------------------------------------------------
                    System.out.println("Adding player...");

                    String playerName;
                    int balance;
                    int playerType;

                    System.out.println("Enter player name:");
                    playerName = sc.nextLine();

                    System.out.println("Enter player balance:");
                    do{
                        try{
                            input = sc.nextLine();
                            if(Integer.parseInt(input) < 0){
                                System.out.println("Invalid input. Please enter a positive number.");
                            }
                        }catch(NumberFormatException e){
                            System.out.println("Invalid input. Please enter a number.");
                            input = "-1";
                        }
                    }while(Integer.parseInt(input) < 0);
                    balance = Integer.parseInt(input);

                    System.out.println("Enter player type:");
                    System.out.println("1. HighRoller");
                    System.out.println("2. EvenSteven");
                    System.out.println("3. LowRoller");

                    do{
                        try{
                            userAction = Integer.parseInt(sc.nextLine());
                        }catch(NumberFormatException e){
                            System.out.println("Invalid input. Please enter a number between 1 and 3.");
                            userAction = -1;
                        }
                    }while(userAction < 1 || userAction > 3);
                    playerType = userAction-1;

                    BehaviorType behaviorType;
                    do{
                        System.out.println("Enter player behavior:");
                        System.out.println("1. Whale");
                        System.out.println("2. Grinder");
                        System.out.println("3. Low-Stakes");
                        System.out.println("4. Obnoxious");
                        try{
                            userAction = Integer.parseInt(sc.nextLine());
                        }
                        catch(NumberFormatException e){
                            userAction = -1;
                        }
                        behaviorType = userAction >= 1 && userAction <= 4
                                ? BehaviorType.values()[userAction - 1] : null;
                        if(behaviorType == null
                                || !BehaviorStyleFactory.isCompatible(behaviorType,
                                        BankrollStyle.fromMenuCode(playerType))){
                            System.out.println("That behavior is not compatible with the selected bankroll type.");
                            behaviorType = null;
                        }
                    }while(behaviorType == null);

                    casino.addPlayer(playerName, balance, playerType,
                            BehaviorStyleFactory.create(behaviorType));

                    break;
                case 5://--------------------------------------------------------------Add Staff--------------------------------------------------------------
                    System.out.println("Adding staff...");
                    
                    String staffName;

                    System.out.println("Enter staff name:");
                    staffName = sc.nextLine();

                    System.out.println("Enter staff type:");
                    System.out.println("1. Security");
                    System.out.println("2. Floor Manager");

                    do{
                        try{
                            userAction = Integer.parseInt(sc.nextLine());
                        }catch(NumberFormatException e){
                            System.out.println("Invalid input. Please enter a number between 1 and 2.");
                            userAction = -1;
                        }
                    }while(userAction < 1 || userAction > 2);
                    casino.addStaff(staffName, userAction-1);

                    break;
                case 6://--------------------------------------------------------------Add Game--------------------------------------------------------------
                    System.out.println("Adding game...");

                    int gameType;
                    int tablePopulation;
                    int minBet;
                    int maxBet;
                    
                    System.out.println("Enter game type:");
                    System.out.println("1. Blackjack");
                    System.out.println("2. Roulette");
                    System.out.println("3. Slots");
                    System.out.println("4. Craps");
                    System.out.println("5. Poker (heuristic)");

                    do{
                        try{
                            gameType = Integer.parseInt(sc.nextLine());
                        }catch(NumberFormatException e){
                            System.out.println("Invalid input. Please enter a number between 1 and 5.");
                            gameType = -1;
                        }
                    }while(gameType < 1 || gameType > 5);

                    System.out.println("Enter table population:");
                    if(gameType == 3){
                        tablePopulation = 1;
                        System.out.println(1);
                    }
                    else{
                        do{
                            try{
                                input = sc.nextLine();
                                if(Integer.parseInt(input) < 0){
                                    System.out.println("Invalid input. Please enter a positive number.");
                                }
                            }catch(NumberFormatException e){
                                System.out.println("Invalid input. Please enter a number.");
                                input = "-1";
                            }
                        }while(Integer.parseInt(input) < 0);
                        tablePopulation = Integer.parseInt(input);
                    }

                    System.out.println("Enter minimum bet: ");
                    do{
                        try{
                            input = sc.nextLine();
                            if(Integer.parseInt(input) < 0){
                                System.out.println("Invalid input. Please enter a positive number.");
                            }
                        }catch(NumberFormatException e){
                            System.out.println("Invalid input. Please enter a number.");
                            input = "-1";
                        }
                    }while(Integer.parseInt(input) < 0);
                    minBet = Integer.parseInt(input);

                    System.out.println("Enter maximum bet: ");
                    if(gameType == 3){
                        maxBet = minBet;
                        System.out.println(minBet);
                    }
                    else{
                        do{
                            try{
                                input = sc.nextLine();
                                if(Integer.parseInt(input) < 0){
                                    System.out.println("Invalid input. Please enter a positive number.");
                                }
                            }catch(NumberFormatException e){
                                System.out.println("Invalid input. Please enter a number.");
                                input = "-1";
                            }
                        }while(Integer.parseInt(input) < 0);
                        maxBet = Integer.parseInt(input);
                    }

                    casino.addGame(tablePopulation, minBet, maxBet,gameType-1);

                    break;
                case 7://--------------------------------------------------------------Run Simulation--------------------------------------------------------------
                    System.out.println("Running simulation...");

                    if(activeSavePath == null){
                        System.out.println("Enter a file name for this simulation: ");
                        input = sc.nextLine();
                        activeSavePath = SIMULATION_FOLDER + input + ".txt";
                        if(!casino.saveSimToFile(activeSavePath)){
                            activeSavePath = null;
                            break;
                        }
                    }
                    
                    int runs;
                    System.out.println("Enter number of rounds you want to run the simulation: ");
                    do{
                        try{
                            input = sc.nextLine();
                            if(Integer.parseInt(input) < 0){
                                System.out.println("Invalid input. Please enter a positive number.");
                            }
                        }catch(NumberFormatException e){
                            System.out.println("Invalid input. Please enter a number.");
                            input = "-1";
                        }
                    }while(Integer.parseInt(input) < 0);
                    runs = Integer.parseInt(input);

                    for(int i = 0; i < runs; i++){
                        CasinoSimulationEngine.runRound(casino);
                    }
                    casino.saveSimToFile(activeSavePath);

                    break;
                case 8://--------------------------------------------------------------View Records--------------------------------------------------------------
                    System.out.println("Viewing records...");
                    casino.displayHistory();
                    break;
                case 10://--------------------------------------------------------------Save Casino State--------------------------------------------------------------
                    System.out.println("Saving casino state...");
                    if(activeSavePath == null){
                        System.out.println("Enter file name: ");
                        input = sc.nextLine();
                        activeSavePath = SIMULATION_FOLDER + input + ".txt";
                    }
                    if(casino.saveSimToFile(activeSavePath)){
                        System.out.println("Saved to " + activeSavePath);
                    }
                    break;
                case 11://--------------------------------------------------------------Load Casino State--------------------------------------------------------------
                    System.out.println("Loading casino state...");
                    System.out.println("Enter file name: ");
                    input = sc.nextLine();
                    String loadedSavePath = SIMULATION_FOLDER + input + ".txt";
                    if(casino.loadSimFromFile(loadedSavePath)){
                        activeSavePath = loadedSavePath;
                        System.out.println("Loaded " + activeSavePath);
                    }
                    break;
                case 12://--------------------------------------------------------------Save Records--------------------------------------------------------------
                    System.out.println("Saving records...");
                    System.out.println("Enter file name: ");
                    input = sc.nextLine();
                    casino.saveRecordsToFile(RECORDS_FOLDER + input + ".txt");
                    break;
                case 13://--------------------------------------------------------------Load Records--------------------------------------------------------------
                    System.out.println("Loading records...");
                    System.out.println("Enter file name: ");
                    input = sc.nextLine();
                    casino.loadRecordsFromFile(RECORDS_FOLDER + input + ".txt");                    
                    break;
                case 14://--------------------------------------------------------------Blacklist Player----------------------------------------------------------
                    System.out.println("Enter player to blacklist"); 
                    String removeName = sc.nextLine();
                    System.out.println("Enter staff to facilitate blacklisting");
                    String staffName1 = sc.nextLine();
                    if(casino.blacklistPlayer(staffName1, removeName)){
                        System.out.println("Player blacklisted successfully");
                    }
                    else{
                        System.out.println("Player not found or staff member not found");
                    }
                    break;
                case 15://--------------------------------------------------------------Kick Player--------------------------------------------------------------
                    System.out.println("Enter player to kick");
                    String kickName = sc.nextLine();
                    System.out.println("Enter staff to facilitate kicking");
                    String staffName2 = sc.nextLine();
                    if(casino.kickPlayer(staffName2, kickName)){
                        System.out.println("Player kicked successfully");
                    }
                    else{
                        System.out.println("Player not found, staff member not found, or player not playing!");
                    }
                    break;
                case 16://--------------------------------------------------------------Exit--------------------------------------------------------------
                    if(activeSavePath != null){
                        casino.saveSimToFile(activeSavePath);
                    }
                    running = false;
                    break;
            }
            
        }

        sc.close();

    }

}
