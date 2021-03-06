//       _________ __                 __
//      /   _____//  |_____________ _/  |______     ____  __ __  ______
//      \_____  \\   __\_  __ \__  \\   __\__  \   / ___\|  |  \/  ___/
//      /        \|  |  |  | \// __ \|  |  / __ \_/ /_/  >  |  /\___ |
//     /_______  /|__|  |__|  (____  /__| (____  /\___  /|____//____  >
//             \/                  \/          \//_____/            \/
//  ______________________                           ______________________
//                        T H E   W A R   B E G I N S
//         Stratagus - A free fantasy real time strategy game engine
//
/**@name commands.cpp - Global command handler - network support. */
//
//      (c) Copyright 2000-2006 by Lutz Sammer, Andreas Arens, and Jimmy Salmon.
//
//      This program is free software; you can redistribute it and/or modify
//      it under the terms of the GNU General Public License as published by
//      the Free Software Foundation; only version 2 of the License.
//
//      This program is distributed in the hope that it will be useful,
//      but WITHOUT ANY WARRANTY; without even the implied warranty of
//      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//      GNU General Public License for more details.
//
//      You should have received a copy of the GNU General Public License
//      along with this program; if not, write to the Free Software
//      Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
//      02111-1307, USA.
//
//      $Id: commands.cpp 7883 2007-01-14 18:51:33Z jsalmon3 $

//@{

//----------------------------------------------------------------------------
// Includes
//----------------------------------------------------------------------------

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "stratagus.h"
#include "unit.h"
#include "unittype.h"
#include "upgrade.h"
#include "map.h"
#include "actions.h"
#include "player.h"
#include "network.h"
#include "netconnect.h"
#include "script.h"
#include "commands.h"
#include "interface.h"
#include "iolib.h"
#include "iocompat.h"
#include "settings.h"
#include "spells.h"

//----------------------------------------------------------------------------
// Declaration
//----------------------------------------------------------------------------

//----------------------------------------------------------------------------
// Structures
//----------------------------------------------------------------------------

/**
**  LogEntry structure.
*/
class LogEntry {
public:
	LogEntry() : GameCycle(0), Flush(0), PosX(0), PosY(0), DestUnitNumber(0),
		Num(0), SyncRandSeed(0), Next(NULL)
	{
		UnitNumber = 0;
	}

	unsigned long GameCycle;
	int UnitNumber;
	std::string UnitIdent;
	std::string Action;
	int Flush;
	int PosX;
	int PosY;
	int DestUnitNumber;
	std::string Value;
	int Num;
	unsigned SyncRandSeed;
	LogEntry *Next;
};

/**
**  Multiplayer Player definition
*/
class MPPlayer {
public:
	MPPlayer() : Race(0), Team(0), Type(0) {}

	std::string Name;
	int Race;
	int Team;
	int Type;
};

/**
** Full replay structure (definition + logs)
*/
class FullReplay {
public:
	FullReplay() :
		MapId(0), Type(0), Race(0), LocalPlayer(0),
		Resource(0), NumUnits(0), Difficulty(0), NoFow(false), RevealMap(0),
		MapRichness(0), GameType(0), Opponents(0), Commands(NULL)
	{
		memset(Engine, 0, sizeof(Engine));
		memset(Network, 0, sizeof(Network));
	}
	std::string Comment1;
	std::string Comment2;
	std::string Comment3;
	std::string Date;
	std::string Map;
	std::string MapPath;
	unsigned MapId;

	int Type;
	int Race;
	int LocalPlayer;
	MPPlayer Players[PlayerMax];

	int Resource;
	int NumUnits;
	int Difficulty;
	bool NoFow;
	int RevealMap;
	int MapRichness;
	int GameType;
	int Opponents;
	int Engine[3];
	int Network[3];
	LogEntry *Commands;
};

//----------------------------------------------------------------------------
// Constants
//----------------------------------------------------------------------------


//----------------------------------------------------------------------------
// Variables
//----------------------------------------------------------------------------

int CommandLogDisabled;            /// True if command log is off
ReplayType ReplayGameType;         /// Replay game type
static int DisabledLog;            /// Disabled log for replay
static CFile *LogFile;            /// Replay log file
static unsigned long NextLogCycle; /// Next log cycle number
static int InitReplay;             /// Initialize replay
static FullReplay *CurrentReplay;
static LogEntry *ReplayStep;

static void AppendLog(LogEntry *log, CFile *dest);

//----------------------------------------------------------------------------
// Log commands
//----------------------------------------------------------------------------

/**
** Allocate & fill a new FullReplay structure, from GameSettings.
**
** @return A new FullReplay structure
*/
static FullReplay *StartReplay(void)
{
	FullReplay *replay;
	char *s;
	time_t now;
	char *s1;

	replay = new FullReplay;

	time(&now);
	s = ctime(&now);
	if ((s1 = strchr(s, '\n'))) {
		*s1 = '\0';
	}

	replay->Comment1 = "Generated by Stratagus Version " VERSION "";
	replay->Comment2 = "Visit http://stratagus.org for more information";
	replay->Comment3 = "$Id: commands.cpp 7883 2007-01-14 18:51:33Z jsalmon3 $";

	if (GameSettings.NetGameType == SettingsSinglePlayerGame) {
		replay->Type = ReplaySinglePlayer;
	} else {
		replay->Type = ReplayMultiPlayer;
	}

	for (int i = 0; i < PlayerMax; ++i) {
		replay->Players[i].Name = Players[i].Name;
		replay->Players[i].Race = GameSettings.Presets[i].Race;
		replay->Players[i].Team = GameSettings.Presets[i].Team;
		replay->Players[i].Type = GameSettings.Presets[i].Type;
	}

	replay->LocalPlayer = ThisPlayer->Index;

	replay->Date = s;
	replay->Map = Map.Info.Description;
	replay->MapId = (signed int)Map.Info.MapUID;
	replay->MapPath = CurrentMapPath;
	replay->Resource = GameSettings.Resources;
	replay->NumUnits = GameSettings.NumUnits;
	replay->Difficulty = GameSettings.Difficulty;
	replay->NoFow = GameSettings.NoFogOfWar;
	replay->GameType = GameSettings.GameType;
	replay->RevealMap = GameSettings.RevealMap;
	replay->MapRichness = GameSettings.MapRichness;
	replay->Opponents = GameSettings.Opponents;

	replay->Engine[0] = StratagusMajorVersion;
	replay->Engine[1] = StratagusMinorVersion;
	replay->Engine[2] = StratagusPatchLevel;

	replay->Network[0] = NetworkProtocolMajorVersion;
	replay->Network[1] = NetworkProtocolMinorVersion;
	replay->Network[2] = NetworkProtocolPatchLevel;
	return replay;
}

/**
**  Applies settings the game used at the start of the replay
*/
static void ApplyReplaySettings(void)
{
	if (CurrentReplay->Type == ReplayMultiPlayer) {
		ExitNetwork1();
		NetPlayers = 2;
		GameSettings.NetGameType = SettingsMultiPlayerGame;

		ReplayGameType = ReplayMultiPlayer;
		NetLocalPlayerNumber = CurrentReplay->LocalPlayer;
	} else {
		GameSettings.NetGameType = SettingsSinglePlayerGame;
		ReplayGameType = ReplaySinglePlayer;
	}

	for (int i = 0; i < PlayerMax; ++i) {
		GameSettings.Presets[i].Race = CurrentReplay->Players[i].Race;
		GameSettings.Presets[i].Team = CurrentReplay->Players[i].Team;
		GameSettings.Presets[i].Type = CurrentReplay->Players[i].Type;
	}

	if (strcpy_s(CurrentMapPath, sizeof(CurrentMapPath), CurrentReplay->MapPath.c_str()) != 0) {
		fprintf(stderr, "Replay map path is too long\n");
		// FIXME: need to handle errors better
		Exit(1);
	}
	GameSettings.Resources = CurrentReplay->Resource;
	GameSettings.NumUnits = CurrentReplay->NumUnits;
	GameSettings.Difficulty = CurrentReplay->Difficulty;
	Map.NoFogOfWar = GameSettings.NoFogOfWar = CurrentReplay->NoFow;
	GameSettings.GameType = CurrentReplay->GameType;
	FlagRevealMap = GameSettings.RevealMap = CurrentReplay->RevealMap;
	GameSettings.MapRichness = CurrentReplay->MapRichness;
	GameSettings.Opponents = CurrentReplay->Opponents;

	// FIXME : check engine version
	// FIXME : FIXME: check network version
	// FIXME : check mapid
}

/**
**  Free a replay from memory
**
**  @param replay  Pointer to the replay to be freed
*/
static void DeleteReplay(FullReplay *replay)
{
	LogEntry *log;
	LogEntry *next;

	log = replay->Commands;
	while (log) {
		next = log->Next;
		delete log;
		log = next;
	}

	delete replay;
}

static void PrintLogCommand(LogEntry *log, CFile *dest)
{
	dest->printf("Log( { ");
	dest->printf("GameCycle = %lu, ", log->GameCycle);
	if (log->UnitNumber != -1) {
		dest->printf("UnitNumber = %d, ", log->UnitNumber);
	}
	if (!log->UnitIdent.empty()) {
		dest->printf("UnitIdent = \"%s\", ", log->UnitIdent.c_str());
	}
	dest->printf("Action = \"%s\", ", log->Action.c_str());
	dest->printf("Flush = %d, ", log->Flush);
	if (log->PosX != -1 || log->PosY != -1) {
		dest->printf("PosX = %d, PosY = %d, ", log->PosX, log->PosY);
	}
	if (log->DestUnitNumber != -1) {
		dest->printf("DestUnitNumber = %d, ", log->DestUnitNumber);
	}
	if (!log->Value.empty()) {
		dest->printf("Value = [[%s]], ", log->Value.c_str());
	}
	if (log->Num != -1) {
		dest->printf("Num = %d, ", log->Num);
	}
	dest->printf("SyncRandSeed = %d } )\n", (signed)log->SyncRandSeed);
}

/**
**  Output the FullReplay list to dest file
**
**  @param dest  The file to output to
*/
static void SaveFullLog(CFile *dest)
{
	LogEntry *log;
	int i;

	dest->printf("ReplayLog( {\n");
	dest->printf("  Comment1 = \"%s\",\n", CurrentReplay->Comment1.c_str());
	dest->printf("  Comment2 = \"%s\",\n", CurrentReplay->Comment2.c_str());
	dest->printf("  Comment3 = \"%s\",\n", CurrentReplay->Comment3.c_str());
	dest->printf("  Date = \"%s\",\n", CurrentReplay->Date.c_str());
	dest->printf("  Map = \"%s\",\n", CurrentReplay->Map.c_str());
	dest->printf("  MapPath = \"%s\",\n", CurrentReplay->MapPath.c_str());
	dest->printf("  MapId = %u,\n", CurrentReplay->MapId);
	dest->printf("  Type = %d,\n", CurrentReplay->Type);
	dest->printf("  Race = %d,\n", CurrentReplay->Race);
	dest->printf("  LocalPlayer = %d,\n", CurrentReplay->LocalPlayer);
	dest->printf("  Players = {\n");
	for (i = 0; i < PlayerMax; ++i) {
		if (!CurrentReplay->Players[i].Name.empty()) {
			dest->printf("\t{ Name = \"%s\",", CurrentReplay->Players[i].Name.c_str());
		} else {
			dest->printf("\t{");
		}
		dest->printf(" Race = %d,", CurrentReplay->Players[i].Race);
		dest->printf(" Team = %d,", CurrentReplay->Players[i].Team);
		dest->printf(" Type = %d }%s", CurrentReplay->Players[i].Type,
			i != PlayerMax - 1 ? ",\n" : "\n");
	}
	dest->printf("  },\n");
	dest->printf("  Resource = %d,\n", CurrentReplay->Resource);
	dest->printf("  NumUnits = %d,\n", CurrentReplay->NumUnits);
	dest->printf("  Difficulty = %d,\n", CurrentReplay->Difficulty);
	dest->printf("  NoFow = %s,\n", CurrentReplay->NoFow ? "true" : "false");
	dest->printf("  RevealMap = %d,\n", CurrentReplay->RevealMap);
	dest->printf("  GameType = %d,\n", CurrentReplay->GameType);
	dest->printf("  Opponents = %d,\n", CurrentReplay->Opponents);
	dest->printf("  MapRichness = %d,\n", CurrentReplay->MapRichness);
	dest->printf("  Engine = { %d, %d, %d },\n",
		CurrentReplay->Engine[0], CurrentReplay->Engine[1], CurrentReplay->Engine[2]);
	dest->printf("  Network = { %d, %d, %d }\n",
		CurrentReplay->Network[0], CurrentReplay->Network[1], CurrentReplay->Network[2]);
	dest->printf("} )\n");
	log = CurrentReplay->Commands;
	while (log) {
		PrintLogCommand(log, dest);
		log = log->Next;
	}
}

/**
**  Append the LogEntry structure at the end of currentLog, and to LogFile
**
**  @param log   Pointer the replay log entry to be added
**  @param dest  The file to output to
*/
static void AppendLog(LogEntry *log, CFile *dest)
{
	LogEntry **last;

	// Append to linked list
	last = &CurrentReplay->Commands;
	while (*last) {
		last = &(*last)->Next;
	}

	*last = log;
	log->Next = 0;

	// Append to file
	if (!dest) {
		return;
	}

	PrintLogCommand(log, dest);
	dest->flush();
}

/**
** Log commands into file.
**
** This could later be used to recover, crashed games.
**
** @param action    Command name (move,attack,...).
** @param unit      Unit that receive the command.
** @param flush     Append command or flush old commands.
** @param x         optional X map position.
** @param y         optional y map position.
** @param dest      optional destination unit.
** @param value     optional command argument (unit-type,...).
** @param num       optional number argument
*/
void CommandLog(const char *action, const CUnit *unit, int flush,
	int x, int y, const CUnit *dest, const char *value, int num)
{
	LogEntry *log;

	if (CommandLogDisabled) { // No log wanted
		return;
	}

	//
	// Create and write header of log file. The player number is added
	// to the save file name, to test more than one player on one computer.
	//
	if (!LogFile) {
		char buf[PATH_MAX];
		char logsdir[PATH_MAX];

#ifdef USE_WIN32
		strcpy_s(logsdir, sizeof(logsdir), GameName.c_str());
		mkdir(logsdir);
		strcat_s(logsdir, sizeof(logsdir), "/logs");
		mkdir(logsdir);
#else
		sprintf(logsdir, "%s/%s", getenv("HOME"), STRATAGUS_HOME_PATH);
		mkdir(logsdir, 0777);
		strcat_s(logsdir, sizeof(logsdir), "/");
		strcat_s(logsdir, sizeof(logsdir), GameName.c_str());
		mkdir(logsdir, 0777);
		strcat_s(logsdir, sizeof(logsdir), "/logs");
		mkdir(logsdir, 0777);
#endif

		sprintf(buf, "%s/log_of_stratagus_%d.log", logsdir, ThisPlayer->Index);
		LogFile = new CFile;
		if (LogFile->open(buf, CL_OPEN_WRITE) == -1) {
			// don't retry for each command
			CommandLogDisabled = 0;
			delete LogFile;
			LogFile = NULL;
			return;
		}

		if (CurrentReplay) {
			SaveFullLog(LogFile);
		}
	}

	if (!CurrentReplay) {
		CurrentReplay = StartReplay();

		SaveFullLog(LogFile);
	}

	if (!action) {
		return;
	}

	log = new LogEntry;

	//
	// Frame, unit, (type-ident only to be better readable).
	//
	log->GameCycle = GameCycle;

	log->UnitNumber = (unit ? UnitNumber(unit) : -1);
	log->UnitIdent = (unit ? unit->Type->Ident.c_str() : "");

	log->Action = action;
	log->Flush = flush;

	//
	// Coordinates given.
	//
	log->PosX = x;
	log->PosY = y;

	//
	// Destination given.
	//
	log->DestUnitNumber = (dest ? UnitNumber(dest) : -1);

	//
	// Value given.
	//
	log->Value = (value ? value : "");

	//
	// Number given.
	//
	log->Num = num;

	log->SyncRandSeed = SyncRandSeed;

	// Append it to ReplayLog list
	AppendLog(log, LogFile);
}

/**
** Parse log
*/
static int CclLog(lua_State *l)
{
	LogEntry *log;
	LogEntry **last;
	const char *value;

	LuaCheckArgs(l, 1);
	if (!lua_istable(l, 1)) {
		LuaError(l, "incorrect argument");
	}

	Assert(CurrentReplay);

	log = new LogEntry;
	log->UnitNumber = -1;
	log->PosX = -1;
	log->PosY = -1;
	log->DestUnitNumber = -1;
	log->Num = -1;

	lua_pushnil(l);
	while (lua_next(l, 1)) {
		value = LuaToString(l, -2);
		if (!strcmp(value, "GameCycle")) {
			log->GameCycle = LuaToNumber(l, -1);
		} else if (!strcmp(value, "UnitNumber")) {
			log->UnitNumber = LuaToNumber(l, -1);
		} else if (!strcmp(value, "UnitIdent")) {
			log->UnitIdent = LuaToString(l, -1);
		} else if (!strcmp(value, "Action")) {
			log->Action = LuaToString(l, -1);
		} else if (!strcmp(value, "Flush")) {
			log->Flush = LuaToNumber(l, -1);
		} else if (!strcmp(value, "PosX")) {
			log->PosX = LuaToNumber(l, -1);
		} else if (!strcmp(value, "PosY")) {
			log->PosY = LuaToNumber(l, -1);
		} else if (!strcmp(value, "DestUnitNumber")) {
			log->DestUnitNumber = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Value")) {
			log->Value = LuaToString(l, -1);
		} else if (!strcmp(value, "Num")) {
			log->Num = LuaToNumber(l, -1);
		} else if (!strcmp(value, "SyncRandSeed")) {
			log->SyncRandSeed = (unsigned)LuaToNumber(l, -1);
		} else {
			LuaError(l, "Unsupported key: %s" _C_ value);
		}
		lua_pop(l, 1);
	}

	// Append to linked list
	last = &CurrentReplay->Commands;
	while (*last) {
		last = &(*last)->Next;
	}

	*last = log;

	return 0;
}

/**
** Parse replay-log
*/
static int CclReplayLog(lua_State *l)
{
	FullReplay *replay;
	const char *value;
	int j;

	LuaCheckArgs(l, 1);
	if (!lua_istable(l, 1)) {
		LuaError(l, "incorrect argument");
	}

	Assert(CurrentReplay == NULL);

	replay = new FullReplay;

	lua_pushnil(l);
	while (lua_next(l, 1) != 0) {
		value = LuaToString(l, -2);
		if (!strcmp(value, "Comment1")) {
			replay->Comment1 = LuaToString(l, -1);
		} else if (!strcmp(value, "Comment2")) {
			replay->Comment2 = LuaToString(l, -1);
		} else if (!strcmp(value, "Comment3")) {
			replay->Comment3 = LuaToString(l, -1);
		} else if (!strcmp(value, "Date")) {
			replay->Date = LuaToString(l, -1);
		} else if (!strcmp(value, "Map")) {
			replay->Map = LuaToString(l, -1);
		} else if (!strcmp(value, "MapPath")) {
			replay->MapPath = LuaToString(l, -1);
		} else if (!strcmp(value, "MapId")) {
			replay->MapId = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Type")) {
			replay->Type = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Race")) {
			replay->Race = LuaToNumber(l, -1);
		} else if (!strcmp(value, "LocalPlayer")) {
			replay->LocalPlayer = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Players")) {
			if (!lua_istable(l, -1) || luaL_getn(l, -1) != PlayerMax) {
				LuaError(l, "incorrect argument");
			}
			for (j = 0; j < PlayerMax; ++j) {
				int top;

				lua_rawgeti(l, -1, j + 1);
				if (!lua_istable(l, -1)) {
					LuaError(l, "incorrect argument");
				}
				top = lua_gettop(l);
				lua_pushnil(l);
				while (lua_next(l, top) != 0) {
					value = LuaToString(l, -2);
					if (!strcmp(value, "Name")) {
						replay->Players[j].Name = LuaToString(l, -1);
					} else if (!strcmp(value, "Race")) {
						replay->Players[j].Race = LuaToNumber(l, -1);
					} else if (!strcmp(value, "Team")) {
						replay->Players[j].Team = LuaToNumber(l, -1);
					} else if (!strcmp(value, "Type")) {
						replay->Players[j].Type = LuaToNumber(l, -1);
					} else {
						LuaError(l, "Unsupported key: %s" _C_ value);
					}
					lua_pop(l, 1);
				}
				lua_pop(l, 1);
			}
		} else if (!strcmp(value, "Resource")) {
			replay->Resource = LuaToNumber(l, -1);
		} else if (!strcmp(value, "NumUnits")) {
			replay->NumUnits = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Difficulty")) {
			replay->Difficulty = LuaToNumber(l, -1);
		} else if (!strcmp(value, "NoFow")) {
			replay->NoFow = LuaToBoolean(l, -1);
		} else if (!strcmp(value, "RevealMap")) {
			replay->RevealMap = LuaToNumber(l, -1);
		} else if (!strcmp(value, "GameType")) {
			replay->GameType = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Opponents")) {
			replay->Opponents = LuaToNumber(l, -1);
		} else if (!strcmp(value, "MapRichness")) {
			replay->MapRichness = LuaToNumber(l, -1);
		} else if (!strcmp(value, "Engine")) {
			if (!lua_istable(l, -1) || luaL_getn(l, -1) != 3) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, -1, 1);
			replay->Engine[0] = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, -1, 2);
			replay->Engine[1] = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, -1, 3);
			replay->Engine[2] = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "Network")) {
			if (!lua_istable(l, -1) || luaL_getn(l, -1) != 3) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, -1, 1);
			replay->Network[0] = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, -1, 2);
			replay->Network[1] = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, -1, 3);
			replay->Network[2] = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else {
			LuaError(l, "Unsupported key: %s" _C_ value);
		}
		lua_pop(l, 1);
	}

	CurrentReplay = replay;

	// Apply CurrentReplay settings.
	if (!SaveGameLoading) {
		ApplyReplaySettings();
	} else {
		CommandLogDisabled = 0;
	}

	return 0;
}

/**
**  Check if we're replaying a game
*/
bool IsReplayGame()
{
	return ReplayGameType != ReplayNone;
}

/**
**  Save generated replay
**
**  @param file  file to save to.
*/
void SaveReplayList(CFile *file)
{
	SaveFullLog(file);
}

/**
**  Load a log file to replay a game
**
**  @param name  name of file to load.
*/
int LoadReplay(const std::string &name)
{
	CleanReplayLog();
	ReplayGameType = ReplaySinglePlayer;

	LuaLoadFile(name);

	NextLogCycle = ~0UL;
	if (!CommandLogDisabled) {
		CommandLogDisabled = 1;
		DisabledLog = 1;
	}
	GameObserve = true;
	InitReplay = 1;

	return 0;
}

/**
** End logging
*/
void EndReplayLog(void)
{
	if (LogFile) {
		LogFile->close();
		delete LogFile;
		LogFile = NULL;
	}
	if (CurrentReplay) {
		DeleteReplay(CurrentReplay);
		CurrentReplay = NULL;
	}
	ReplayStep = NULL;
}

/**
** Clean replay log
*/
void CleanReplayLog(void)
{
	if (CurrentReplay) {
		DeleteReplay(CurrentReplay);
		CurrentReplay = 0;
	}
	ReplayStep = NULL;

// if (DisabledLog) {
		CommandLogDisabled = 0;
		DisabledLog = 0;
// }
	GameObserve = false;
	NetPlayers = 0;
	ReplayGameType = ReplayNone;
}

/**
** Do next replay
*/
static void DoNextReplay(void)
{
	int unit;
	const char *action;
	int flags;
	int posx;
	int posy;
	const char *val;
	int num;
	CUnit *dunit;

	Assert(ReplayStep != 0);

	NextLogCycle = ReplayStep->GameCycle;

	if (NextLogCycle != GameCycle) {
		return;
	}

	unit = ReplayStep->UnitNumber;
	action = ReplayStep->Action.c_str();
	flags = ReplayStep->Flush;
	posx = ReplayStep->PosX;
	posy = ReplayStep->PosY;
	dunit = (ReplayStep->DestUnitNumber != -1 ? UnitSlots[ReplayStep->DestUnitNumber] : NoUnitP);
	val = ReplayStep->Value.c_str();
	num = ReplayStep->Num;

	Assert(unit == -1 || ReplayStep->UnitIdent == UnitSlots[unit]->Type->Ident);

	if (SyncRandSeed != ReplayStep->SyncRandSeed) {
#ifdef DEBUG
		if (!ReplayStep->SyncRandSeed) {
			// Replay without the 'sync info
			ThisPlayer->Notify(NotifyYellow, -1, -1, _("No sync info for this replay !"));
		} else {
			ThisPlayer->Notify(NotifyYellow, -1, -1, _("Replay got out of sync (%lu) !"), GameCycle);
			DebugPrint("OUT OF SYNC %u != %u\n" _C_ SyncRandSeed _C_ ReplayStep->SyncRandSeed);
			DebugPrint("OUT OF SYNC GameCycle %lu \n" _C_ GameCycle);
			Assert(0);
			// ReplayStep = 0;
			// NextLogCycle = ~0UL;
			// return;
		}
#else
		ThisPlayer->Notify(NotifyYellow, -1, -1, _("Replay got out of sync !"));
		ReplayStep = 0;
		NextLogCycle = ~0UL;
		return;
#endif
	}

	if (!strcmp(action, "stop")) {
		SendCommandStopUnit(UnitSlots[unit]);
	} else if (!strcmp(action, "stand-ground")) {
		SendCommandStandGround(UnitSlots[unit], flags);
	} else if (!strcmp(action, "follow")) {
		SendCommandFollow(UnitSlots[unit], dunit, flags);
	} else if (!strcmp(action, "move")) {
		SendCommandMove(UnitSlots[unit], posx, posy, flags);
	} else if (!strcmp(action, "repair")) {
		SendCommandRepair(UnitSlots[unit], posx, posy, dunit, flags);
	} else if (!strcmp(action, "auto-repair")) {
		SendCommandAutoRepair(UnitSlots[unit], posx);
	} else if (!strcmp(action, "attack")) {
		SendCommandAttack(UnitSlots[unit], posx, posy, dunit, flags);
	} else if (!strcmp(action, "attack-ground")) {
		SendCommandAttackGround(UnitSlots[unit], posx, posy, flags);
	} else if (!strcmp(action, "patrol")) {
		SendCommandPatrol(UnitSlots[unit], posx, posy, flags);
	} else if (!strcmp(action, "board")) {
		SendCommandBoard(UnitSlots[unit], posx, posy, dunit, flags);
	} else if (!strcmp(action, "unload")) {
		SendCommandUnload(UnitSlots[unit], posx, posy, dunit, flags);
	} else if (!strcmp(action, "build")) {
		SendCommandBuildBuilding(UnitSlots[unit], posx, posy, UnitTypeByIdent(val), flags);
	} else if (!strcmp(action, "dismiss")) {
		SendCommandDismiss(UnitSlots[unit]);
	} else if (!strcmp(action, "resource-loc")) {
		SendCommandResourceLoc(UnitSlots[unit], posx, posy, flags);
	} else if (!strcmp(action, "resource")) {
		SendCommandResource(UnitSlots[unit], dunit, flags);
	} else if (!strcmp(action, "return")) {
		SendCommandReturnGoods(UnitSlots[unit], dunit, flags);
	} else if (!strcmp(action, "train")) {
		SendCommandTrainUnit(UnitSlots[unit], UnitTypeByIdent(val), flags);
	} else if (!strcmp(action, "cancel-train")) {
		SendCommandCancelTraining(UnitSlots[unit], num, (val && *val) ? UnitTypeByIdent(val) : NULL);
	} else if (!strcmp(action, "upgrade-to")) {
		SendCommandUpgradeTo(UnitSlots[unit], UnitTypeByIdent(val), flags);
	} else if (!strcmp(action, "cancel-upgrade-to")) {
		SendCommandCancelUpgradeTo(UnitSlots[unit]);
	} else if (!strcmp(action, "research")) {
		SendCommandResearch(UnitSlots[unit], CUpgrade::Get(val), flags);
	} else if (!strcmp(action, "cancel-research")) {
		SendCommandCancelResearch(UnitSlots[unit]);
	} else if (!strcmp(action, "spell-cast")) {
		SendCommandSpellCast(UnitSlots[unit], posx, posy, dunit, num, flags);
	} else if (!strcmp(action, "auto-spell-cast")) {
		SendCommandAutoSpellCast(UnitSlots[unit], num, posx);
	} else if (!strcmp(action, "diplomacy")) {
		int state;
		if (!strcmp(val, "neutral")) {
			state = DiplomacyNeutral;
		} else if (!strcmp(val, "allied")) {
			state = DiplomacyAllied;
		} else if (!strcmp(val, "enemy")) {
			state = DiplomacyEnemy;
		} else if (!strcmp(val, "crazy")) {
			state = DiplomacyCrazy;
		} else {
			DebugPrint("Invalid diplomacy command: %s" _C_ val);
			state = -1;
		}
		SendCommandDiplomacy(posx, state, posy);
	} else if (!strcmp(action, "shared-vision")) {
		bool state;
		state = atoi(val) ? true : false;
		SendCommandSharedVision(posx, state, posy);
	} else if (!strcmp(action, "input")) {
		if (val[0] == '-') {
			CclCommand(val + 1);
		} else {
			HandleCheats(val);
		}
	} else if (!strcmp(action, "quit")) {
		CommandQuit(posx);
	} else {
		DebugPrint("Invalid action: %s" _C_ action);
	}

	ReplayStep = ReplayStep->Next;
	NextLogCycle = ReplayStep ? (unsigned)ReplayStep->GameCycle : ~0UL;
}

/**
** Replay user commands from log each cycle
*/
static void ReplayEachCycle(void)
{
	if (!CurrentReplay) {
		return;
	}
	if (InitReplay) {
		int i;
		for (i = 0; i < PlayerMax; ++i) {
			if (!CurrentReplay->Players[i].Name.empty()) {
				Players[i].SetName(CurrentReplay->Players[i].Name);
			}
		}
		ReplayStep = CurrentReplay->Commands;
		NextLogCycle = (ReplayStep ? (unsigned)ReplayStep->GameCycle : ~0UL);
		InitReplay = 0;
	}

	if (!ReplayStep) {
		SetMessage(_("End of replay"));
		GameObserve = false;
		return;
	}

	if (NextLogCycle != ~0UL && NextLogCycle != GameCycle) {
		return;
	}

	do {
		DoNextReplay();
	} while (ReplayStep &&
			(NextLogCycle == ~0UL || NextLogCycle == GameCycle));

	if (!ReplayStep) {
		SetMessage(_("End of replay"));
		GameObserve = false;
	}
}

/**
** Replay user commands from log each cycle, single player games
*/
void SinglePlayerReplayEachCycle(void)
{
	if (ReplayGameType == ReplaySinglePlayer) {
		ReplayEachCycle();
	}
}

/**
** Replay user commands from log each cycle, multiplayer games
*/
void MultiPlayerReplayEachCycle(void)
{
	if (ReplayGameType == ReplayMultiPlayer) {
		ReplayEachCycle();
	}
}
//@}

//----------------------------------------------------------------------------
// Send game commands, maybe over the network.
//----------------------------------------------------------------------------

/**@name send */
//@{

/**
** Send command: Unit stop.
**
** @param unit pointer to unit.
*/
void SendCommandStopUnit(CUnit *unit)
{
	if (!IsNetworkGame()) {
		CommandLog("stop", unit, FlushCommands, -1, -1, NoUnitP, NULL, -1);
		CommandStopUnit(unit);
	} else {
		NetworkSendCommand(MessageCommandStop, unit, 0, 0, NoUnitP, 0, FlushCommands);
	}
}

/**
** Send command: Unit stand ground.
**
** @param unit     pointer to unit.
** @param flush    Flag flush all pending commands.
*/
void SendCommandStandGround(CUnit *unit, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("stand-ground", unit, flush, -1, -1, NoUnitP, NULL, -1);
		CommandStandGround(unit, flush);
	} else {
		NetworkSendCommand(MessageCommandStand, unit, 0, 0, NoUnitP, 0, flush);
	}
}

/**
** Send command: Follow unit to position.
**
** @param unit    pointer to unit.
** @param dest    follow this unit.
** @param flush   Flag flush all pending commands.
*/
void SendCommandFollow(CUnit *unit, CUnit *dest, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("follow", unit, flush, -1, -1, dest, NULL, -1);
		CommandFollow(unit, dest, flush);
	} else {
		NetworkSendCommand(MessageCommandFollow, unit, 0, 0, dest, 0, flush);
	}
}

/**
** Send command: Move unit to position.
**
** @param unit    pointer to unit.
** @param x       X map tile position to move to.
** @param y       Y map tile position to move to.
** @param flush   Flag flush all pending commands.
*/
void SendCommandMove(CUnit *unit, int x, int y, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("move", unit, flush, x, y, NoUnitP, NULL, -1);
		CommandMove(unit, x, y, flush);
	} else {
		NetworkSendCommand(MessageCommandMove, unit, x, y, NoUnitP, 0, flush);
	}
}

/**
** Send command: Unit repair.
**
** @param unit    Pointer to unit.
** @param x       X map tile position to repair.
** @param y       Y map tile position to repair.
** @param dest    Unit to be repaired.
** @param flush   Flag flush all pending commands.
*/
void SendCommandRepair(CUnit *unit, int x, int y, CUnit *dest, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("repair", unit, flush, x, y, dest, NULL, -1);
		CommandRepair(unit, x, y, dest, flush);
	} else {
		NetworkSendCommand(MessageCommandRepair, unit, x, y, dest, 0, flush);
	}
}

/**
** Send command: Unit auto repair.
**
** @param unit      pointer to unit.
** @param on        1 for auto repair on, 0 for off.
*/
void SendCommandAutoRepair(CUnit *unit, int on)
{
	if (!IsNetworkGame()) {
		CommandLog("auto-repair", unit, FlushCommands, on, -1, NoUnitP,
			NULL, 0);
		CommandAutoRepair(unit, on);
	} else {
		NetworkSendCommand(MessageCommandAutoRepair,
			unit, on, -1, NoUnitP, NULL, FlushCommands);
	}
}

/**
** Send command: Unit attack unit or at position.
**
** @param unit     pointer to unit.
** @param x        X map tile position to attack.
** @param y        Y map tile position to attack.
** @param attack   or !=NoUnitP unit to be attacked.
** @param flush    Flag flush all pending commands.
*/
void SendCommandAttack(CUnit *unit, int x, int y, CUnit *attack, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("attack", unit, flush, x, y, attack, NULL, -1);
		CommandAttack(unit, x, y, attack, flush);
	} else {
		NetworkSendCommand(MessageCommandAttack, unit, x, y, attack, 0, flush);
	}
}

/**
** Send command: Unit attack ground.
**
** @param unit     pointer to unit.
** @param x        X map tile position to fire on.
** @param y        Y map tile position to fire on.
** @param flush    Flag flush all pending commands.
*/
void SendCommandAttackGround(CUnit *unit, int x, int y, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("attack-ground", unit, flush, x, y, NoUnitP, NULL, -1);
		CommandAttackGround(unit, x, y, flush);
	} else {
		NetworkSendCommand(MessageCommandGround, unit, x, y, NoUnitP, 0, flush);
	}
}

/**
** Send command: Unit patrol between current and position.
**
** @param unit     pointer to unit.
** @param x        X map tile position to patrol between.
** @param y        Y map tile position to patrol between.
** @param flush    Flag flush all pending commands.
*/
void SendCommandPatrol(CUnit *unit, int x, int y, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("patrol", unit, flush, x, y, NoUnitP, NULL, -1);
		CommandPatrolUnit(unit, x, y, flush);
	} else {
		NetworkSendCommand(MessageCommandPatrol, unit, x, y, NoUnitP, 0, flush);
	}
}

/**
** Send command: Unit board unit.
**
** @param unit     pointer to unit.
** @param x        X map tile position (unused).
** @param y        Y map tile position (unused).
** @param dest     Destination to be boarded.
** @param flush    Flag flush all pending commands.
*/
void SendCommandBoard(CUnit *unit, int x, int y, CUnit *dest, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("board", unit, flush, x, y, dest, NULL, -1);
		CommandBoard(unit, dest, flush);
	} else {
		NetworkSendCommand(MessageCommandBoard, unit, x, y, dest, 0, flush);
	}
}

/**
** Send command: Unit unload unit.
**
** @param unit    pointer to unit.
** @param x       X map tile position of unload.
** @param y       Y map tile position of unload.
** @param what    Passagier to be unloaded.
** @param flush   Flag flush all pending commands.
*/
void SendCommandUnload(CUnit *unit, int x, int y, CUnit *what, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("unload", unit, flush, x, y, what, NULL, -1);
		CommandUnload(unit, x, y, what, flush);
	} else {
		NetworkSendCommand(MessageCommandUnload, unit, x, y, what, 0, flush);
	}
}

/**
** Send command: Unit builds building at position.
**
** @param unit    pointer to unit.
** @param x       X map tile position of construction.
** @param y       Y map tile position of construction.
** @param what    pointer to unit-type of the building.
** @param flush   Flag flush all pending commands.
*/
void SendCommandBuildBuilding(CUnit *unit, int x, int y,
	CUnitType *what, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("build", unit, flush, x, y, NoUnitP, what->Ident.c_str(), -1);
		CommandBuildBuilding(unit, x, y, what, flush);
	} else {
		NetworkSendCommand(MessageCommandBuild, unit, x, y, NoUnitP, what, flush);
	}
}

/**
**  Send command: Cancel this building construction.
**
**  @param unit  pointer to unit.
*/
void SendCommandDismiss(CUnit *unit)
{
	// FIXME: currently unit and worker are same?
	if (!IsNetworkGame()) {
		CommandLog("dismiss", unit, FlushCommands, -1, -1, NULL, NULL, -1);
		CommandDismiss(unit);
	} else {
		NetworkSendCommand(MessageCommandDismiss, unit, 0, 0, NULL, 0,
			FlushCommands);
	}
}

/**
**  Send command: Unit harvests a location (wood for now).
**
** @param unit     pointer to unit.
** @param x        X map tile position where to harvest.
** @param y        Y map tile position where to harvest.
** @param flush    Flag flush all pending commands.
*/
void SendCommandResourceLoc(CUnit *unit, int x, int y, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("resource-loc", unit, flush, x, y, NoUnitP, NULL, -1);
		CommandResourceLoc(unit, x, y, flush);
	} else {
		NetworkSendCommand(MessageCommandResourceLoc, unit, x, y, NoUnitP, 0, flush);
	}
}

/**
** Send command: Unit harvest resources
**
** @param unit    pointer to unit.
** @param dest    pointer to destination (oil-platform,gold mine).
** @param flush   Flag flush all pending commands.
*/
void SendCommandResource(CUnit *unit, CUnit *dest, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("resource", unit, flush, -1, -1, dest, NULL, -1);
		CommandResource(unit, dest, flush);
	} else {
		NetworkSendCommand(MessageCommandResource, unit, 0, 0, dest, 0, flush);
	}
}

/**
** Send command: Unit return goods.
**
** @param unit    pointer to unit.
** @param goal    pointer to destination of the goods. (NULL=search best)
** @param flush   Flag flush all pending commands.
*/
void SendCommandReturnGoods(CUnit *unit, CUnit *goal, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("return", unit, flush, -1, -1, goal, NULL, -1);
		CommandReturnGoods(unit, goal, flush);
	} else {
		NetworkSendCommand(MessageCommandReturn, unit, 0, 0, goal, 0, flush);
	}
}

/**
** Send command: Building/unit train new unit.
**
** @param unit    pointer to unit.
** @param what    pointer to unit-type of the unit to be trained.
** @param flush   Flag flush all pending commands.
*/
void SendCommandTrainUnit(CUnit *unit, CUnitType *what, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("train", unit, flush, -1, -1, NoUnitP, what->Ident.c_str(), -1);
		CommandTrainUnit(unit, what, flush);
	} else {
		NetworkSendCommand(MessageCommandTrain, unit, 0, 0, NoUnitP, what, flush);
	}
}

/**
** Send command: Cancel training.
**
** @param unit    Pointer to unit.
** @param slot    Slot of training queue to cancel.
** @param type    Unit-type of unit to cancel.
*/
void SendCommandCancelTraining(CUnit *unit, int slot, const CUnitType *type)
{
	if (!IsNetworkGame()) {
		CommandLog("cancel-train", unit, FlushCommands, -1, -1, NoUnitP,
				type ? type->Ident.c_str() : NULL, slot);
		CommandCancelTraining(unit, slot, type);
	} else {
		NetworkSendCommand(MessageCommandCancelTrain, unit, slot, 0, NoUnitP,
			type, FlushCommands);
	}
}

/**
** Send command: Building starts upgrading to.
**
** @param unit     pointer to unit.
** @param what     pointer to unit-type of the unit upgrade.
** @param flush    Flag flush all pending commands.
*/
void SendCommandUpgradeTo(CUnit *unit, CUnitType *what, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("upgrade-to", unit, flush, -1, -1, NoUnitP, what->Ident.c_str(), -1);
		CommandUpgradeTo(unit, what, flush);
	} else {
		NetworkSendCommand(MessageCommandUpgrade, unit, 0, 0, NoUnitP, what, flush);
	}
}

/**
** Send command: Cancel building upgrading to.
**
** @param unit  Pointer to unit.
*/
void SendCommandCancelUpgradeTo(CUnit *unit)
{
	if (!IsNetworkGame()) {
		CommandLog("cancel-upgrade-to", unit, FlushCommands,
			-1, -1, NoUnitP, NULL, -1);
		CommandCancelUpgradeTo(unit);
	} else {
		NetworkSendCommand(MessageCommandCancelUpgrade, unit,
			0, 0, NoUnitP, NULL, FlushCommands);
	}
}

/**
** Send command: Building/unit research.
**
** @param unit     pointer to unit.
** @param what     research-type of the research.
** @param flush    Flag flush all pending commands.
*/
void SendCommandResearch(CUnit *unit, CUpgrade *what, int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("research", unit, flush, -1, -1, NoUnitP, what->Ident.c_str(), -1);
		CommandResearch(unit, what, flush);
	} else {
		NetworkSendCommand(MessageCommandResearch, unit,
			what->ID, 0, NoUnitP, NULL, flush);
	}
}

/**
** Send command: Cancel Building/unit research.
**
** @param unit pointer to unit.
*/
void SendCommandCancelResearch(CUnit *unit)
{
	if (!IsNetworkGame()) {
		CommandLog("cancel-research", unit, FlushCommands, -1, -1, NoUnitP, NULL, -1);
		CommandCancelResearch(unit);
	} else {
		NetworkSendCommand(MessageCommandCancelResearch, unit,
			0, 0, NoUnitP, NULL, FlushCommands);
	}
}

/**
** Send command: Unit spell cast on position/unit.
**
** @param unit      pointer to unit.
** @param x         X map tile position where to cast spell.
** @param y         Y map tile position where to cast spell.
** @param dest      Cast spell on unit (if exist).
** @param spellid   Spell type id.
** @param flush     Flag flush all pending commands.
*/
void SendCommandSpellCast(CUnit *unit, int x, int y, CUnit *dest, int spellid,
	int flush)
{
	if (!IsNetworkGame()) {
		CommandLog("spell-cast", unit, flush, x, y, dest, NULL, spellid);
		CommandSpellCast(unit, x, y, dest, SpellTypeTable[spellid], flush);
	} else {
		NetworkSendCommand(MessageCommandSpellCast + spellid,
			unit, x, y, dest, NULL, flush);
	}
}

/**
** Send command: Unit auto spell cast.
**
** @param unit      pointer to unit.
** @param spellid   Spell type id.
** @param on        1 for auto cast on, 0 for off.
*/
void SendCommandAutoSpellCast(CUnit *unit, int spellid, int on)
{
	if (!IsNetworkGame()) {
		CommandLog("auto-spell-cast", unit, FlushCommands, on, -1, NoUnitP,
			NULL, spellid);
		CommandAutoSpellCast(unit, spellid, on);
	} else {
		NetworkSendCommand(MessageCommandSpellCast + spellid,
			unit, on, -1, NoUnitP, NULL, FlushCommands);
	}
}

/**
** Send command: Diplomacy changed.
**
** @param player     Player which changes his state.
** @param state      New diplomacy state.
** @param opponent   Opponent.
*/
void SendCommandDiplomacy(int player, int state, int opponent)
{
	if (!IsNetworkGame()) {
		switch (state) {
			case DiplomacyNeutral:
				CommandLog("diplomacy", NoUnitP, 0, player, opponent,
					NoUnitP, "neutral", -1);
				break;
			case DiplomacyAllied:
				CommandLog("diplomacy", NoUnitP, 0, player, opponent,
					NoUnitP, "allied", -1);
				break;
			case DiplomacyEnemy:
				CommandLog("diplomacy", NoUnitP, 0, player, opponent,
					NoUnitP, "enemy", -1);
				break;
			case DiplomacyCrazy:
				CommandLog("diplomacy", NoUnitP, 0, player, opponent,
					NoUnitP, "crazy", -1);
				break;
		}
		CommandDiplomacy(player, state, opponent);
	} else {
		NetworkSendExtendedCommand(ExtendedMessageDiplomacy,
			-1, player, state, opponent, 0);
	}
}

/**
** Send command: Shared vision changed.
**
** @param player     Player which changes his state.
** @param state      New shared vision state.
** @param opponent   Opponent.
*/
void SendCommandSharedVision(int player, bool state, int opponent)
{
	if (!IsNetworkGame()) {
		if (state == false) {
			CommandLog("shared-vision", NoUnitP, 0, player, opponent,
				NoUnitP, "0", -1);
		} else {
			CommandLog("shared-vision", NoUnitP, 0, player, opponent,
				NoUnitP, "1", -1);
		}
		CommandSharedVision(player, state, opponent);
	} else {
		NetworkSendExtendedCommand(ExtendedMessageSharedVision,
			-1, player, state, opponent, 0);
	}
}

/**
**  Register Ccl functions with lua
*/
void NetworkCclRegister(void)
{
	lua_register(Lua, "Log", CclLog);
	lua_register(Lua, "ReplayLog", CclReplayLog);
}

//@}

//----------------------------------------------------------------------------
// Parse the message, from the network.
//----------------------------------------------------------------------------

/**@name parse */
//@{

/**
** Parse a command (from network).
**
** @param msgnr    Network message type
** @param unum     Unit number (slot) that receive the command.
** @param x        optional X map position.
** @param y        optional y map position.
** @param dstnr    optional destination unit.
*/
void ParseCommand(unsigned char msgnr, UnitRef unum,
	unsigned short x, unsigned short y, UnitRef dstnr)
{
	CUnit *unit;
	CUnit *dest;
	int id;
	int status;

	unit = UnitSlots[unum];
	Assert(unit);
	//
	// Check if unit is already killed?
	//
	if (unit->Destroyed) {
		DebugPrint(" destroyed unit skipping %d\n" _C_ UnitNumber(unit));
		return;
	}

	Assert(unit->Type);

	status = (msgnr & 0x80) >> 7;

	// Note: destroyed destination unit is handled by the action routines.

	switch (msgnr & 0x7F) {
		case MessageSync:
			return;
		case MessageQuit:
			return;
		case MessageChat:
			return;

		case MessageCommandStop:
			CommandLog("stop", unit, FlushCommands, -1, -1, NoUnitP, NULL, -1);
			CommandStopUnit(unit);
			break;
		case MessageCommandStand:
			CommandLog("stand-ground", unit, status, -1, -1, NoUnitP, NULL, -1);
			CommandStandGround(unit, status);
			break;
		case MessageCommandFollow:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("follow", unit, status, -1, -1, dest, NULL, -1);
			CommandFollow(unit, dest, status);
			break;
		case MessageCommandMove:
			CommandLog("move", unit, status, x, y, NoUnitP, NULL, -1);
			CommandMove(unit, x, y, status);
			break;
		case MessageCommandRepair:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("repair", unit, status, x, y, dest, NULL, -1);
			CommandRepair(unit, x, y, dest, status);
			break;
		case MessageCommandAutoRepair:
			CommandLog("auto-repair", unit, status, x, y, NoUnitP, NULL, 0);
			CommandAutoRepair(unit, x);
			break;
		case MessageCommandAttack:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("attack", unit, status, x, y, dest, NULL, -1);
			CommandAttack(unit, x, y, dest, status);
			break;
		case MessageCommandGround:
			CommandLog("attack-ground", unit, status, x, y, NoUnitP, NULL, -1);
			CommandAttackGround(unit, x, y, status);
			break;
		case MessageCommandPatrol:
			CommandLog("patrol", unit, status, x, y, NoUnitP, NULL, -1);
			CommandPatrolUnit(unit, x, y, status);
			break;
		case MessageCommandBoard:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("board", unit, status, x, y, dest, NULL, -1);
			CommandBoard(unit, dest, status);
			break;
		case MessageCommandUnload:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("unload", unit, status, x, y, dest, NULL, -1);
			CommandUnload(unit, x, y, dest, status);
			break;
		case MessageCommandBuild:
			CommandLog("build", unit, status, x, y, NoUnitP, UnitTypes[dstnr]->Ident.c_str(),
				-1);
			CommandBuildBuilding(unit, x, y, UnitTypes[dstnr], status);
			break;
		case MessageCommandDismiss:
			CommandLog("dismiss", unit, FlushCommands, -1, -1, NULL, NULL, -1);
			CommandDismiss(unit);
			break;
		case MessageCommandResourceLoc:
			CommandLog("resource-loc", unit, status, x, y, NoUnitP, NULL, -1);
			CommandResourceLoc(unit, x, y, status);
			break;
		case MessageCommandResource:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("resource", unit, status, -1, -1, dest, NULL, -1);
			CommandResource(unit, dest, status);
			break;
		case MessageCommandReturn:
			dest = NoUnitP;
			if (dstnr != (unsigned short)0xFFFF) {
				dest = UnitSlots[dstnr];
				Assert(dest && dest->Type);
			}
			CommandLog("return", unit, status, -1, -1, dest, NULL, -1);
			CommandReturnGoods(unit, dest, status);
			break;
		case MessageCommandTrain:
			CommandLog("train", unit, status, -1, -1, NoUnitP,
				UnitTypes[dstnr]->Ident.c_str(), -1);
			CommandTrainUnit(unit, UnitTypes[dstnr], status);
			break;
		case MessageCommandCancelTrain:
			// We need (short)x for the last slot -1
			if (dstnr != (unsigned short)0xFFFF) {
				CommandLog("cancel-train", unit, FlushCommands, -1, -1, NoUnitP,
					UnitTypes[dstnr]->Ident.c_str(), (short)x);
				CommandCancelTraining(unit, (short)x, UnitTypes[dstnr]);
			} else {
				CommandLog("cancel-train", unit, FlushCommands, -1, -1, NoUnitP,
					NULL, (short)x);
				CommandCancelTraining(unit, (short)x, NULL);
			}
			break;
		case MessageCommandUpgrade:
			CommandLog("upgrade-to", unit, status, -1, -1, NoUnitP,
				UnitTypes[dstnr]->Ident.c_str(), -1);
			CommandUpgradeTo(unit, UnitTypes[dstnr], status);
			break;
		case MessageCommandCancelUpgrade:
			CommandLog("cancel-upgrade-to", unit, FlushCommands, -1, -1, NoUnitP,
				NULL, -1);
			CommandCancelUpgradeTo(unit);
			break;
		case MessageCommandResearch:
			CommandLog("research", unit, status, -1, -1, NoUnitP,
				AllUpgrades[x]->Ident.c_str(), -1);
			CommandResearch(unit, AllUpgrades[x], status);
			break;
		case MessageCommandCancelResearch:
			CommandLog("cancel-research", unit, FlushCommands, -1, -1, NoUnitP,
				NULL, -1);
			CommandCancelResearch(unit);
			break;
		default:
			id = (msgnr&0x7f) - MessageCommandSpellCast;
			if (y != (unsigned short)0xFFFF) {
				dest = NoUnitP;
				if (dstnr != (unsigned short)0xFFFF) {
					dest = UnitSlots[dstnr];
					Assert(dest && dest->Type);
				}
				CommandLog("spell-cast", unit, status, x, y, dest, NULL, id);
				CommandSpellCast(unit, x, y, dest, SpellTypeTable[id], status);
			} else {
				CommandLog("auto-spell-cast", unit, status, x, -1, NoUnitP, NULL, id);
				CommandAutoSpellCast(unit, id, x);
			}
			break;
	}
}

/**
** Parse an extended command (from network).
**
** @param type     Network extended message type
** @param status   Bit 7 of message type
** @param arg1     Messe argument 1
** @param arg2     Messe argument 2
** @param arg3     Messe argument 3
** @param arg4     Messe argument 4
*/
void ParseExtendedCommand(unsigned char type, int status,
	unsigned char arg1, unsigned short arg2, unsigned short arg3,
	unsigned short arg4)
{
	// Note: destroyed units are handled by the action routines.

	switch (type) {
		case ExtendedMessageDiplomacy:
			switch (arg3) {
				case DiplomacyNeutral:
					CommandLog("diplomacy", NoUnitP, 0, arg2, arg4,
						NoUnitP, "neutral", -1);
					break;
				case DiplomacyAllied:
					CommandLog("diplomacy", NoUnitP, 0, arg2, arg4,
						NoUnitP, "allied", -1);
					break;
				case DiplomacyEnemy:
					CommandLog("diplomacy", NoUnitP, 0, arg2, arg4,
						NoUnitP, "enemy", -1);
					break;
				case DiplomacyCrazy:
					CommandLog("diplomacy", NoUnitP, 0, arg2, arg4,
						NoUnitP, "crazy", -1);
					break;
			}
			CommandDiplomacy(arg2, arg3, arg4);
			break;
		case ExtendedMessageSharedVision:
			if (arg3 == 0) {
				CommandLog("shared-vision", NoUnitP, 0, arg2, arg4,
					NoUnitP, "0", -1);
			} else {
				CommandLog("shared-vision", NoUnitP, 0, arg2, arg4,
					NoUnitP, "1", -1);
			}
			CommandSharedVision(arg2, arg3 ? true : false, arg4);
			break;
		default:
			DebugPrint("Unknown extended message %u/%s %u %u %u %u\n" _C_
				type _C_ status ? "flush" : "-" _C_
				arg1 _C_ arg2 _C_ arg3 _C_ arg4);
			break;
	}
}

//@}
