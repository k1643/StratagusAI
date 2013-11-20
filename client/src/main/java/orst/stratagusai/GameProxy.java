package orst.stratagusai;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;
import orst.stratagusai.util.Spatial;

public class GameProxy {

    private static final Logger log = Logger.getLogger(GameProxy.class);
    private static final int NEUTRAL_PLAYER_ID = 15;
    private static WargusUnitPrototypes prototypes = new WargusUnitPrototypes();
    private String hostname = "localhost";
    private GameSocket gameSocket;
    private InboundMessageSocket inboundMessageSocket;
    private int currentCycle = -1; // holds the Current Game Cycle
    
    private int mapWidth = -1; // map width
    private int mapLength = -1; // map height
    // delimiters for using with Stratagus
    private final String delimiters = "() ";

    private Map<Integer,Player> players = new LinkedHashMap<Integer,Player>();

    /**
     * map file path for statistics and logging.
     */
    private String mapPath;

    /**
     * Holds the map: row 1, col 4 is map[0][3] - originally holds tildes (~)
     *  at initialization
     */
    private char[][] gameMap = null;
    /*
     * Holds all of the game units.
     */
    private Map<Integer, Unit> units = new LinkedHashMap<Integer, Unit>();

    /** Maps unit strings to ints */
    private Map<Integer, String> UnitTypeMap = new LinkedHashMap<Integer, String>();
    private int request_count;

    /** unit events that occurred since the last unit state update */
    private List<UnitEvent> events = new ArrayList<UnitEvent>();

    public GameProxy() {
        try {
            // add all unit types to UnitTypeMap
            InputStream is = 
                    ClassLoader.getSystemResourceAsStream("wargus-unit-types.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            
            String st = br.readLine();

            int i = 1;
            while (st != null) {
                UnitTypeMap.put(i++, st);
              st = br.readLine();
            }
        }
        catch (FileNotFoundException fnfe) {
            log.error("wargus-unit-types.txt not found!");
            throw new RuntimeException("wargus-unit-types.txt not found!", fnfe);
        }
        catch (IOException e) {
            log.debug("ERROR reading from wargus-unit-types.txt");
            throw new RuntimeException("wargus-unit-types.txt not found!", e);
        }
    }

    /**
     * wait for connection to stratagus engine.
     */
    public void connect() throws IOException {
        connect(hostname);  // connect to localhost.
        //init();
    }

    /**
     * wait for connection to stratagus engine.
     */
    public void connect(String hostname) {
        this.hostname = hostname;
        while (gameSocket == null) {
            try {
                gameSocket = new GameSocket(hostname); // creates the client socket
            }
            catch (IOException e) {
                log.error("waiting for connection to " + hostname + "...");
                try {
                    Thread.sleep(5000);  // sleep 5 seconds
                }
                catch (InterruptedException e1) {
                    log.error("interrupted during wait for connection.");
                    return;
                }
            }
        }
        init();
    }

    /**
     * The inboundMessageSocket will carry messages from StratagusHIL - the human in the loop version
     * of stratagus.  We decided to have a second channel so that the two socket connections could have their
     * protocols evolve independently.
     * @param hostname
     */
    public void connectInboundMessageSocket(String hostname) {
        this.hostname = hostname;
        while (inboundMessageSocket == null) {
            try {
                inboundMessageSocket = new InboundMessageSocket(); // creates the client socket
            }
            catch (IOException e) {
                log.error("waiting for connection to inboundMessageSocket to " + hostname + "...");
                try {
                    Thread.sleep(5000);  // sleep 5 seconds
                }
                catch (InterruptedException e1) {
                    log.error("interrupted during wait for connection.");
                    return;
                }
            }
        }
    }

    /**
     * The inboundMessageSocket has a thread that pulls messages off the wire and puts them on a queue.  This call
     * pulls from the queue.
     * @return
     * @throws IOException
     */
    public String getNextInboundMessage() throws IOException {
        if (inboundMessageSocket == null) {
            throw new IOException("tried to getNextInboundMessage before inboundMessageSocket connected.");
        }
        return inboundMessageSocket.getNextInboundMessage();
    }

    /** initialize after connection to game.  get cycle and map. */
    public void init() {
        getCurrentCycleFromStratagus(); // sets the current cycle
        String gameInfo = getGameInfo();
        parseGameInfo(gameInfo);
    }

    /** clear the state after game is over. Prepare for new map */
    public void clearState() {
        gameMap = null;
        units.clear();
//        player_units.clear();
    }

    protected void parseGameInfo(String gameInfo) {
        StringTokenizer st = new StringTokenizer(gameInfo, delimiters, false);

        String str = st.nextToken();
        str = st.nextToken();
        str = st.nextToken(); // consumes #s, gameinfo, player-id
        int playerId = Integer.valueOf(st.nextToken()).intValue(); // get the player id
        str = st.nextToken(); // consumes width
        mapWidth = Integer.valueOf(st.nextToken()); // get the map width
        str = st.nextToken(); // consumes map length
        mapLength = Integer.valueOf(st.nextToken()); // get the map length

        gameMap = new char[mapLength][mapWidth]; // loading the map variable with ~
        for (int i = 0; i < mapLength; i++) {
            for (int j = 0; j < mapWidth; j++) {
                gameMap[i][j] = '~';
            }
        }
    }

    public int getCurrentCycleFromStratagus() {
        String command = "6 4";
        log.debug("Command: " + command);
        String response = sendCommandAndReceiveResponse(command);
        if (!response.matches("\\d+")) {
            // TODO: Stratagus returns OK sometimes.  What does it mean?
            log.error("invalid response: " + response);
            return currentCycle;
        }
        try {
            currentCycle = Integer.valueOf(response, 10).intValue();
        }
        catch (NumberFormatException e) {
            System.out.println(e.toString() + " >> Error in getting Current Cycle from Stratagus");
            System.exit(1);
        }
        return currentCycle;
    }

    public int getCurrentCycle() {
        return this.currentCycle;
    }

    public String getMapPath() {
        return mapPath;
    }

    public char getMapCell(int row, int col) {
        return gameMap[row][col];
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public int getMapHeight() {
        return mapLength;
    }

    public char[][] getMapCells() {
        return gameMap;
    }

    public Set<Player> getPlayers() {
        Set<Player> ps = new LinkedHashSet<Player>();
        ps.addAll(players.values());
        return ps;
    }

    public Player getPlayer(int id) {
        return players.get(id);
    }

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
    }
    
    public void getMapStateFromStratagus() {
        String command = "6 6";
        log.debug("Command: " + command);
        String mapState = sendCommandAndReceiveResponse(command);
        //log.debug("response: "+mapState);
        StringTokenizer st = new StringTokenizer(mapState, delimiters);
        mapLength = st.countTokens(); // #of rows
        int i = 0, j = 0, len = 0;
        while (st.hasMoreTokens()) {
            String str = st.nextToken();
            len = str.length();
            for (j = 0; j < len; j++) {
                gameMap[i][j] = str.charAt(j);
            }
            i++;
        }
        mapWidth = len;
    }

    public void myUnitCommandMove(int unitId, long moveToX, long moveToY) {
        String command = "2 " + unitId + " 2 " + moveToX + " " + moveToY;
        this.sendCommandAndReceiveResponse(command);
    }

    public void myUnitCommandAttack(int unitIdOfAttacker, int attackThisUnitId) {
        String command = "2 " + unitIdOfAttacker + " 3 " + attackThisUnitId;
        log.debug("Command: " + command + " (Attack)");
        String response = sendCommandAndReceiveResponse(command);
        assert "OK".equals(response) : "unrecognized response: " + response +
            " attacker=" + unitIdOfAttacker + ", enemy=" + attackThisUnitId;
    }

    public void myUnitCommandStopAllActions(int unitId) {
        String command = "2 " + unitId + " 1";
        this.sendCommandAndReceiveResponse(command);
    }

    public void myUnitCommandBuildBuilding(int unitId, String unitToBuild, int buildAtX, int buildAtY) {
        String command = "2 " + unitId + " 7 " + buildAtX + " " + buildAtY + " " + unitToBuild;
        log.debug("Command: " + command + " (Build) at " + currentCycle);
        String response = sendCommandAndReceiveResponse(command);
        assert "OK".equals(response) : "ERROR in build request: " + response;
    }

    public void myUnitCommandTrainUnit(int buildingUnitId, String unitToTrain) {
        // can't send X and Y if a building, else TEH ERRERZ!!1!
        String command = "2 " + buildingUnitId + " 8 " + unitToTrain;
        log.debug("Command: " + command + " (Train) at " + currentCycle);
        String response = sendCommandAndReceiveResponse(command);
        assert "OK".equals(response) : "ERROR in train request: " + response;
    }

    public void myUnitCommandRepair(int unitId, int unitToBuild) {
        String command = "2 " + unitId + " 10 " + unitToBuild;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        log.debug("response: " + response);
    }

    public void myUnitCommandDropOffResources(int unitId, int destId) {
        String command = "2 " + unitId + " 17 " + destId;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        log.debug("response: " + response);
    }

    public void myUnitCommandHarvestGold(int unitId, int destId) {
        String command = "2 " + unitId + " 15 " + destId;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        log.debug("response: " + response);
    }

    public void myUnitCommandHarvestWood(int unitId, int x, int y) {
        String command = "2 " + unitId + " 14 " + x + " " + y;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        log.debug("response: " + response);
    }

    public void myUnitCommandHarvestOil(int unitId, int destId) {
        String command = "2 " + unitId + " 16 " + destId;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        log.debug("response: " + response);
    }

    public void unitCommandFollow(int unitId, int leaderId) {
        String command = "2 " + unitId + " 20 " + leaderId;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        log.debug("response: " + response);
    }

    public void setVideoSpeed(int cyclesPerVideoUpdate) {
        String command = "9 " + cyclesPerVideoUpdate;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        //log.debug("response: "+response);
    }

    public void setSpeed(int newSpeed) {
        String command = "7 " + newSpeed;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        //log.debug("response: "+response);
    }

    public void killStratagus() {
        String command = "5";
        log.debug("Command: " + command);
        this.sendCommandAndReceiveResponse(command);
    }

    public void quitStratagus() {
        String command = "4";
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
        //og.debug("response: "+response);
    }

    public void advanceNCycles(int N) {
        String command = "8 " + N;
        log.debug("Command: " + command);
        this.sendCommandAndReceiveResponse(command);
        //        sequenceLog.transition(N);
    }

    public void advanceOneCycle() {
        String command = "8 1";
        log.debug("Command: " + command);
        this.sendCommandAndReceiveResponse(command);
    }

    public void restartScenario() {
        String command = "3";
        assert "OK".equals(sendCommandAndReceiveResponse(command));
    }

    public void sendRandomNumberSeedToStratagus(long newSeed) {
        String command = "11 " + newSeed;
        log.debug("Command: " + command);
        String response = this.sendCommandAndReceiveResponse(command);
    }

    public void loadMapAndRestartScenario(String newMapPath) {
        String command = "12 " + newMapPath;
        log.debug("Command: " + command);
        assert "OK".equals(sendCommandAndReceiveResponse(command));
        mapPath = newMapPath;
    }

    private String getGameInfo() {
        String command = "6 5";
        String str = "";
        log.debug("Command: " + command);
        str = this.sendCommandAndReceiveResponse(command);
        log.debug("Response: " + str);
        return str;
    }

    private String sendCommandAndReceiveResponse(String command) {
        gameSocket.writeToSocket(command);
        String response = gameSocket.readFromSocket();
        ++request_count;
        return response;
    }

    public void closeGameProxySocket() {
        if (gameSocket != null) {
            gameSocket.close();
        }
    }

    public void getUnitStatesFromStratagus() {
        String command = "6 7";
        log.debug("command: " + command);
        String response = sendCommandAndReceiveResponse(command);
        if (response == null) {
            log.error("get units states command " + command + 
                      " receives invalid response: null.");
            return;
        }
        if ("OK".equals(response)) {
            log.error("invalid response: " + response);
            return;
        }
        parseUnitStates(response);
    }

    protected void parseUnitStates(String response) {
        StringReader rd = new StringReader(response);
        try {
            // UnitStateReader updates the units in this proxy.
            UnitStateReader.read(this, rd);
            
        } catch (IOException ex) {
            // shouldn't happen when reading from a string
            throw new RuntimeException(ex);
        }
    }

    public void updateUnit(int playerId,
            int unitID, int unit_type,
            int x, int y, int hitPoints,
            long read_rAmount, int read_kills, 
            int armor, int dmg, int piercingDmg,
            int read_status,
            String arg1,
            String arg2) {

        Unit unit = units.get(unitID);
        String typeName = typeIntToString(unit_type);
        if (unit == null) {
            // new unit
            unit = prototypes.createUnit(typeIntToString(unit_type), unitID, playerId);
            addUnit(playerId, unit);
            log.debug(currentCycle + ": new unit " + unit);
        } else {
            // existing unit
            WargusUnitType type = WargusUnitType.getType(typeName);
            if (unit.getType() != unit_type && type != null) {
                // sometimes unit changes type to something like
                // 'unit-destroyed-3x3-place-water' to show dying animation.
                // We ignore type changes unless we know the type.
                prototypes.setTypeValues(unit, typeName);
            }
        }

        unit.setOwnerId(playerId);
        unit.setType(unit_type);
        unit.setUnitTypeString(typeName);
        unit.setDirection(Spatial.getDirection(unit.getLocX(), unit.getLocY(), x, y));
        unit.setLocX(x);
        unit.setLocY(y);
        unit.setHitPoints(hitPoints);
        unit.setRAmount(read_rAmount);
        unit.setKills(read_kills);
        unit.setArmor(armor);
        unit.setBasicDamage(dmg);
        unit.setPiercingDamage(piercingDmg);
        unit.setStatus(read_status);
        if (arg1 != null) {
            unit.getActionState().setArg1(Integer.parseInt(arg1));
        }
        if (arg2 != null) {
            unit.getActionState().setArg2(Integer.parseInt(arg2));
        }
    }

    /**
     * public access for creating test cases.
     */
    public void addUnit(int playerId, Unit unit) {
        Integer id = new Integer(unit.getUnitId());
        units.put(id, unit);
    }

    public Map<Integer, Unit> getUnits() {
        Map<Integer, Unit> us = new LinkedHashMap<Integer, Unit>();
        us.putAll(units);
        return us;
    }

    /** get units by player Id. */
    public Map<Integer, Unit> getUnits(int playerId) {
        Map<Integer, Unit> us = new LinkedHashMap<Integer, Unit>();
        for (Unit u : units.values()) {
            if (u.getOwnerId() == playerId) {
                us.put(u.getUnitId(), u);
            }
        }
        return us;
    }

    /** get enemies of given player.  Does not include neutral units. */
    public Map<Integer, Unit> getEnemyUnits(int playerId) {

        Map<Integer, Unit> us = new LinkedHashMap<Integer, Unit>();
        for (Unit unit : units.values()) {
            if (unit.getOwnerId() != playerId && unit.getOwnerId() != NEUTRAL_PLAYER_ID) {
                us.put(unit.getUnitId(), unit);
            }
        }
        return us;
    }

    /** get unit by ID. */
    public Unit getUnit(int unitId) {
        return units.get(unitId);
    }

    public List<UnitEvent> getEvents() {
        return events;
    }

    public void setEvents(List<UnitEvent> events) {
        this.events = events;
    }

    public void addEvent(UnitEvent e) {
        events.add(e);
    }

    public String typeIntToString(int type) {
        if (!UnitTypeMap.containsKey(type)) {
            log.warn("no unit type name for code " + type);
        }
        return UnitTypeMap.get(type);
    }
}
