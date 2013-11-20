/*
 *                               _|                    _|          _|
 *   _|_|_|    _|_|      _|_|_|  _|  _|      _|_|    _|_|_|_|      _|
 * _|_|      _|    _|  _|        _|_|      _|_|_|_|    _|          _|
 *     _|_|  _|    _|  _|        _|  _|    _|          _|
 * _|_|_|      _|_|      _|_|_|  _|    _|    _|_|_|      _|_|      _|
 *
 * Socket interface header file.
 * Adapted from the old version for the Oregon State University
 * Stratagusai project.
 * Based on protocol written by David Taylor
 * 
 * Sean Moore 11-8-2010
 */

#ifndef __SOCKET_H__
#define __SOCKET_H__

#include <string>
#include <queue>

#include "net_lowlevel.h"
#include "unit.h"
#include "unittype.h"

class SocketInterface {
public:

    enum Request {
        PING,           // 0: return "PING"
        ACTIVITY,       // 1: get info on game activity (paused, running, etc)
        COMMAND,        // 2: give a command to a unit
        RESTART,        // 3: restart the current game
        EXIT,           // 4: exit Stratagus
        KILL,           // 5: Exit(0);
        GET,            // 6: get various things at once
        SPEED,          // 7: set game speed and toggle WarpSpeed
        TRANSITION,     // 8: set GameCyclesPerTransistion
        VIDEO,          // 9: set the number of cycles per video update
        WRITE,          // 10: write the log to the specified filename
        SEED,           // 11: set random seed for Stratagus
        MAP,            // 12: set new map to be used and restart game
        CHAT,           // 13: send a chat message
        DIPLOMACY,      // 14: <playerID> <stanceID> <opponentID>
        UNKNOWN,        // 15: unknown request
        EVENTS          // 16: events from the event queue
    };

    enum Command {
        NOOP,           // 0: returns OK
        STOP,           // 1: stops unit's current and pending actions
        MOVE,           // 2: <x> <y> try to reach (x, y)
        ATTACK,         // 3: <targetID> attack target
        ATTACK_GROUND,  // 4: <x> <y> attack location
        BOARD,          // 5: <transportID> load unit onto transport
        UNLOAD,         // 6: <x> <y> <targetID ?= -1> unload target/all
        BUILD,          // 7: <x> <y> <typeID:string> build structure
        TRAIN,          // 8: <typeID> train unit of given type
        RESEARCH,       // 9: <techID:string> research technology
        REPAIR,         // 10: <targetID> repair target
        CAST,           // 11: <spellID:int> <targetID> cast spell on target
        CAST_AREA,      // 12: <spellID:int> <x> <y> cast spell at location
        CAST_AUTO,      // 13: <spillID:int> <toggle> toggle autocast
        HARVEST_WOOD,   // 14: <x> <y> try to harvest wood at location
        HARVEST_GOLD,   // 15: <mineID> try to harvest gold at mine
        HARVEST_OIL,    // 16: <platformID> try to harvest oil at platform
        RETURN_RES,     // 17: <targetID ?= -1> return res to target or closest
        STAND_GROUND,   // 18: stand ground
        PATROL,         // 19: <x> <y> patrol between current and given pts
        FOLLOW,         // 20: <targetID> follow unit
        DISMISS,        // 21: cancel building construction
        UPGRADE,        // 22: <unitType:string> upgrade unit to unitType
        ATTACK_MOVE     // 23: <x> <y> attack move to x,y
    };

    enum GetRequest {
        GET_MAP,        // 0: <playerID?> get visible map row-by-row as chars
        INFO,           // 1: num player, playerIDs, res, supply/demand, score
        UNITS_PLAYER,   // 2: <playerID> get units owned by player
        UNITS_VISIBLE,  // 3: <playerID> get unowned, visible units
        CYCLE,          // 4: get current cycle
        LEGACY_INFO,    // 5: support for old get
        LEGACY_MAP,     // 6: support for old get
        LEGACY_STATE    // 7: support for old get
    };

    enum Event {
        BUILT,
        TRAINED,
        DIED,
		ERROR_BUILD,
		ERROR_TRAIN
    };

    static const int DEFAULT_SOCKET_INTERFACE_PORT = 4870;
    static const int BUFFER_SIZE = 512;
    static const int FIND_RESOURCE_RANGE = 4;
    static const int SOCK_TIMEOUT_MS = 10;

    bool warpSpeed;
    unsigned cyclesPerTransition;
    unsigned long lastPausedCycle;
    int cyclesPerVideoUpdate;

    SocketInterface();
    virtual ~SocketInterface();
    void initInterface();
    void handleInterface();

    void HandleLispGetGameInfo();
    void HandleLispGetMap();
    void HandleLispGetState();

    // add event with event type and two unit ids
    void addEvent(Event type, int u1, int u2);

private:
    int socketInterfacePort;
    bool connected;
    Socket listeningSocket;
    Socket interfaceSocket;

    char socketBuffer[BUFFER_SIZE];
    char tempBuffer[BUFFER_SIZE];

    int minVideoSyncSpeed;

    std::queue<std::string> eventQueue;

    // ----------------------------------------------------------------------
    // Event methods
    // ----------------------------------------------------------------------
    std::string dumpEvents();

    // ----------------------------------------------------------------------
    // Socket methods
    // ----------------------------------------------------------------------
    int createListenSocket();
    void acceptConnection();
    int checkForDisconnect();
    void acceptRequests();
    void acceptRequestsNew();
    void sendResponseMessage(const char* message, const char mode);
    void cleanup();
    void handleRequests();

    // ----------------------------------------------------------------------
    // Request methods
    // network.cpp has network command parsing code, consider using?
    // ----------------------------------------------------------------------
    void handleActivity();
    void handleCommand();
    void handleGet();
    void handleKill();
    void handlePing();
    void handleQuit();
    void handleRestart();
    bool handleNewMap();
    void handleSpeed();
    void handleTransition();
    void handleVideo();
    void handleWrite();
    void handleRandomSeed();
    void handleChat();
    void handleDiplomacy();

    // ----------------------------------------------------------------------
    // GetRequest methods
    // ----------------------------------------------------------------------
    void handleGetMap();
    void handleGetCycle();
    void handleGetGameInfo();
    void handleGetUnits();

    // ----------------------------------------------------------------------
    // Command methods
    // commands.cpp has command parsing code, consider using?
    // ----------------------------------------------------------------------
    void handleCommandStop(CUnit *unit);
    void handleCommandMove(CUnit *unit);
    void handleCommandAttack(CUnit *unit);
    void handleCommandAttackGround(CUnit *unit);
    void handleCommandBoard(CUnit *unit);
    void handleCommandUnload(CUnit *unit);
    void handleCommandBuild(CUnit *unit);
    void handleCommandTrain(CUnit *unit);
    void handleCommandResearch(CUnit *unit);
    void handleCommandRepair(CUnit *unit);
    void handleCommandCast(CUnit *unit);
    void handleCommandCastArea(CUnit *unit);
    void handleCommandCastAuto(CUnit *unit);
    void handleCommandHarvestWood(CUnit *unit);
    void handleCommandHarvestGold(CUnit *unit);
    void handleCommandHarvestOil(CUnit *unit);
    void handleCommandReturnRes(CUnit *unit);
    void handleCommandStandGround(CUnit *unit);
    void handleCommandPatrol(CUnit *unit);
    void handleCommandFollow(CUnit *unit);
    void handleCommandUpgrade(CUnit *unit);
    void handleCommandAttackMove(CUnit *unit);
    void handleCommandDismiss(CUnit *unit);

    int getUnitStatus(const CUnit* unit, char* statusargs);
    CUnit* getUnitByID(int unitID);
    int getUnitTypeNum(CUnitType* type);

    // ----------------------------------------------------------------------
    // Lexing/Parsing methods
    // ----------------------------------------------------------------------
    char *firstToken(char *buffer);
    char *nextToken();
    Request toRequest(std::string token);
    Command toCommand(std::string token);
    GetRequest toGetRequest(std::string token);
	void consumeCommand();
};

#endif // __SOCKET_H__
