
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if !defined(WIN32)
#include<netinet/tcp.h>
#endif

#include "stratagus.h"
#include "iocompat.h"
#include "interface.h"
#include "map.h"
#include "net_lowlevel.h"
#include "player.h"
#include "socket.h"
#include "unittype.h"
#include "unit.h"
#include "commands.h"
#include "video.h"
#include "results.h"
#include "ctype.h"    // toupper,tolower


#define DEFAULT_SOCKET_INTERFACE_PORT 4870
#define FIND_RESOURCE_RANGE 4


// Local functions
void CheckForConnection();
void CheckForCommands();
void HandleRequest();
void HandleActivity();
void HandleCommand();
void HandleGet();
void HandleKill();
void HandleLispGet();
void HandleLispGetGameInfo();
void HandleLispGetMap();
void HandleLispGetState();
void HandlePing();
void HandleQuit();
void HandleRestart();
char HandleNewMap();
void HandleSpeed();
void HandleTransition();
void HandleVideo();
void HandleWrite();
void HandleGetCycle();
void HandleRandomSeed();
void HandleGetGameState();
void HandleGetGameResult();
void SendResponseMessage(const char* message, const char mode);
int GetUnitStatus(const CUnit* unit, char* statusargs);
CUnit* GetUnitByID(int unitID);
int GetUnitTypeNum(CUnitType* type);
void FindAndGatherResource(CUnit* unit, int resourceType);
void ReturnResource(CUnit* unit);
int NetCheckForDisconnect();
void HarvestResAtLoc(int resourceType, int x, int y);

Socket listeningSocket;
Socket interfaceSocket;
#define RECV_BUFFER_SIZE 512
char recv_buffer[RECV_BUFFER_SIZE];
char temp_buffer[RECV_BUFFER_SIZE];

int SocketInterfacePort = DEFAULT_SOCKET_INTERFACE_PORT;
bool connected;
unsigned GameCyclesPerTransition = 30;
unsigned long LastPausedCycle = 0;
int CyclesPerVideoUpdate = 1;
int EnforcedVideoSyncSpeed = 100;
char WarpSpeed;

enum Requests {
    ACTIVITY, // is game waiting, running, paused, or over?
    COMMAND,
    EXIT,
    GET,
    KILL,
    LISPGET,
    MAP,
    PING,
    QUIT,
    RESTART,
    SPEED,
    TRANSITION,
    VIDEO,
    WRITE,
    CYCLE,
    Z, // Random number seed
    UNKNOWN
};
Requests to_request(char* token);

/**
 *
 */
void SocketInterfaceInit() {
    int result;

    DebugPrint("Initializing Socket Interface.\n");
    connected = false;

    NetInit();
    listeningSocket = NetOpenTCP(SocketInterfacePort);
    NetSetNonBlocking(listeningSocket);
    result = NetListenTCP(listeningSocket);

#ifdef WIN32
    DebugPrint("Socket interface initialized.\nListening on port %d, result = %d, error = %d\n" _C_ SocketInterfacePort _C_ result _C_ WSAGetLastError());
#else
    DebugPrint("Socket interface initialized.\nListening on port %d, result = %d\n" _C_ SocketInterfacePort _C_ result);
#endif
}

/**
 * call in game loop to check for client requests.
 */
void DoSocketInterface() {
    CheckForConnection();

    if (connected) { // Now we're connected, pause for client
        GamePaused = true;
        LastPausedCycle = GameCycle;
        CheckForCommands();

        if (VideoSyncSpeed < EnforcedVideoSyncSpeed) {
            VideoSyncSpeed = EnforcedVideoSyncSpeed;
            SetVideoSync();
        }
    } else {
        DebugPrint("Not connected.\n");
        GamePaused = false;
    }
}

void CheckForConnection() {
    Socket newSocket = NetAcceptTCP(listeningSocket);

    if (newSocket != INVALID_SOCKET) {
        if (connected) {
            SendResponseMessage("New connection made.  Goodbye.\n", 3);
            NetCloseTCP(interfaceSocket);
        }
        interfaceSocket = newSocket;

        DebugPrint("Established socket interface connection to %d.%d.%d.%d port %d\n" _C_ NIPQUAD(ntohl(NetLastHost)) _C_ NetLastPort);
        NetSetNonBlocking(interfaceSocket);
        connected = true;
    }
}

void CheckForCommands() {
    int result = strlen(recv_buffer);

    if (result == 0) // No pending commands
    { // Keep going till the commands have been received completely (the last character is an endline; recv_buffer better be big enough for at least one entire command)
        do {
            memset(temp_buffer, 0, RECV_BUFFER_SIZE);
            result = NetRecvTCP(interfaceSocket, temp_buffer, RECV_BUFFER_SIZE - strlen(recv_buffer) - 1);
            if (result > 0)
                strcat(recv_buffer, temp_buffer);
        } while (result > 0 && (strlen(recv_buffer) > 0 && strlen(recv_buffer) < RECV_BUFFER_SIZE - 1 && recv_buffer[strlen(recv_buffer) - 1] != '\n'));
    }

    if (result > 0)
        HandleRequest();
    else if (NetCheckForDisconnect()) {
        DebugPrint("Broken connection.");
        connected = false;
        NetCloseTCP(interfaceSocket);
    }
}

char* FirstToken() {
    strcpy(temp_buffer, recv_buffer);
    return strtok(temp_buffer, " \r\n");
}

char* NextToken() {
    return strtok(NULL, " \r\n");
}

void HandleRequest() {
    char* token;
    short keep_processing = 1;
    int offset;

    DebugPrint("Received request %s" _C_ recv_buffer);
    token = FirstToken();
    while (token != 0 && keep_processing) {
        switch (to_request(token)) {
            case ACTIVITY:
                HandleActivity();
                break;
            case COMMAND: // COMMAND
                HandleCommand();
                break;
            case EXIT: // EXIT
                HandleQuit();
                break;
            case GET: // GET
                HandleGet();
                break;
            case KILL:
                HandleKill();
                break;
            case LISPGET: // LISPGET {STATE | GAMEINFO | MAPINFO}
                HandleLispGet();
                break;
            case MAP: // Load new map
                if (HandleNewMap()) {
                    LastPausedCycle = 0;
                    keep_processing = 0;
                }
                break;
            case PING: // PING
                HandlePing();
                break;
            case QUIT: // QUIT
                HandleQuit();
                break;
            case RESTART: // RESTART / RESET
                HandleRestart();
                LastPausedCycle = 0;
                keep_processing = 0;
                break;
            case SPEED: // SPEED
                HandleSpeed();
                break;
            case TRANSITION: // TRANSITION
                HandleTransition();
                keep_processing = 0;
                break;
            case VIDEO: // VIDEO
                HandleVideo();
                break;
            case WRITE:
                HandleWrite();
                break;
            case CYCLE:
                HandleGetCycle();
                break;
            case Z:
                HandleRandomSeed();
                break;
            default:
                SendResponseMessage("Unknown command.\n", 3);
        }
        token = NextToken();
    }

    // Bring pending commands to the front of recv_buffer
    if (token != 0) {
        offset = token + strlen(token) - temp_buffer;
        temp_buffer[offset] = recv_buffer[offset];
        strcpy(recv_buffer, token);
    } else
        memset(recv_buffer, 0, RECV_BUFFER_SIZE); // Clear the command buffer
}

Requests to_request(char* token) {
    switch (toupper(token[0])) {
        case 'A':
            return ACTIVITY;
        case 'C': // COMMAND
            return COMMAND;
        case 'E': // EXIT
            return EXIT;
        case 'G': // GET
            return GET;
        case 'K':
            return KILL;
        case 'L': // LISPGET {STATE | GAMEINFO | MAPINFO}
            return LISPGET;
        case 'M': // Load new map
            return MAP;
        case 'P': // PING
            return PING;
        case 'Q': // QUIT
            return QUIT;
        case 'R': // RESTART / RESET
            return RESTART;
        case 'S': // SPEED
            return SPEED;
        case 'T': // TRANSITION
            return TRANSITION;
        case 'V': // VIDEO
            return VIDEO;
        case 'W':
            return WRITE;
        case 'X':
            return CYCLE;
        case 'Z':
            return Z;
        default:
            return UNKNOWN;
    }
}

void HandleCommand() {
    char* token;
    int unitID;
    CUnit* unit;
    int actionID;
    int resourceType;
    int x, y;
    char* typeID;
    CUnitType* type;
    int destID;
    CUnit* dest;

    token = NextToken();
    if (!token) return;
    unitID = atoi(token);
    unit = GetUnitByID(unitID);
    if (!unit) {
        SendResponseMessage("Unknown unit.\n", 3);
        return;
    }

    token = NextToken();
    if (!token) return;
    actionID = atoi(token);
    switch (actionID) {
        case 0: // NOOP
            //			SendResponseMessage("OK\n", 3);
            break;

        case 1: // STOP
            SendCommandStopUnit(unit);
            //			SendResponseMessage("OK\n", 3);
            break;

        case 2: // MoveTo <X> <Y>
            token = NextToken();
            if (token == NULL) {
                SendResponseMessage("Bad arguments to Move Action.\n", 3);
                return;
            }
            x = atoi(token);
            token = NextToken();
            if (token == NULL) {
                SendResponseMessage("Bad arguments to Move Action.\n", 3);
                return;
            }
            y = atoi(token);
            SendCommandMove(unit, x, y, 1);
            //			SendResponseMessage("OK\n", 3);
            break;

        case 3: // Build <Type> <X> <Y>
            token = NextToken();
            if (token == NULL) {
                SendResponseMessage("Bad arguments to Build Action.\n", 3);
                return;
            }
            typeID = token;
            type = UnitTypeByIdent(typeID);
            if (type == NULL) {
                SendResponseMessage("Unknown type to build.\n", 3);
                return;
            }
            // If it's a building, train the unit.
            if (unit->Type->Building)
                SendCommandTrainUnit(unit, type, 1);
            else // otherwise, build a building.
            {
                token = NextToken();
                if (token == NULL) {
                    SendResponseMessage("Bad arguments to Build Action.\n", 3);
                    return;
                }
                x = atoi(token);
                token = NextToken();
                if (token == NULL) {
                    SendResponseMessage("Bad arguments to Build Action.\n", 3);
                    return;
                }
                y = atoi(token);
                SendCommandBuildBuilding(unit, x, y, type, 1);
            }
            SendResponseMessage("OK\n", 3);
            break;

        case 4: // Attack <UnitID>
            token = NextToken();
            if (!token) return;
            destID = atoi(token);
            dest = GetUnitByID(destID);
            if (!dest) {
                SendResponseMessage("Unknown unit to attack.\n", 3);
                return;
            }
            SendCommandAttack(unit, 0, 0, dest, 1);
            SendResponseMessage("OK\n", 3);
            break;

        case 5: // Repair <UnitID>
            token = NextToken();
            if (!token) return;
            destID = atoi(token);
            dest = GetUnitByID(destID);
            if (!dest) {
                SendResponseMessage("Unknown unit to repair.\n", 3);
                return;
            }
            SendCommandRepair(unit, 0, 0, dest, 1);
            SendResponseMessage("OK\n", 3);
            break;

        case 6: // Harvest <ResourceType>
            token = NextToken();
            if (!token) return;
            resourceType = atoi(token); // GOLD = 1, WOOD = 2
            FindAndGatherResource(unit, resourceType);
            break;

        case 7: // Return resource
            ReturnResource(unit);
            break;

        case 8: // Harvest unit (gold/oil) <DestUnit>
            token = NextToken();
            if (!token) {
                SendResponseMessage("Invalid harvest argument.\n", 3);
                return;
            }
            destID = atoi(token);
            dest = GetUnitByID(destID);
            if (dest == NoUnitP) {
                SendResponseMessage("Invalid resource unit ID.\n", 3);
                return;
            }
            SendCommandResource(unit, dest, 1);
            SendResponseMessage("OK\n", 3);
            break;

        case 9: // Harvest terrain (wood) <X> <Y>
            token = NextToken();
            if (!token) {
                SendResponseMessage("Invalid harvest argument.\n", 3);
                return;
            }
            x = atoi(token);
            token = NextToken();
            if (!token) {
                SendResponseMessage("Invalid harvest argument.\n", 3);
                return;
            }
            y = atoi(token);
            SendCommandResourceLoc(unit, x, y, 1);
            SendResponseMessage("OK\n", 3);
            break;

        case 10: // Harvest location <ResourceType> <X> <Y>
            token = NextToken();
            if (!token) {
                SendResponseMessage("Invalid harvest argument.\n", 3);
                return;
            }
            resourceType = atoi(token); // GOLD = 1, WOOD = 2
            token = NextToken();
            if (!token) {
                SendResponseMessage("Invalid harvest argument.\n", 3);
                return;
            }
            x = atoi(token);
            token = NextToken();
            if (!token) {
                SendResponseMessage("Invalid harvest argument.\n", 3);
                return;
            }
            y = atoi(token);
            HarvestResAtLoc(resourceType, x, y);
            break;

        default:
            SendResponseMessage("Unknown command.\n", 3);
            return;
    }
}

void HandleGet() {
    int i;
    char buf[256];

    sprintf(buf, "Units: %d\n", NumUnits);
    SendResponseMessage(buf, 1);
    for (i = 0; i < NumUnits; ++i) {
        if (Units[i]->Orders[0]->Goal) {
            sprintf(buf, "Unit %d, Slot %d, Player %d: %s (%d, %d) HP: %d/%d ACTION: (%d Goal: %d (%d, %d))\n",
                    UnitNumber(Units[i]),
                    Units[i]->Slot,
                    Units[i]->Player->Index,
                    Units[i]->Type->Ident.c_str(),
                    Units[i]->X,
                    Units[i]->Y,
                    Units[i]->Variable[HP_INDEX].Value,
                    Units[i]->Stats->Variables[HP_INDEX].Value,
                    Units[i]->Orders[0]->Action,
                    UnitNumber(Units[i]->Orders[0]->Goal),
                    Units[i]->Orders[0]->Goal->X,
                    Units[i]->Orders[0]->Goal->Y);
        } else {
            sprintf(buf, "Unit %d, Slot %d, Player %d: %s (%d, %d) HP: %d/%d ACTION: %d\n",
                    UnitNumber(Units[i]),
                    Units[i]->Slot,
                    Units[i]->Player->Index,
                    Units[i]->Type->Ident.c_str(),
                    Units[i]->X,
                    Units[i]->Y,
                    Units[i]->Variable[HP_INDEX].Value,
                    Units[i]->Stats->Variables[HP_INDEX].Value,
                    Units[i]->Orders[0]->Action);
        }
        SendResponseMessage(buf, 0);
    }
    sprintf(buf, "Map Size: %dx%d\n", Map.Info.MapWidth, Map.Info.MapHeight);
    SendResponseMessage(buf, 2);
}

void HandleKill() {
    Exit(0);
}

void HandleLispGet() {
    char* token = NextToken();
    if (token == NULL) {
        SendResponseMessage("Bad command.\n", 3);
        return;
    }
    switch (token[0]) {
        case 'G': // LISPGET GAMEINFO
        case 'g':
            HandleLispGetGameInfo();
            break;
        case 'M': // LISPGET MAP
        case 'm':
            HandleLispGetMap();
            break;
        case 'S': // LISPGET STATE
        case 's':
            HandleLispGetState();
            break;
        default:
            SendResponseMessage("Unknown LISPGET argument.\n", 3);
    }
}

void HandleLispGetGameInfo() {
    char buf[256];
    sprintf(buf, "#s(gameinfo player-id %d width %d length %d)\n", ThisPlayer->Index, Map.Info.MapWidth, Map.Info.MapHeight);
    SendResponseMessage(buf, 3);
}

void HandleLispGetMap() {
    int i, j;
    char buf[MaxMapWidth + 20];

    sprintf(buf, "(\n");
    SendResponseMessage(buf, 1);
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
        SendResponseMessage(buf, 0);
    }
    sprintf(buf, ")\n");
    SendResponseMessage(buf, 2);
}

void HandleLispGetState() {
    int i;
    char buf[256];
    int status;
    char argsbuf[80];

    sprintf(buf, "((\n"); // begin units list
    SendResponseMessage(buf, 1);
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
        status = GetUnitStatus(Units[i], argsbuf); // UnitVisibleOnMap(Units[i], ThisPlayer)
        sprintf(buf, "( %d . #s(unit player-id %d type %d loc (%d %d) hp %d r-amt %d kills %d status %d status-args (%s)))\n",
                UnitNumber(Units[i]),
                Units[i]->Player->Index,
                GetUnitTypeNum(Units[i]->Type),
                Units[i]->X,
                Units[i]->Y,
                hp,
                Units[i]->ResourcesHeld,
                Units[i]->Variable[KILL_INDEX].Value,
                status,
                argsbuf);
        SendResponseMessage(buf, 0);
    }
    sprintf(buf, ") #S(global-state :gold %d :wood %d :oil %d :supply %d :demand %d))\n",
            ThisPlayer->Resources[GoldCost], ThisPlayer->Resources[WoodCost], ThisPlayer->Resources[OilCost], ThisPlayer->Supply, ThisPlayer->Demand);
    SendResponseMessage(buf, 2);
}

void HandlePing() {
    SendResponseMessage("PING\n", 3);
}

void HandleQuit() {
    SendResponseMessage("BYE\n", 3);
    connected = false;
    NetCloseTCP(interfaceSocket);
}

void HandleRestart() {
    // code from trigger.cpp StopGame().
    GameResult = GameRestart;
    GamePaused = true;
    GameRunning = false;
    SendResponseMessage("OK\n", 3);
}

char HandleNewMap() {
    char* token;

    token = NextToken();
    if (token) {
        GameResult = GameRestart;
        GamePaused = true;
        GameRunning = false;
        strcpy(CurrentMapPath, token);
        fprintf(stderr, "new map is '%s'\n", CurrentMapPath);
        SendResponseMessage("OK\n", 3);
        return 1;
    }
    return 0;
}

void HandleSpeed() {
    char* token = NextToken();
    if (token) {
        int speedup = atoi(token);
        if (speedup == 0)
            SendResponseMessage("Bad or missing argument to Game Speed command.\n", 3);
        else {
            if (speedup == -1)
                WarpSpeed = 1;
            else {
                WarpSpeed = 0;
                EnforcedVideoSyncSpeed = speedup;
                VideoSyncSpeed = EnforcedVideoSyncSpeed;
                SetVideoSync();
            }
            SendResponseMessage("OK\n", 3);
        }
    }
}

void HandleTransition() {
    GamePaused = false;

    char* token = NextToken();
    if (token) {
        unsigned num_cycles = (unsigned) atoi(token);
        if (num_cycles == 0)
            SendResponseMessage("Bad or missing argument to Transition command.\n", 3);
        else {
            GameCyclesPerTransition = num_cycles;
        }
    }
}

void HandleVideo() {
    char* token;
    int speedup;

    token = NextToken();
    if (token) {
        speedup = atoi(token);
        if (speedup == 0)
            SendResponseMessage("Bad or missing argument to Video speedup command.\n", 3);
        else {
            CyclesPerVideoUpdate = speedup;
            SendResponseMessage("OK\n", 3);
        }
    }
}

void HandleWrite() {
    char* newFileName;
    char logFileName[PATH_MAX];
    FILE *fd;
    char *buf;
    struct stat s;

    newFileName = NextToken();
    if (!newFileName) return;

    sprintf(logFileName, "%s/logs/log_of_stratagus_%d.log", GameName.c_str(), ThisPlayer->Index);

    stat(logFileName, &s);
    buf = (char*) malloc(s.st_size);
    fd = fopen(logFileName, "rb");
    fread(buf, s.st_size, 1, fd);
    fclose(fd);

    fd = fopen(newFileName, "wb");
    if (!fd) {
        SendResponseMessage("Cannot write to file.\n", 3);
        free(buf);
        return;
    }
    fwrite(buf, s.st_size, 1, fd);
    fclose(fd);

    free(buf);
    SendResponseMessage("OK\n", 3);
}

void HandleGetCycle() {
    char buf[32];
    sprintf(buf, "%ld\n", GameCycle);
    SendResponseMessage(buf, 3);
}

void HandleRandomSeed() {
    char* token;

    token = NextToken();
    if (token) {
        srand(atoi(token));
        SendResponseMessage("OK\n", 3);
    }
}

/**
 * Return game activity state: waiting, running, paused, or game over.
 */
void HandleActivity() {
    char buf[32];
    if (GamePaused) {
        // GamePaused defined in interface.h/interface.cpp
        sprintf(buf, "GAME PAUSED\n");
    } else if (GameRunning) {
        // GamePaused defined in interface.h/interface.cpp
        sprintf(buf, "GAME RUNNING\n");
    } else if (GameResult == GameNoResult) {
        sprintf(buf, "GAME WAITING\n");
    } else {
        sprintf(buf, "GAME OVER\n"); // see results.h for results
    }
    SendResponseMessage(buf, 3);
}

void HandleGetGameResult() {
    // GameResult defined in results.h/game.h
}

void SocketInterfaceExit() {
    if (connected)
        NetCloseTCP(interfaceSocket);
    NetCloseTCP(listeningSocket);
}

void SendResponseMessage(const char* message, const char mode) {
    int sent_bytes, total_sent_bytes = 0;

#if !defined(WIN32)
#if defined(__APPLE__)
    int flag;
    if (mode == 1 || mode == 3) // Set TCP cork
    {
        flag = 1;
        if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_NOPUSH, (char*) & flag, sizeof (flag)) == -1) {
            fprintf(stderr, "src/socket.c: SendResponseMessage setsockopt TCP_CORK.\n");
            ExitFatal(-1);
        }
    }
#else
    int flag;
    if (mode == 1 || mode == 3) // Set TCP cork
    {
        flag = 1;
        if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_CORK, (char*) & flag, sizeof (flag)) == -1) {
            fprintf(stderr, "src/socket.c: SendResponseMessage setsockopt TCP_CORK.\n");
            ExitFatal(-1);
        }
    }
#endif
#endif

    while (total_sent_bytes < (int) strlen(message)) {
        sent_bytes = NetSendTCP(interfaceSocket, message + total_sent_bytes, strlen(message) - total_sent_bytes);
        if (sent_bytes == -1) {
            fprintf(stderr, "src/socket.cpp: unable to send bytes in SendResponseMessage().\n");
            ExitFatal(-1);
        }

        total_sent_bytes += sent_bytes;
    }

#if !defined(WIN32)
#if defined(__APPLE__)
    if (mode == 2 || mode == 3) // Unset TCP cork
    {
        flag = 0;
        if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_NOPUSH, (char*) & flag, sizeof (flag)) == -1) {
            fprintf(stderr, "src/socket.c: SendResponseMessage setsockopt TCP_CORK.\n");
            ExitFatal(-1);
        }
    }
#else
    if (mode == 2 || mode == 3) // Unset TCP cork
    {
        flag = 0;
        if (setsockopt(interfaceSocket, IPPROTO_TCP, TCP_CORK, (char*) & flag, sizeof (flag)) == -1) {
            fprintf(stderr, "src/socket.c: SendResponseMessage setsockopt TCP_CORK.\n");
            ExitFatal(-1);
        }
    }
#endif
#endif
}

int GetUnitStatus(const CUnit* unit, char* statusargs) { // Special case: If a unit is inside another unit that is being built,
    // consider it to be building it.
    if (unit->Container && unit->Container->Orders[0]->Action == UnitActionBuilt) {
        sprintf(statusargs, "%d", GetUnitTypeNum(unit->Container->Type));
        return 4;
    }

    switch (unit->Orders[0]->Action) {
        case UnitActionStill: ///< unit stand still, does nothing
        case UnitActionStandGround: ///< unit stands ground
            if (GetUnitTypeNum(unit->Type) == 2 && unit->ResourcesHeld == 100) // Peasant with some resource
                sprintf(statusargs, "%d", unit->CurrentResource);
            else
                *statusargs = '\0';
            return 1;

        case UnitActionMove: ///< unit moves to position/unit
            sprintf(statusargs, "%d %d", unit->Orders[0]->X, unit->Orders[0]->Y);
            return 2;

        case UnitActionAttack: ///< unit attacks position/unit
            if (unit->Orders[0]->Goal) {
                sprintf(statusargs, "%d", UnitNumber(unit->Orders[0]->Goal));
                return 3;
            } else { // Don't support attacking a position yet, status is 0.
                *statusargs = '\0';
                return 0;
            }

        case UnitActionBuild: ///< unit builds building
        case UnitActionTrain: ///< building is training
            sprintf(statusargs, "%d", GetUnitTypeNum(unit->Orders[0]->Type));
            return 4;

        case UnitActionBuilt: ///< building is under construction
            *statusargs = '\0';
            return 5;

        case UnitActionRepair: ///< unit repairing
            if (unit->Orders[0]->Goal) {
                sprintf(statusargs, "%d", UnitNumber(unit->Orders[0]->Goal));
                return 6;
            } else { // Don't support repairing a position yet, status is 0.
                *statusargs = '\0';
                return 0;
            }

        case UnitActionResource: ///< unit harvesting resources
            sprintf(statusargs, "%d", unit->CurrentResource);
            return 7;

        case UnitActionReturnGoods: ///< unit returning any resource
            *statusargs = '\0';
            return 8;

        case UnitActionNone: ///< No valid action
        case UnitActionFollow: ///< unit follows units
        case UnitActionAttackGround: ///< unit attacks ground
        case UnitActionDie: ///< unit dies
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

CUnit* GetUnitByID(int unitID) {
    int i;
    for (i = 0; i < NumUnits; i++)
        if (UnitNumber(Units[i]) == unitID)
            return Units[i];

    return NoUnitP;
}

int GetUnitTypeNum(CUnitType* type) {
    int j;
    for (j = 0; UnitTypes[j]; j++)
        if (UnitTypes[j]->Ident == type->Ident)
            break;
    return j;
}

void FindAndGatherResource(CUnit* unit, int resourceType) {
    CUnit* dest;
    int x, y, result;

    switch (resourceType) {
        case GoldCost:
            dest = UnitFindResource(unit, unit->X, unit->Y, FIND_RESOURCE_RANGE, resourceType);
            if (dest != NoUnitP) {
                SendCommandResource(unit, dest, 1);
                // TODO: don't know what StopResourceFlag corresponds to in 2.2.4 code
                // unit->StopResourceFlag = 1;
            } else {
                SendResponseMessage("Unable to find gold within range.\n", 3);
                return;
            }
            break;
        case WoodCost:
            result = FindTerrainType(unit->Type->MovementMask, MapFieldForest, 0, FIND_RESOURCE_RANGE, unit->Player, unit->X, unit->Y, &x, &y);
            if (result) {
                SendCommandResourceLoc(unit, x, y, 1);
                // TODO: don't know what StopResourceFlag corresponds to in 2.2.4 code
                //unit->StopResourceFlag = 1;
            } else {
                SendResponseMessage("Unable to find wood within range.\n", 3);
                return;
            }
            break;
        default:
            SendResponseMessage("Unknown resource type\n", 3);
    }
    SendResponseMessage("OK\n", 3);
}

void ReturnResource(CUnit* unit) {
    CUnit* dest;

    if (unit->CurrentResource == 0 || unit->ResourcesHeld == 0) {
        SendResponseMessage("Unit has no resources to return.\n", 3);
        return;
    }

    dest = FindDeposit(unit, unit->X, unit->Y, FIND_RESOURCE_RANGE, unit->CurrentResource);
    if (dest != NoUnitP) {
        SendCommandReturnGoods(unit, dest, 1);
        // TODO: don't know what StopResourceFlag corresponds to in 2.2.4 code
        // unit->StopResourceFlag = 1;
    } else {
        SendResponseMessage("Unable to find location to return resource within range.\n", 3);
        return;
    }
    SendResponseMessage("OK\n", 3);
}

void HarvestResAtLoc(int resourceType, int x, int y) {
    CUnit *unit;
    CUnit *dest;

    if (resourceType == GoldCost || resourceType == OilCost) {
        dest = UnitFindResource(unit, x, y, FIND_RESOURCE_RANGE, resourceType);
        if (dest != NoUnitP) {
            SendCommandResource(unit, dest, 1);
        } else {
            SendResponseMessage("Unable to find resource in range.\n", 3);
            return;
        }
    } else if (resourceType == WoodCost) {
        SendCommandResourceLoc(unit, x, y, 1);
    } else {
        SendResponseMessage("Unknown resource type\n", 3);
        return;
    }
    SendResponseMessage("OK\n", 3);
}

// BDK - copied from lowlevel.c
#ifdef USE_WINSOCK

int NetCheckForDisconnect() {
    if (WSAGetLastError() == WSAECONNRESET) {
        return 1;
    }
    return 0;
}
#else  // USE_WINSOCK

int NetCheckForDisconnect() {
    if (errno == ENOTCONN) {
        return 1;
    }
    return 0;
}
#endif // USE_WINSOCK
