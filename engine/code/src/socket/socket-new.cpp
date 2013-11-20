/*
 *                               _|                    _|          _|
 *   _|_|_|    _|_|      _|_|_|  _|  _|      _|_|    _|_|_|_|      _|
 * _|_|      _|    _|  _|        _|_|      _|_|_|_|    _|          _|
 *     _|_|  _|    _|  _|        _|  _|    _|          _|
 * _|_|_|      _|_|      _|_|_|  _|    _|    _|_|_|      _|_|      _|
 *
 * Socket interface implementation file.
 * Adapted from the old version for the Oregon State University
 * Stratagusai project.
 * 
 * Sean Moore 7-11-2010
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <iostream>
#include <string>

#ifndef WIN32
#include<netinet/tcp.h>
#endif

#include <sstream>

#include "stratagus.h"
#include "iocompat.h"
#include "interface.h"
#include "map.h"
#include "net_lowlevel.h"
#include "player.h"
#include "socket-new.h"
#include "unittype.h"
#include "unit.h"
#include "commands.h"
#include "video.h"
#include "results.h"
#include "ctype.h"
#include "spells.h"
#include "network.h"

// ----------------------------------------------------------------------------

// I'm putting this here becasue it complained when I
// tried to put it in socket-new.h.
// FIXME: Find a better home for this.
// This is used around line 1340 in the toRequest function.
static char RequestStrings[][16] = {
	"PING",
	"ACTIVITY",
	"COMMAND",
	"RESTART",
	"EXIT",
	"KILL",
	"GET",
	"SPEED",
	"TRANSITION",
	"VIDEO",
	"WRITE",
	"SEED",
	"MAP",
	"CHAT",
	"DIPLOMACY",
	"UNKNOWN",
    "EVENTS"
};

static char GetStrings[][16] = {
	"GET_MAP",
	"INFO",
	"UNITS_VISIBLE",
	"UNITS_PLAYER",
	"CYCLE",
	"LEGACY_INFO",
	"LEGACY_MAP",
	"LEGACY_STATE"
};

// ----------------------------------------------------------------------------
// EVENT QUEUE
// ----------------------------------------------------------------------------
void SocketInterface::addEvent(Event type, int u1, int u2) {
    std::stringstream msg;

    switch (type) {
        case BUILT:
            msg << "(built " << u1 << " " << u2 << ")";
            eventQueue.push(msg.str());
            break;
        case TRAINED:
            msg << "(trained " << u1 << " " << u2 << ")";
            eventQueue.push(msg.str());
            break;
        case DIED:
            msg << "(died " << u1 << ")";
            eventQueue.push(msg.str());
            break;
        default:
            break;
    }
}

/**
 * Empties the event queue and returns a string comprised of the events
 * @return all events in the event queue
 */
std::string SocketInterface::dumpEvents() {
    std::stringstream events;

    while (!eventQueue.empty()) {
        events << eventQueue.front();
        eventQueue.pop();
    }

    return events.str();
}

// ----------------------------------------------------------------------------
// LEGACY GET FUNCTIONS
// ----------------------------------------------------------------------------
void SocketInterface::HandleLispGetGameInfo() {
    char buf[256];
    sprintf(buf, "#s(gameinfo player-id %d width %d length %d)\n", ThisPlayer->Index, Map.Info.MapWidth, Map.Info.MapHeight);
    sendResponseMessage(buf, 3);
}

void SocketInterface::HandleLispGetMap() {
    int i, j;
    char buf[MaxMapWidth + 20];

    sprintf(buf, "(\n");
    sendResponseMessage(buf, 1);
    for (i = 0; i < Map.Info.MapHeight; i++) {
        buf[0] = '(';
        for (j = 0; j < Map.Info.MapWidth; j++) {
            if (Map.ForestOnMap(j, i)) {
                buf[j + 1] = 'T';
            } else if (Map.WaterOnMap(j, i)) {
                buf[j + 1] = '^';
            } else if (Map.CoastOnMap(j, i)) {
                buf[j + 1] = '+';
            } else if (Map.HumanWallOnMap(j, i)) {
                buf[j + 1] = 'h';
            } else if (Map.RockOnMap(j, i)) {
                buf[j + 1] = '#';
            } else if (Map.WallOnMap(j, i)) {
                buf[j + 1] = 'W';
            } else if (Map.OrcWallOnMap(j, i)) {
                buf[j + 1] = 'c';
            } else {
                buf[j + 1] = '0';
            }
        }
        sprintf(buf + Map.Info.MapWidth + 1, ")\n");
        sendResponseMessage(buf, 0);
    }
    sprintf(buf, ")\n");
    sendResponseMessage(buf, 2);
}

void SocketInterface::HandleLispGetState() {
    int i;
    char buf[256];
    int status;
    char argsbuf[80];

    sprintf(buf, "((\n"); // begin units list
    sendResponseMessage(buf, 1);
    for (i = 0; i < NumUnits; ++i) {
        // Unit is dying when its action is UnitActionDie.  It may still have
        // hit points left as its corpse decays on the battlefield.  We
        // mark the hit points as 0 to indicate that the unit is dead.
        //
        // See unit.cpp LetUnitDie():
        //  unit->Orders[0]->Action = UnitActionDie;
        //
        int hp = Units[i]->Variable[HP_INDEX].Value;
        if (Units[i]->Orders[0]->Action == UnitActionDie ||
                Units[i]->Destroyed) {
            hp = 0;
        }
        status = getUnitStatus(Units[i], argsbuf); // UnitVisibleOnMap(Units[i], ThisPlayer)
		int type = getUnitTypeNum(Units[i]->Type);
		int armor = Units[i]->Variable[ARMOR_INDEX].Value;
		int basic = Units[i]->Variable[BASICDAMAGE_INDEX].Value;
		int piercing = Units[i]->Variable[PIERCINGDAMAGE_INDEX].Value;
        sprintf(buf, "( %d . #s(unit player-id %d type %d loc (%d %d) hp %d r-amt %d kills %d armor %d dmg %d piercing-dmg %d status %d status-args (%s)))\n",
                UnitNumber(Units[i]),
                Units[i]->Player->Index,
                type,
                Units[i]->X,
                Units[i]->Y,
                hp,
                Units[i]->ResourcesHeld,
                Units[i]->Variable[KILL_INDEX].Value,
				armor, 
				basic, 
				piercing,
                status,
                argsbuf);
        sendResponseMessage(buf, 0);
    }
	sprintf(buf, ")");
	sendResponseMessage(buf, 0);

	for (int i = 0; i < NumPlayers; i++) {
		sprintf(buf, "(:player :id %d :gold %d :wood %d :oil %d :supply %d :demand %d)",
					Players[i].Index,
					Players[i].Resources[GoldCost], 
					Players[i].Resources[WoodCost], 
					Players[i].Resources[OilCost], 
					Players[i].Supply, 
					Players[i].Demand);
		sendResponseMessage(buf, 0);
	}

    //sprintf(buf, ") #S(global-state :gold %d :wood %d :oil %d :supply %d :demand %d)",
    //        ThisPlayer->Resources[GoldCost], ThisPlayer->Resources[WoodCost], ThisPlayer->Resources[OilCost], ThisPlayer->Supply, ThisPlayer->Demand);
	//sendResponseMessage(buf, 0);

	sprintf(buf, "(:events ");
	sendResponseMessage(buf, 0);
	sprintf(buf, dumpEvents().c_str());
	sendResponseMessage(buf, 0);
	sprintf(buf, ")");
	sendResponseMessage(buf, 0);

	sprintf(buf, ")\n");
	sendResponseMessage(buf, 2);
}

// ----------------------------------------------------------------------------

SocketInterface::SocketInterface()
: socketInterfacePort(DEFAULT_SOCKET_INTERFACE_PORT), cyclesPerTransition(30),
		lastPausedCycle(0), cyclesPerVideoUpdate(1), minVideoSyncSpeed(100),
		warpSpeed(false)
{
	initInterface();
}

SocketInterface::~SocketInterface() {
	cleanup();
}

// Initialize the listening socket
void SocketInterface::initInterface() {
	cleanup();
	DebugPrint("Initializing Socket Interface.\n");
	NetInit();
	int result = createListenSocket();

#ifdef WIN32
	DebugPrint("Socket interface initialized.\nListening on port %d, result = "
			"%d, error = %d\n" _C_ socketInterfacePort _C_ result _C_
			WSAGetLastError());
#else
	DebugPrint("Socket interface initialized.\nListening on port %d, result = "
			"%d\n" _C_ socketInterfacePort _C_ result);
#endif
}

// Initializes listening socket
// @return 0 for success, -1 for error
int SocketInterface::createListenSocket() {
	listeningSocket = NetOpenTCP(socketInterfacePort);
	NetSetNonBlocking(listeningSocket);
	int result = NetListenTCP(listeningSocket);
	return result;
}

// Close the sockets
void SocketInterface::cleanup() {
	if (connected && interfaceSocket != INVALID_SOCKET) {
        NetCloseTCP(interfaceSocket);
		connected = false;
	}

	if (listeningSocket != INVALID_SOCKET)
		NetCloseTCP(listeningSocket);
}

// Called in the engine game loop
void SocketInterface::handleInterface() {
	acceptConnection();
	// if we're connected, pause for client
	if (connected) {
		// comment out GamePaused for one command at a time
		GamePaused = true;
		lastPausedCycle = GameCycle;
		acceptRequests();

		if (VideoSyncSpeed < minVideoSyncSpeed) {
			VideoSyncSpeed = minVideoSyncSpeed;
			SetVideoSync();
		}
	}
	else {
		DebugPrint("Not connected.\n");
		GamePaused = false;
	}
}

// Accept possible socket connection request
void SocketInterface::acceptConnection() {
	Socket newSocket = NetAcceptTCP(listeningSocket);
	if (newSocket != INVALID_SOCKET) {
		if (connected) {
			sendResponseMessage("New connection made. Goodbye.\n", 3);
			NetCloseTCP(interfaceSocket);
		}
		interfaceSocket = newSocket;
		DebugPrint("Established socket interface connection to %d.%d.%d.%d "
				"port %d\n" _C_ NIPQUAD(ntohl(NetLastHost)) _C_ NetLastPort);
        NetSetNonBlocking(interfaceSocket);
        connected = true;

		memset(socketBuffer, 0, BUFFER_SIZE);
	}
}

/* Old version
void SocketInterface::acceptRequests() {
	int length = strlen(socketBuffer);

	// if no pending requests
	if (length == 0)
	{
		// keep going till the requests have been received completely
		// (the last character is an endline)
		do {
			memset(tempBuffer, 0, BUFFER_SIZE);
			length = NetRecvTCP(interfaceSocket, tempBuffer, BUFFER_SIZE -
					length - 1);
			if (length > 0)
				strcat(socketBuffer, tempBuffer);
			
		} while (length > 0 && strlen(socketBuffer) < BUFFER_SIZE - 1 &&
				socketBuffer[strlen(socketBuffer) - 1] != '\n');
	}

	if (strlen(socketBuffer) > 0) {
		handleRequests();
	}
	else if (checkForDisconnect()) {
		DebugPrint("Broken connection.");
		connected = false;
		NetCloseTCP(interfaceSocket);
	}
}
 */

void SocketInterface::acceptRequests() {
	int bufferLength = strlen(socketBuffer);
	int recvLength = 0;
	int ready = NetSocketReady(interfaceSocket, SOCK_TIMEOUT_MS);
	
	if (ready) {
		memset(tempBuffer, 0, BUFFER_SIZE);
		recvLength = NetRecvTCP(interfaceSocket, tempBuffer, BUFFER_SIZE -
				bufferLength);

		if (recvLength > 0)
			strcat(socketBuffer, tempBuffer);
	}
	else if (checkForDisconnect()) {
		DebugPrint("Broken connection.");
		connected = false;
		NetCloseTCP(interfaceSocket);
	}

	bufferLength = strlen(socketBuffer);
	if (bufferLength > 0) {
		handleRequests();
	}
}

char *SocketInterface::firstToken(char *buffer) {
	strcpy(tempBuffer, buffer);
	return strtok(tempBuffer, " \r\n");
}

char *SocketInterface::nextToken() {
	return strtok(NULL, " \r\n");
}

void SocketInterface::handleRequests() {
	DebugPrint("Received request %s" _C_ socketBuffer);
	bool keepProcessing = true;
	char *token = firstToken(socketBuffer);
    std::stringstream eventStream;

	while (token != 0 && keepProcessing) {
		Request request = toRequest(token);
		
		switch (request) {
			case PING:
				handlePing();
				break;
			case ACTIVITY:
				handleActivity();
				break;
			case COMMAND:
				handleCommand();
				break;
			case RESTART:
				handleRestart();
				keepProcessing = false;
				break;
			case EXIT:
				handleQuit();
				break;
			case KILL:
				handleKill();
				break;
			case GET:
				handleGet();
				break;
			case SPEED:
				handleSpeed();
				break;
			case TRANSITION:
				handleTransition();
				keepProcessing = false;
				break;
			case VIDEO:
				handleVideo();
				break;
			case WRITE:
				handleWrite();
				break;
			case SEED:
				handleRandomSeed();
				break;
			case MAP:
				if (handleNewMap())
					keepProcessing = false;
				break;
			case CHAT:
				// not supported
				// handleChat();
				sendResponseMessage("NOT SUPPORTED\n", 3);
				break;
			case DIPLOMACY:
				// not supported
				// handleDiplomacy();
				sendResponseMessage("NOT SUPPORTED\n", 3);
				break;
            case EVENTS:
                eventStream << dumpEvents() << '\n';
                sendResponseMessage(eventStream.str().c_str(), 3);
                break;
			default:
				sendResponseMessage("Unknown command.\n", 3);
				break;
		}
		token = nextToken();
	}
	// Bring pending commands to the front of receiveBuffer
    if (token != 0) {
        int offset = token + strlen(token) - tempBuffer;
        tempBuffer[offset] = socketBuffer[offset];
        strcpy(socketBuffer, token);
    }
	else {
        memset(socketBuffer, 0, BUFFER_SIZE); // Clear the command buffer
	}
}

// Not sure of behavior for non-network game
void SocketInterface::handleDiplomacy() {
	std::string player, stance, opponent;
	int playerID, stanceID, opponentID;

	player = nextToken();
	stance = nextToken();
	opponent = nextToken();
	
	if (player.empty() || stance.empty() || opponent.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	playerID = atoi(player.c_str());
	stanceID = atoi(stance.c_str());
	opponentID = atoi(opponent.c_str());

	SendCommandDiplomacy(playerID, stanceID, opponentID);
	sendResponseMessage("OK\n", 3);
}

// Only works in a network game
// Consider SetMessage from mainscr.cpp for non-network
void SocketInterface::handleChat() {
	std::string chatMsg = nextToken();

	if (chatMsg.empty()) {
		sendResponseMessage("MISSING ARG message\n", 3);
		return;
	}
	NetworkChatMessage(chatMsg);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommand() {
	char* token;
	int unitID;
	CUnit *unit;

	token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	unitID = atoi(token);
	unit = getUnitByID(unitID);
	if (!unit) {
		std::string msg = "Invalid unit ";
		msg.append(token);
		msg.append(".\n");
		consumeCommand();
		sendResponseMessage(msg.c_str(), 3);
		return;
	}

	if (unit->Orders[0]->Action == UnitActionDie) {
		std::string msg = "Cannot command dying unit ";
		msg.append(token);
		msg.append(".\n");
		consumeCommand();
		DebugPrint(msg.c_str());
		sendResponseMessage(msg.c_str(), 3);	
		return;
	}

	token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	Command command = toCommand(token);

	switch (command) {
		case NOOP:
			sendResponseMessage("OK\n", 3);
			break;
		case STOP:
			handleCommandStop(unit);
			break;
		case MOVE:
			handleCommandMove(unit);
			break;
		case ATTACK:
			handleCommandAttack(unit);
			break;
		case ATTACK_GROUND:
			handleCommandAttackGround(unit);
			break;
		case BOARD:
			handleCommandBoard(unit);
			break;
		case UNLOAD:
			handleCommandUnload(unit);
			break;
		case BUILD:
			handleCommandBuild(unit);
			break;
		case TRAIN:
			handleCommandTrain(unit);
			break;
		case RESEARCH:
			handleCommandResearch(unit);
			break;
		case REPAIR:
			handleCommandRepair(unit);
			break;
		case CAST:
			handleCommandCast(unit);
			break;
		case CAST_AREA:
			handleCommandCastArea(unit);
			break;
		case CAST_AUTO:
			handleCommandCastAuto(unit);
			break;
		case HARVEST_WOOD:
			handleCommandHarvestWood(unit);
			break;
		case HARVEST_OIL:
		case HARVEST_GOLD:
			handleCommandHarvestGold(unit);
			break;
		case RETURN_RES:
			handleCommandReturnRes(unit);
			break;
		case STAND_GROUND:
			handleCommandStandGround(unit);
			break;
		case PATROL:
			handleCommandPatrol(unit);
			break;
		case FOLLOW:
			handleCommandFollow(unit);
			break;
		case DISMISS:
			handleCommandDismiss(unit);
			break;
		case UPGRADE:
			handleCommandUpgrade(unit);
			break;
		case ATTACK_MOVE:
			handleCommandAttackMove(unit);
			break;
		default:
			sendResponseMessage("Unknown command.\n", 3);
			return;
	}
}

void SocketInterface::handleCommandDismiss(CUnit *unit) {
	SendCommandDismiss(unit);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandStop(CUnit *unit) {
	SendCommandStopUnit(unit);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandMove(CUnit *unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("BAD ARG\n", 3);
		return;
	}
	int x = atoi(token);

	token = nextToken();
	if (!token) {
		sendResponseMessage("BAD ARG\n", 3);
		return;
	}
	int y = atoi(token);

	SendCommandMove(unit, x, y, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandAttack(CUnit *unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	int destID = atoi(token);
	
	CUnit *dest = getUnitByID(destID);
	if (!dest) {
		sendResponseMessage("Unknown unit to attack.\n", 3);
		return;
	}
	SendCommandAttack(unit, 0, 0, dest, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandAttackGround(CUnit* unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	int x = atoi(token);

	token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	int y = atoi(token);
	SendCommandAttackGround(unit, x, y, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandBoard(CUnit *unit) {
	std::string xtok, ytok, desttok;

	// @FIXME: Temporary fix (maybe get rid of x, y args)
	/*
	xtok = nextToken();
	ytok = nextToken();
	*/
	desttok = nextToken();

	if (/*xtok.empty() || ytok.empty() || */desttok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}

	/*
	int x = atoi(xtok.c_str());
	int y = atoi(ytok.c_str());
	*/

	int x = unit->X;
	int y = unit->Y;

	CUnit *dest = getUnitByID(atoi(desttok.c_str()));
	if (!dest) {
		sendResponseMessage("Invalid dest unit\n", 3);
		return;
	}
	
	SendCommandBoard(unit, x, y, dest, 1);

	SendCommandFollow(dest, unit, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandUnload(CUnit *unit) {
	std::string xtok, ytok, whattok;
	xtok = nextToken();
	ytok = nextToken();
	whattok = nextToken();

	if (xtok.empty() || ytok.empty()) {
		sendResponseMessage("MISSING ARGS x, y\n", 3);
		return;
	}

	int x = atoi(xtok.c_str());
	int y = atoi(ytok.c_str());
	CUnit *what = getUnitByID(atoi(whattok.c_str()));
	
	SendCommandUnload(unit, x, y, what, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandBuild(CUnit *unit) {
	std::string xtok, ytok, whattok;
	xtok = nextToken();
	ytok = nextToken();
	whattok = nextToken();

	if (xtok.empty() || ytok.empty() || whattok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}

	int x = atoi(xtok.c_str());
	int y = atoi(ytok.c_str());
	CUnitType *what = UnitTypeByIdent(whattok);

	if (!what) {
		sendResponseMessage("Unknown unit type\n", 3);
		return;
	}
	
	SendCommandBuildBuilding(unit, x, y, what, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandTrain(CUnit *unit) {
	std::string whattok = nextToken();
	if (whattok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	CUnitType *what = UnitTypeByIdent(whattok);

	SendCommandTrainUnit(unit, what, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandResearch(CUnit *unit) {
	std::string whattok = nextToken();
	if (whattok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	CUpgrade *what = CUpgrade::Get(whattok);
	
	SendCommandResearch(unit, what, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandRepair(CUnit *unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	CUnit *dest = getUnitByID(atoi(token));
	if (!dest) {
		sendResponseMessage("Invalid unit\n", 3);
		return;
	}

	SendCommandRepair(unit, 0, 0, dest, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandCast(CUnit *unit) {
	std::string desttok, spellidtok;
	desttok = nextToken();
	spellidtok = nextToken();

	if (desttok.empty() || spellidtok.empty()) {
		sendResponseMessage("MISSING ARG", 3);
		return;
	}

	CUnit *dest = getUnitByID(atoi(desttok.c_str()));
	if (!dest) {
		sendResponseMessage("Invalid unit\n", 3);
		return;
	}

	int spellid = atoi(spellidtok.c_str());
	
	SendCommandSpellCast(unit, 0, 0, dest, spellid, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandCastArea(CUnit* unit) {
	std::string xtok, ytok, spellidtok;
	xtok = nextToken();
	ytok = nextToken();
	spellidtok = nextToken();

	if (xtok.empty() || ytok.empty() || spellidtok.empty()) {
		sendResponseMessage("MISSING ARG", 3);
		return;
	}

	int x = atoi(xtok.c_str());
	int y = atoi(ytok.c_str());
	int spellid = atoi(spellidtok.c_str());

	SendCommandSpellCast(unit, x, y, NoUnitP, spellid, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandCastAuto(CUnit *unit) {
	std::string spellidtok, ontok;
	spellidtok = nextToken();
	ontok = nextToken();

	if (spellidtok.empty() || ontok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}

	int spellid = atoi(spellidtok.c_str());
	int on = atoi(ontok.c_str());

	SendCommandAutoSpellCast(unit, spellid, on);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandHarvestWood(CUnit* unit) {
	std::string xtok, ytok;
	xtok = nextToken();
	ytok = nextToken();

	if (xtok.empty() || ytok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	int x = atoi(xtok.c_str());
	int y = atoi(ytok.c_str());

	SendCommandResourceLoc(unit, x, y, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandHarvestGold(CUnit* unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	CUnit *dest = getUnitByID(atoi(token));
	if (dest == NoUnitP) {
		sendResponseMessage("Invalid resource unit ID.\n", 3);
		return;
	}
	SendCommandResource(unit, dest, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandReturnRes(CUnit *unit) {
	if (unit->CurrentResource == 0 || unit->ResourcesHeld == 0) {
		sendResponseMessage("Unit has no resources to return.\n", 3);
		return;
	}
	CUnit *dest;
	char *token = nextToken();
	
	if (!token) {
		dest = FindDeposit(unit, unit->X, unit->Y, FIND_RESOURCE_RANGE,
			unit->CurrentResource);
	}
	else {
		dest = getUnitByID(atoi(token));
		if (dest == NoUnitP) {
			sendResponseMessage("Invalid unit\n", 3);
			return;
		}
	}
	SendCommandReturnGoods(unit, dest, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandStandGround(CUnit *unit) {
	SendCommandStandGround(unit, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandPatrol(CUnit *unit) {
	std::string xtok, ytok;
	xtok = nextToken();
	ytok = nextToken();
	if (xtok.empty() || ytok.empty()) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	int x = atoi(xtok.c_str());
	int y = atoi(ytok.c_str());
	
	SendCommandPatrol(unit, x, y, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandFollow(CUnit *unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	CUnit *dest = getUnitByID(atoi(token));
	
	SendCommandFollow(unit ,dest, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandUpgrade(CUnit *unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}
	CUnitType *what = UnitTypeByIdent(token);
	SendCommandUpgradeTo(unit, what, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleCommandAttackMove(CUnit *unit) {
	char *token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG x\n", 3);
		return;
	}
	int x = atoi(token);
	token = nextToken();
	if (!token) {
		sendResponseMessage("MISSING ARG y\n", 3);
		return;
	}
	int y = atoi(token);
	SendCommandAttack(unit, x, y, NoUnitP, 1);
	sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleGet() {
	std::string token = nextToken();
	if (token.empty()) {
		sendResponseMessage("Bad command.\n", 3);
		return;
	}
	GetRequest get = toGetRequest(token);
	printf("GET %s\n", GetStrings[get]);
	fflush(stdout);
	
	switch (get) {
		case GET_MAP:
			handleGetMap();
			break;
		case INFO:
			handleGetGameInfo();
			break;
		case UNITS_VISIBLE:
			// @TODO: implement
		case UNITS_PLAYER:
			handleGetUnits();
			break;
		case CYCLE:
			handleGetCycle();
			break;
		case LEGACY_INFO:
			HandleLispGetGameInfo();
			break;
		case LEGACY_MAP:
			HandleLispGetMap();
			break;
		case LEGACY_STATE:
			HandleLispGetState();
			break;
		default:
			sendResponseMessage("Unknown GET argument.\n", 3);
	}
}

void SocketInterface::handleKill() {
	sendResponseMessage("OK\n", 3);
	Exit(0);
}

void SocketInterface::handlePing() {
	sendResponseMessage("PING\n", 3);
}

void SocketInterface::handleQuit() {
	sendResponseMessage("BYE\n", 3);
	NetCloseTCP(interfaceSocket);
	connected = false;
}

void SocketInterface::handleRestart() {
	// code from trigger.cpp StopGame().
	GameResult = GameRestart;
	GamePaused = true;
	GameRunning = false;
	lastPausedCycle = 0;
	sendResponseMessage("OK\n", 3);
}

bool SocketInterface::handleNewMap() {
	char* token = nextToken();

	if (token) {
		GameResult = GameRestart;
		GamePaused = true;
		GameRunning = false;
		strcpy(CurrentMapPath, token);
		fprintf(stderr, "new map is '%s'\n", CurrentMapPath);
		lastPausedCycle = 0;
		sendResponseMessage("OK\n", 3);
		return true;
	}
	else {
		sendResponseMessage("MISSING ARG\n", 3);
		return false;
	}
}

void SocketInterface::handleSpeed() {
	char* token = nextToken();

	if (token) {
		int speedup = atoi(token);

		if (speedup == 0) {
			char message[BUFFER_SIZE];
			sprintf(message, "BAD ARG %s\n", token);
			sendResponseMessage(message, 3);
		}
		else if (speedup == -1) {
			warpSpeed = true;
		}
		else {
			warpSpeed = false;
			minVideoSyncSpeed = speedup;
			VideoSyncSpeed = minVideoSyncSpeed;
			SetVideoSync();
		}
		sendResponseMessage("OK\n", 3);
	}
	else {
		sendResponseMessage("MISSING ARG\n", 3);
	}
}

void SocketInterface::handleTransition() {
	GamePaused = false;
	char* token = nextToken();

	if (token) {
		unsigned num_cycles = (unsigned) atoi(token);

		if (num_cycles == 0) {
			char message[BUFFER_SIZE];
			sprintf(message, "BAD ARG %s\n", token);
			sendResponseMessage(message, 3);
		}
		else {
			cyclesPerTransition = num_cycles;
			sendResponseMessage("OK\n", 3);
		}
	}
	else {
		sendResponseMessage("MISSING ARG\n", 3);
	}
}

void SocketInterface::handleVideo() {
    char *token = nextToken();
    int speedup;

    if (token) {
        speedup = atoi(token);

        if (speedup == 0) {
			char message[BUFFER_SIZE];
			sprintf(message, "BAD ARG %s\n", token);
            sendResponseMessage(message, 3);
		}
        else {
            cyclesPerVideoUpdate = speedup;
            sendResponseMessage("OK\n", 3);
        }
    }
	else {
		sendResponseMessage("MISSING ARG\n", 3);
	}
}

void SocketInterface::handleWrite() {
    char* newFileName;
    char logFileName[PATH_MAX];
    FILE *fd;
    char *buf;
    struct stat s;

    newFileName = nextToken();
    if (!newFileName) {
		sendResponseMessage("MISSING ARG\n", 3);
		return;
	}

    sprintf(logFileName, "%s/logs/log_of_stratagus_%d.log", GameName.c_str(),
			ThisPlayer->Index);

    stat(logFileName, &s);
    buf = (char*) malloc(s.st_size);
    fd = fopen(logFileName, "rb");
    int read = fread(buf, s.st_size, 1, fd);
    fclose(fd);

    fd = fopen(newFileName, "wb");
    if (!fd) {
		char message[BUFFER_SIZE];
		sprintf(message, "CANNOT WRITE TO FILE %s\n", newFileName);
        sendResponseMessage(message, 3);
        free(buf);
        return;
    }
    fwrite(buf, s.st_size, 1, fd);
    fclose(fd);

    free(buf);
    sendResponseMessage("OK\n", 3);
}

void SocketInterface::handleGetCycle() {
    char buf[32];
    sprintf(buf, "%ld\n", GameCycle);
    sendResponseMessage(buf, 3);
}

void SocketInterface::handleRandomSeed() {
    char* token = nextToken();
	
    if (token) {
        srand(atoi(token));
        sendResponseMessage("OK\n", 3);
    }
	else {
		sendResponseMessage("MISSING ARG seed\n", 3);
	}
}

void SocketInterface::handleActivity() {
	char buf[32];

	if (GamePaused) {
		// GamePaused defined in interface.h/interface.cpp
		sprintf(buf, "GAME PAUSED\n");
	}
	else if (GameRunning) {
		// GamePaused defined in interface.h/interface.cpp
		sprintf(buf, "GAME RUNNING\n");
	}
	else if (GameResult == GameNoResult) {
		sprintf(buf, "GAME WAITING\n");
	}
	else {
		// see results.h for results
		sprintf(buf, "GAME OVER\n");
	}
	sendResponseMessage(buf, 3);
}

void SocketInterface::handleGetMap() {
	int i, j;
	char buf[MaxMapWidth + 20];

	sprintf(buf, "(\n");
	sendResponseMessage(buf, 1);
	for (i = 0; i < Map.Info.MapHeight; i++) {
		buf[0] = '(';
		for (j = 0; j < Map.Info.MapWidth; j++) {
			if (Map.ForestOnMap(j, i)) {
				buf[j + 1] = 'T';
			}
			else if (Map.WaterOnMap(j, i)) {
				buf[j + 1] = '^';
			}
			else if (Map.CoastOnMap(j, i)) {
				buf[j + 1] = '+';
			}
			else if (Map.HumanWallOnMap(j, i)) {
				buf[j + 1] = 'h';
			}
			else if (Map.RockOnMap(j, i)) {
				buf[j + 1] = '#';
			}
			else if (Map.WallOnMap(j, i)) {
				buf[j + 1] = 'W';
			}
			else if (Map.OrcWallOnMap(j, i)) {
				buf[j + 1] = 'c';
			}
			else {
				buf[j + 1] = '0';
			}
		}
		sprintf(buf + Map.Info.MapWidth + 1, ")\n");
		sendResponseMessage(buf, 0);
	}
	sprintf(buf, ")\n");
	sendResponseMessage(buf, 2);
}

void SocketInterface::handleGetUnits() {
	char buf[256];
	int status;
	char argsbuf[80];

	sprintf(buf, "((\n"); // begin units list
	sendResponseMessage(buf, 1);
	for (int i = 0; i < NumUnits; ++i) {
		// Unit is dying when its action is UnitActionDie. It may still have
		// hit points left as its corpse decays on the battlefield. We
		// mark the hit points as 0 to indicate that the unit is dead.
		//
		// See unit.cpp LetUnitDie():
		// unit->Orders[0]->Action = UnitActionDie;
		//
		int hp = Units[i]->Variable[HP_INDEX].Value;
		if (Units[i]->Orders[0]->Action == UnitActionDie ||
				Units[i]->Destroyed) {
			hp = 0;
		}
		// UnitVisibleOnMap(Units[i], ThisPlayer)
		status = getUnitStatus(Units[i], argsbuf);
		sprintf(buf, "( %d . #s(unit player-id %d type %d loc (%d %d) hp %d "
				"r-amt %d kills %d status %d status-args (%s)))\n",
				UnitNumber(Units[i]),
				Units[i]->Player->Index,
				getUnitTypeNum(Units[i]->Type),
				Units[i]->X,
				Units[i]->Y,
				hp,
				Units[i]->ResourcesHeld,
				Units[i]->Variable[KILL_INDEX].Value,
				status,
				argsbuf);
		sendResponseMessage(buf, 0);
	}
	sendResponseMessage(")\n", 2);
}

void SocketInterface::handleGetGameInfo() {
	char buf[256];
    
	sprintf(buf, "#s(gameinfo player-id %d gold %d wood %d oil %d supply %d "
			"demand %d num-players %d width %d length %d)\n",
			ThisPlayer->Index, ThisPlayer->Resources[GoldCost],
			ThisPlayer->Resources[WoodCost], ThisPlayer->Resources[OilCost],
			ThisPlayer->Supply, ThisPlayer->Demand, NumPlayers,
			Map.Info.MapWidth, Map.Info.MapHeight);

	sendResponseMessage(buf, 3);
}

void SocketInterface::sendResponseMessage(const char* message, const char mode)
{
	int sent_bytes, total_sent_bytes = 0;

#if !defined(WIN32)
#if defined(__APPLE__)
	int flag;
	if (mode == 1 || mode == 3) // Set TCP cork
	{
		flag = 1;
		if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_NOPUSH,
				(char*) & flag, sizeof (flag)) == -1) {
			fprintf(stderr, "src/socket.c: SendResponseMessage setsockopt "
					"TCP_CORK.\n");
			ExitFatal(-1);
		}
	}
#else
	int flag;
	if (mode == 1 || mode == 3) // Set TCP cork
	{
		flag = 1;
		if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_CORK, (char*) & flag,
				sizeof (flag)) == -1) {
			fprintf(stderr, "sendResponseMessage setsockopt TCP_CORK.\n");
			ExitFatal(-1);
		}
	}
#endif
#endif // !WIN32

	int retries = 0;
	while (total_sent_bytes < (int) strlen(message)) {
		sent_bytes = NetSendTCP(interfaceSocket, message + total_sent_bytes,
				strlen(message) - total_sent_bytes);
		if (sent_bytes == -1) {
			if (retries > 5) {
				fprintf(stderr, "More than 5 retries. Exiting...\n");
				ExitFatal(-1);
			}
			fprintf(stderr, "Unable to send bytes in sendResponseMessage(). Retry %d...\n", retries+1);
			sent_bytes = 0;
		} else {
			total_sent_bytes += sent_bytes;
		}
	}

#if !defined(WIN32)
#if defined(__APPLE__)
	if (mode == 2 || mode == 3) // Unset TCP cork
	{
		flag = 0;
		if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_NOPUSH,
				(char*) & flag, sizeof (flag)) == -1) {
			fprintf(stderr, "src/socket.c: SendResponseMessage setsockopt "
					"TCP_CORK.\n");
			ExitFatal(-1);
		}
	}
#else
	if (mode == 2 || mode == 3) // Unset TCP cork
	{
		flag = 0;
		if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_CORK, (char*) & flag,
				sizeof (flag)) == -1) {
			fprintf(stderr, "sendResponseMessage setsockopt TCP_CORK.\n");
			ExitFatal(-1);
		}
	}
#endif
#endif // !WIN32
}

int SocketInterface::getUnitStatus(const CUnit* unit, char* statusargs) {
	// Special case: If a unit is inside another unit that is being built,
	// consider it to be building it.
	if (unit->Container &&
			unit->Container->Orders[0]->Action == UnitActionBuilt) {
		sprintf(statusargs, "%d", getUnitTypeNum(unit->Container->Type));
		return 4;
	}

	switch (unit->Orders[0]->Action) {
		case UnitActionStill: ///< unit stand still, does nothing
		case UnitActionStandGround: ///< unit stands ground
			if (getUnitTypeNum(unit->Type) == 2 && unit->ResourcesHeld == 100)
				// Peasant with some resource
				sprintf(statusargs, "%d", unit->CurrentResource);
			else
				*statusargs = '\0';
			return 1;

		case UnitActionMove: ///< unit moves to position/unit
			sprintf(statusargs, "%d %d", unit->Orders[0]->X,
					unit->Orders[0]->Y);
			return 2;

		case UnitActionAttack: ///< unit attacks position/unit
			if (unit->Orders[0]->Goal) {
				sprintf(statusargs, "%d", UnitNumber(unit->Orders[0]->Goal));
				return 3;
			}
			else { // Don't support attacking a position yet, status is 0.
				*statusargs = '\0';
				return 0;
			}

		case UnitActionBuild: ///< unit builds building
		case UnitActionTrain: ///< building is training
			sprintf(statusargs, "%d", getUnitTypeNum(unit->Orders[0]->Type));
			return 4;

		case UnitActionBuilt: ///< building is under construction
			*statusargs = '\0';
			return 5;

		case UnitActionRepair: ///< unit repairing
			if (unit->Orders[0]->Goal) {
				sprintf(statusargs, "%d", UnitNumber(unit->Orders[0]->Goal));
				return 6;
			}
			else { // Don't support repairing a position yet, status is 0.
				*statusargs = '\0';
				return 0;
			}

		case UnitActionResource: ///< unit harvesting resources
			sprintf(statusargs, "%d %d", unit->CurrentResource, unit->SubAction);
			return 7;

		case UnitActionReturnGoods: ///< unit returning any resource
			*statusargs = '\0';
			return 8;

		case UnitActionDie: ///< unit dies
			*statusargs = '\0';
			return 9;

		case UnitActionNone: ///< No valid action
		case UnitActionFollow: ///< unit follows units
		case UnitActionAttackGround: ///< unit attacks ground

		case UnitActionSpellCast: ///< unit casts spell
		case UnitActionUpgradeTo: ///< building is upgrading itself
		case UnitActionResearch: ///< building is researching spell

			// Compound actions
		case UnitActionBoard: ///< unit entering transporter
		case UnitActionUnload: ///< unit leaving transporter
		case UnitActionPatrol: ///< unit paroling area

		default:
			*statusargs = '\0';
			return 0;
	}
}

CUnit *SocketInterface::getUnitByID(int unitID) {
    int i;
    for (i = 0; i < NumUnits; i++)
        if (UnitNumber(Units[i]) == unitID)
            return Units[i];

    return NoUnitP;
}

int SocketInterface::checkForDisconnect() {
#ifdef USE_WINSOCK
	if (WSAGetLastError() == WSAECONNRESET) {
		return 1;
	}
	return 0;
#else
	if (errno == ENOTCONN) {
		return 1;
	}
	return 0;
#endif
}

int SocketInterface::getUnitTypeNum(CUnitType* type) {
	int j;
	for (j = 0; UnitTypes[j]; j++)
		if (UnitTypes[j]->Ident == type->Ident)
			break;
	return j;
}

SocketInterface::Request SocketInterface::toRequest(std::string token) {
	// stub
	printf("Request: %s\n", RequestStrings[atoi(token.c_str())]);
	return (Request) atoi(token.c_str());
}

SocketInterface::Command SocketInterface::toCommand(std::string token) {
	// stub
	return (Command) atoi(token.c_str());
}

SocketInterface::GetRequest SocketInterface::toGetRequest(std::string token) {
	// stub
	return (GetRequest) atoi(token.c_str());
}

/**
 * read the rest of the command.  This is sometimes needed to clear the 
 * buffer when aborting the processing of a command.
 */
void SocketInterface::consumeCommand() {
	while (nextToken());  // consume command.
}