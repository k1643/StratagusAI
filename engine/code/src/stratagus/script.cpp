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
/**@name script.cpp - The configuration language. */
//
//      (c) Copyright 1998-2006 by Lutz Sammer, Jimmy Salmon and Joris Dauphin.
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
//      $Id: script.cpp 7894 2007-02-26 19:29:36Z nehalmistry $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <signal.h>
#include <ctype.h>

#include "stratagus.h"

#include "iocompat.h"
#include "unit.h"
#include "unittype.h"
#include "iolib.h"
#include "script.h"
#include "missile.h"
#include "depend.h"
#include "upgrade.h"
#include "construct.h"
#include "unit.h"
#include "map.h"
#include "tileset.h"
#include "script_sound.h"
#include "ui.h"
#include "interface.h"
#include "font.h"
#include "pathfinder.h"
#include "ai.h"
#include "results.h"
#include "trigger.h"
#include "settings.h"
#include "editor.h"
#include "sound.h"
#include "sound_server.h"
#include "master.h"
#include "netconnect.h"
#include "network.h"
#include "spells.h"
#include "actions.h"


/*----------------------------------------------------------------------------
--  Variables
----------------------------------------------------------------------------*/

lua_State *Lua;                       /// Structure to work with lua files.

char *CclStartFile;                   /// CCL start file
std::string GameName;                 /// Game Preferences
int CclInConfigFile;                  /// True while config file parsing
int SaveGameLoading;                  /// If a Saved Game is Loading
std::string CurrentLuaFile;           /// Lua file currently being interpreted

int NoRandomPlacementMultiplayer = 0; /// Disable the random placement of players in muliplayer mode

bool UseHPForXp = false;              /// true if gain XP by dealing damage, false if by killing.
NumberDesc *Damage;                   /// Damage calculation for missile.

static int NumberCounter = 0; /// Counter for lua function.
static int StringCounter = 0; /// Counter for lua function.

/*----------------------------------------------------------------------------
--  Functions
----------------------------------------------------------------------------*/


/// Usefull for getComponent.
typedef enum {
	USTRINT_STR, USTRINT_INT
} UStrIntType;
typedef struct {
	union {const char *s; int i;};
	UStrIntType type;
} UStrInt;
/// Get component for unit variable.
extern UStrInt GetComponent(const CUnit *unit, int index, EnumVariable e, int t);

/**
**  FIXME: docu
*/
static void lstop(lua_State *l, lua_Debug *ar)
{
	(void)ar;  // unused arg.
	lua_sethook(l, NULL, 0, 0);
	luaL_error(l, "interrupted!");
}

/**
**  FIXME: docu
*/
static void laction(int i)
{
	// if another SIGINT happens before lstop,
	// terminate process (default action)
	signal(i, SIG_DFL);
	lua_sethook(Lua, lstop, LUA_MASKCALL | LUA_MASKRET | LUA_MASKCOUNT, 1);
}

/**
**  Print error message and exit.
**
**  @param pname  Source of the error.
**  @param msg    error message to print.
*/
static void l_message(const char *pname, const char *msg)
{
	if (pname) {
		fprintf(stderr, "%s: ", pname);
	}
	fprintf(stderr, "%s\n", msg);
	ExitFatal(1);
}

/**
**  Check error status, and print error message and exit
**  if status is different of 0.
**
**  @param status  status of the last lua call. (0: success)
**
**  @return        0 in success, else exit.
*/
static int report(int status)
{
	const char *msg;

	if (status) {
		msg = lua_tostring(Lua, -1);
		if (msg == NULL) {
			msg = "(error with no message)";
		}
		l_message(NULL, msg);
		lua_pop(Lua, 1);
	}
	return status;
}

static int luatraceback(lua_State *L) 
{
	lua_pushliteral(L, "debug");
	lua_gettable(L, LUA_GLOBALSINDEX);
	if (!lua_istable(L, -1)) {
		lua_pop(L, 1);
		return 1;
	}
	lua_pushliteral(L, "traceback");
	lua_gettable(L, -2);
	if (!lua_isfunction(L, -1)) {
		lua_pop(L, 2);
		return 1;
	}
	lua_pushvalue(L, 1);  // pass error message
	lua_pushnumber(L, 2);  // skip this function and traceback
	lua_call(L, 2, 1);  // call debug.traceback
	return 1;
}

/**
**  Call a lua function
**
**  @param narg   Number of arguments
**  @param clear  Clear the return value(s)
**
**  @return       0 in success, else exit.
*/
int LuaCall(int narg, int clear)
{
	int status;
	int base;

	base = lua_gettop(Lua) - narg;  // function index
	lua_pushcfunction(Lua, luatraceback);  // push traceback function
	lua_insert(Lua, base);  // put it under chunk and args
	signal(SIGINT, laction);
	status = lua_pcall(Lua, narg, (clear ? 0 : LUA_MULTRET), base);
	signal(SIGINT, SIG_DFL);
	lua_remove(Lua, base);  // remove traceback function

	return report(status);
}

/**
**  Load a file into a buffer
*/
static void LuaLoadBuffer(const char *file, char **buffer, int *buffersize)
{
	CFile fp;
	int size;
	int oldsize;
	int location;
	int read;
	char *buf;

	*buffer = NULL;
	*buffersize = 0;

	if (fp.open(file, CL_OPEN_READ) == -1) {
		fprintf(stderr, "Can't open file '%s': %s\n",
			file, strerror(errno));
		return;
	}

	size = 10000;
	buf = new char[size];
	if (!buf) {
		fprintf(stderr, "Out of memory\n");
		ExitFatal(-1);
	}
	location = 0;
	for (;;) {
		read = fp.read(&buf[location], size - location);
		if (read != size - location) {
			location += read;
			break;
		}
		location += read;
		oldsize = size;
		size = size * 2;
		char *newb = new char[size];
		if (!newb) {
			fprintf(stderr, "Out of memory\n");
			ExitFatal(-1);
		}
		memcpy(newb, buf, oldsize);
		delete[] buf;
		buf = newb;
	}
	fp.close();

	*buffer = buf;
	*buffersize = location;
}

/**
**  Load a file and execute it
**
**  @param file  File to load and execute
**
**  @return      0 in success, else exit.
*/
int LuaLoadFile(const std::string &file)
{
	int status;
	std::string PreviousLuaFile;
	char *buf;
	int size;

	PreviousLuaFile = CurrentLuaFile;
	CurrentLuaFile = file;

	LuaLoadBuffer(file.c_str(), &buf, &size);
	if (!buf) {
		return -1;
	}

	if (!(status = luaL_loadbuffer(Lua, buf, size, file.c_str()))) {
		LuaCall(0, 1);
	} else {
		report(status);
	}
	delete[] buf;
	CurrentLuaFile = PreviousLuaFile;

	return status;
}

/**
**  Get the directory of the current lua file
*/
static int CclGetCurrentLuaPath(lua_State *l)
{
	char *path;
	char *seperator;

	LuaCheckArgs(l, 0);
	path = new_strdup(CurrentLuaFile.c_str());
	Assert(path);
	seperator = strrchr(path, '/');
	if (seperator) {
		*seperator = 0;
		lua_pushstring(l, path);
	} else {
		lua_pushstring(l, "");
	}
	delete[] path;
	return 1;
}

/**
**	Save preferences
**
**  @param l  Lua state.
*/
static int CclSavePreferences(lua_State *l)
{
	LuaCheckArgs(l, 0);
	SavePreferences();
	return 0;
}

/**
**  Load a file and execute it.
**
**  @param l  Lua state.
**
**  @return   0 in success, else exit.
*/
static int CclLoad(lua_State *l)
{
	char buf[1024];

	LuaCheckArgs(l, 1);
	LibraryFileName(LuaToString(l, 1), buf, sizeof(buf));
	if (LuaLoadFile(buf) == -1) {
		DebugPrint("Load failed: %s\n" _C_ LuaToString(l, 1));
	}
	return 0;
}

/**
**  Load a file into a buffer and return it.
**
**  @param l  Lua state.
**
**  @return   buffer or nil on failure
*/
static int CclLoadBuffer(lua_State *l)
{
	char file[1024];
	char *buf;
	int size;

	LuaCheckArgs(l, 1);
	LibraryFileName(LuaToString(l, 1), file, sizeof(file));
	LuaLoadBuffer(file, &buf, &size);
	if (buf) {
		lua_pushstring(l, buf);
		delete[] buf; // Lua creates an internal copy
		return 1;
	}
	return 0;
}

/**
**  Load the SavedGameInfo Header
**
**  @param l  Lua state.
*/
static int CclSavedGameInfo(lua_State *l)
{
	const char *value;

	LuaCheckArgs(l, 1);
	if (!lua_istable(l, 1)) {
		LuaError(l, "incorrect argument");
	}

	lua_pushnil(l);
	while (lua_next(l, 1)) {
		value = LuaToString(l, -2);

		if (!strcmp(value, "SaveFile")) {
			if (strcpy_s(CurrentMapPath, sizeof(CurrentMapPath), LuaToString(l, -1)) != 0) {
				LuaError(l, "SaveFile too long");
			}
			std::string buf = StratagusLibPath;
			buf += "/";
			buf += LuaToString(l, -1);
			if (LuaLoadFile(buf) == -1) {
				DebugPrint("Load failed: %s\n" _C_ value);
			}
		} else if (!strcmp(value, "SyncHash")) {
			SyncHash = LuaToNumber(l, -1);
		} else if (!strcmp(value, "SyncRandSeed")) {
			SyncRandSeed = LuaToNumber(l, -1);
		} else {
			LuaError(l, "Unsupported tag: %s" _C_ value);
		}
		lua_pop(l, 1);
	}

	return 0;
}

/**
**  Convert lua string in char*.
**  It checks also type and exit in case of error.
**
**  @note char* could be invalidated with lua garbage collector.
**
**  @param l     Lua state.
**  @param narg  Argument number.
**
**  @return      char* from lua.
*/
const char *LuaToString(lua_State *l, int narg)
{
	luaL_checktype(l, narg, LUA_TSTRING);
	return lua_tostring(l, narg);
}

/**
**  Convert lua number in C number.
**  It checks also type and exit in case of error.
**
**  @param l     Lua state.
**  @param narg  Argument number.
**
**  @return      C number from lua.
*/
int LuaToNumber(lua_State *l, int narg)
{
	luaL_checktype(l, narg, LUA_TNUMBER);
	return static_cast<int>(lua_tonumber(l, narg));
}

/**
**  Convert lua boolean to bool.
**  It also checks type and exits in case of error.
**
**  @param l     Lua state.
**  @param narg  Argument number.
**
**  @return      1 for true, 0 for false from lua.
*/
bool LuaToBoolean(lua_State *l, int narg)
{
	luaL_checktype(l, narg, LUA_TBOOLEAN);
	return lua_toboolean(l, narg) != 0;
}

/**
**  Perform CCL garbage collection
**
**  @param fast  set this flag to disable slow GC (during game)
*/
void CclGarbageCollect(int fast)
{
#if LUA_VERSION_NUM >= 501
	DebugPrint("Garbage collect (before): %d\n" _C_
		lua_gc(Lua, LUA_GCCOUNT, 0));

	lua_gc(Lua, LUA_GCCOLLECT, 0);

	DebugPrint("Garbage collect (after): %d\n" _C_
		lua_gc(Lua, LUA_GCCOUNT, 0));
#else
	DebugPrint("Garbage collect (before): %d/%d\n" _C_
		lua_getgccount(Lua) _C_ lua_getgcthreshold(Lua));

	lua_setgcthreshold(Lua, 0);

	DebugPrint("Garbage collect (after): %d/%d\n" _C_
		lua_getgccount(Lua) _C_ lua_getgcthreshold(Lua));
#endif
}

// ////////////////////

/**
**  Parse binary operation with number.
**
**  @param l       lua state.
**  @param binop   Where to stock info (must be malloced)
*/
static void ParseBinOp(lua_State *l, BinOp *binop)
{
	Assert(l);
	Assert(binop);
	Assert(lua_istable(l, -1));
	Assert(luaL_getn(l, -1) == 2);

	lua_rawgeti(l, -1, 1); // left
	binop->Left = CclParseNumberDesc(l);
	lua_rawgeti(l, -1, 2); // right
	binop->Right = CclParseNumberDesc(l);
	lua_pop(l, 1); // table.
}

/**
**  Convert the string to the corresponding data (which is a unit).
**
**  @param l   lua state.
**  @param s   Ident.
**
**  @return    The reference of the unit.
**
**  @todo better check for error (restrict param).
*/
static CUnit **Str2UnitRef(lua_State *l, const char *s)
{
	CUnit **res; // Result.

	Assert(l);
	Assert(s);
	res = NULL;
	if (!strcmp(s, "Attacker")) {
		res = &TriggerData.Attacker;
	} else if (!strcmp(s, "Defender")) {
		res = &TriggerData.Defender;
	} else if (!strcmp(s, "Active")) {
		res = &TriggerData.Active;
	} else {
		LuaError(l, "Invalid unit reference '%s'\n" _C_ s);
	}
	Assert(res); // Must check for error.
	return res;
}

/**
**  Return unit referernce definition.
**
**  @param l  lua state.
**
**  @return   unit referernce definition.
*/
UnitDesc *CclParseUnitDesc(lua_State *l)
{
	UnitDesc *res;  // Result

	res = new UnitDesc;
	if (lua_isstring(l, -1)) {
		res->e = EUnit_Ref;
		res->D.AUnit = Str2UnitRef(l, LuaToString(l, -1));
	} else {
		LuaError(l, "Parse Error in ParseUnit\n");
	}
	lua_pop(l, 1);
	return res;
}


/**
**  Add a Lua handler
**
**  @param l          lua state.
**  @param tablename  name of the lua table.
**  @param counter    Counter for the handler
**
**  @return handle of the function.
*/
static int ParseLuaFunction(lua_State *l, const char *tablename, int *counter)
{
	lua_pushstring(l, tablename);
	lua_gettable(l, LUA_GLOBALSINDEX);
	if (lua_isnil(l, -1)) {
		lua_pop(l, 1);
		lua_pushstring(l, tablename);
		lua_newtable(l);
		lua_settable(l, LUA_GLOBALSINDEX);
		lua_pushstring(l, tablename);
		lua_gettable(l, LUA_GLOBALSINDEX);
	}
	lua_pushvalue(l, -2);
	lua_rawseti(l, -2, *counter);
	lua_pop(l, 1);
	return (*counter)++;
}

/**
**  Call a Lua handler
**
**  @param handler  handler of the lua function to call.
**
**  @return  lua function result.
*/
static int CallLuaNumberFunction(unsigned int handler)
{
	int narg;
	int res;

	narg = lua_gettop(Lua);
	lua_pushstring(Lua, "_numberfunction_");
	lua_gettable(Lua, LUA_GLOBALSINDEX);
	lua_rawgeti(Lua, -1, handler);
	LuaCall(0, 0);
	if (lua_gettop(Lua) - narg != 2) {
		LuaError(Lua, "Function must return one value.");
	}
	res = LuaToNumber(Lua, -1);
	lua_pop(Lua, 2);
	return res;
}

/**
**  Call a Lua handler
**
**  @param handler  handler of the lua function to call.
**
**  @return         lua function result.
*/
static char *CallLuaStringFunction(unsigned int handler)
{
	int narg;
	char *res;

	narg = lua_gettop(Lua);
	lua_pushstring(Lua, "_stringfunction_");
	lua_gettable(Lua, LUA_GLOBALSINDEX);
	lua_rawgeti(Lua, -1, handler);
	LuaCall(0, 0);
	if (lua_gettop(Lua) - narg != 2) {
		LuaError(Lua, "Function must return one value.");
	}
	res = new_strdup(LuaToString(Lua, -1));
	lua_pop(Lua, 2);
	return res;
}

/**
**  Return number.
**
**  @param l  lua state.
**
**  @return   number.
*/
NumberDesc *CclParseNumberDesc(lua_State *l)
{
	NumberDesc *res;
	int nargs;
	const char *key;

	res = new NumberDesc;
	if (lua_isnumber(l, -1)) {
		res->e = ENumber_Dir;
		res->D.Val = LuaToNumber(l, -1);
	} else if (lua_isfunction(l, -1)) {
		res->e = ENumber_Lua;
		res->D.Index = ParseLuaFunction(l, "_numberfunction_", &NumberCounter);
	} else if (lua_istable(l, -1)) {
		nargs = luaL_getn(l, -1);
		if (nargs != 2) {
			LuaError(l, "Bad number of args in parse Number table\n");
		}
		lua_rawgeti(l, -1, 1); // key
		key = LuaToString(l, -1);
		lua_pop(l, 1);
		lua_rawgeti(l, -1, 2); // table
		if (!strcmp(key, "Add")) {
			res->e = ENumber_Add;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Sub")) {
			res->e = ENumber_Sub;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Mul")) {
			res->e = ENumber_Mul;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Div")) {
			res->e = ENumber_Div;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Min")) {
			res->e = ENumber_Min;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Max")) {
			res->e = ENumber_Max;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Rand")) {
			res->e = ENumber_Rand;
			res->D.N = CclParseNumberDesc(l);
		} else if (!strcmp(key, "GreaterThan")) {
			res->e = ENumber_Gt;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "GreaterThanOrEq")) {
			res->e = ENumber_GtEq;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "LessThan")) {
			res->e = ENumber_Lt;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "LessThanOrEq")) {
			res->e = ENumber_LtEq;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "Equal")) {
			res->e = ENumber_Eq;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "NotEqual")) {
			res->e = ENumber_NEq;
			ParseBinOp(l, &res->D.BinOp);
		} else if (!strcmp(key, "UnitVar")) {
			Assert(lua_istable(l, -1));

			res->e = ENumber_UnitStat;
			for (lua_pushnil(l); lua_next(l, -2); lua_pop(l, 1)) {
				key = LuaToString(l, -2);
				if (!strcmp(key, "Unit")) {
					res->D.UnitStat.Unit = CclParseUnitDesc(l);
					lua_pushnil(l);
				} else if (!strcmp(key, "Variable")) {
					res->D.UnitStat.Index = GetVariableIndex(LuaToString(l, -1));
					if (res->D.UnitStat.Index == -1) {
						LuaError(l, "Bad variable name :'%s'" _C_ LuaToString(l, -1));
					}
				} else if (!strcmp(key, "Component")) {
					res->D.UnitStat.Component = Str2EnumVariable(l, LuaToString(l, -1));
				} else if (!strcmp(key, "Loc")) {
					res->D.UnitStat.Loc = LuaToNumber(l, -1);
					if (res->D.UnitStat.Loc < 0 || 2 < res->D.UnitStat.Loc) {
						LuaError(l, "Bad Loc number :'%d'" _C_ (int) LuaToNumber(l, -1));
					}
				} else {
					LuaError(l, "Bad param %s for Unit" _C_ key);
				}
			}
			lua_pop(l, 1); // pop the table.
		} else if (!strcmp(key, "VideoTextLength")) {
			Assert(lua_istable(l, -1));
			res->e = ENumber_VideoTextLength;

			for (lua_pushnil(l); lua_next(l, -2); lua_pop(l, 1)) {
				key = LuaToString(l, -2);
				if (!strcmp(key, "Text")) {
					res->D.VideoTextLength.String = CclParseStringDesc(l);
					lua_pushnil(l);
				} else if (!strcmp(key, "Font")) {
					res->D.VideoTextLength.Font = CFont::Get(LuaToString(l, -1));
					if (!res->D.VideoTextLength.Font) {
						LuaError(l, "Bad Font name :'%s'" _C_ LuaToString(l, -1));
					}
				} else {
					LuaError(l, "Bad param %s for VideoTextLength" _C_ key);
				}
			}
			lua_pop(l, 1); // pop the table.
		} else if (!strcmp(key, "StringFind")) {
			Assert(lua_istable(l, -1));
			res->e = ENumber_StringFind;
			if (luaL_getn(l, -1) != 2) {
				LuaError(l, "Bad param for StringFind");
			}
			lua_rawgeti(l, -1, 1); // left
			res->D.StringFind.String = CclParseStringDesc(l);

			lua_rawgeti(l, -1, 2); // right
			res->D.StringFind.C = *LuaToString(l, -1);
			lua_pop(l, 1); // pop the char

			lua_pop(l, 1); // pop the table.
		} else {
			lua_pop(l, 1);
			LuaError(l, "unknow condition '%s'"_C_ key);
		}
	} else {
		LuaError(l, "Parse Error in ParseNumber");
	}
	lua_pop(l, 1);
	return res;
}

/**
**  Create a StringDesc with const string.
**
**  @param s  direct value for the StringDesc
**
**  @return   the new StringDesc.
*/
StringDesc *NewStringDesc(const char *s)
{
	StringDesc *res;

	if (!s) {
		return NULL;
	}
	res = new StringDesc;
	res->e = EString_Dir;
	res->D.Val = new_strdup(s);
	return res;
}

/**
**  Return String description.
**
**  @param l  lua state.
**
**  @return   String description.
*/
StringDesc *CclParseStringDesc(lua_State *l)
{
	StringDesc *res;      // Result.
	int nargs;            // Size of table.
	const char *key;      // Key.

	res = new StringDesc;
	if (lua_isstring(l, -1)) {
		res->e = EString_Dir;
		res->D.Val = new_strdup(LuaToString(l, -1));
	} else if (lua_isfunction(l, -1)) {
		res->e = EString_Lua;
		res->D.Index = ParseLuaFunction(l, "_stringfunction_", &StringCounter);
	} else if (lua_istable(l, -1)) {
		nargs = luaL_getn(l, -1);
		if (nargs != 2) {
			LuaError(l, "Bad number of args in parse String table\n");
		}
		lua_rawgeti(l, -1, 1); // key
		key = LuaToString(l, -1);
		lua_pop(l, 1);
		lua_rawgeti(l, -1, 2); // table
		if (!strcmp(key, "Concat")){
			int i; // iterator.

			res->e = EString_Concat;
			res->D.Concat.n = luaL_getn(l, -1);
			if (res->D.Concat.n < 1) {
				LuaError(l, "Bad number of args in Concat\n");
			}
			res->D.Concat.Strings = new StringDesc *[res->D.Concat.n];
			for (i = 0; i < res->D.Concat.n; ++i) {
				lua_rawgeti(l, -1, 1 + i);
				res->D.Concat.Strings[i] = CclParseStringDesc(l);
			}
			lua_pop(l, 1); // table.
		} else if (!strcmp(key, "String")) {
			res->e = EString_String;
			res->D.Number = CclParseNumberDesc(l);
		} else if (!strcmp(key, "InverseVideo")) {
			res->e = EString_InverseVideo;
			res->D.String = CclParseStringDesc(l);
		} else if (!strcmp(key, "UnitName")) {
			res->e = EString_UnitName;
			res->D.Unit = CclParseUnitDesc(l);
		} else if (!strcmp(key, "If")) {
			res->e = EString_If;
			if (luaL_getn(l, -1) != 2 && luaL_getn(l, -1) != 3) {
				LuaError(l, "Bad number of args in If\n");
			}
			lua_rawgeti(l, -1, 1); // Condition.
			res->D.If.Cond = CclParseNumberDesc(l);
			lua_rawgeti(l, -1, 2); // Then.
			res->D.If.True = CclParseStringDesc(l);
			if (luaL_getn(l, -1) == 3) {
				lua_rawgeti(l, -1, 3); // Else.
				res->D.If.False = CclParseStringDesc(l);
			}
			lua_pop(l, 1); // table.
		} else if (!strcmp(key, "SubString")) {
			res->e = EString_SubString;
			if (luaL_getn(l, -1) != 2 && luaL_getn(l, -1) != 3) {
				LuaError(l, "Bad number of args in SubString\n");
			}
			lua_rawgeti(l, -1, 1); // String.
			res->D.SubString.String = CclParseStringDesc(l);
			lua_rawgeti(l, -1, 2); // Begin.
			res->D.SubString.Begin = CclParseNumberDesc(l);
			if (luaL_getn(l, -1) == 3) {
				lua_rawgeti(l, -1, 3); // End.
				res->D.SubString.End = CclParseNumberDesc(l);
			}
			lua_pop(l, 1); // table.
		} else if (!strcmp(key, "Line")) {
			res->e = EString_Line;
			if (luaL_getn(l, -1) < 2 || luaL_getn(l, -1) > 4) {
				LuaError(l, "Bad number of args in Line\n");
			}
			lua_rawgeti(l, -1, 1); // Line.
			res->D.Line.Line = CclParseNumberDesc(l);
			lua_rawgeti(l, -1, 2); // String.
			res->D.Line.String = CclParseStringDesc(l);
			if (luaL_getn(l, -1) >= 3) {
				lua_rawgeti(l, -1, 3); // Lenght.
				res->D.Line.MaxLen = CclParseNumberDesc(l);
			}
			res->D.Line.Font = NULL;
			if (luaL_getn(l, -1) >= 4) {
				lua_rawgeti(l, -1, 4); // Font.
				res->D.Line.Font = CFont::Get(LuaToString(l, -1));
				if (!res->D.Line.Font) {
					LuaError(l, "Bad Font name :'%s'" _C_ LuaToString(l, -1));
				}
				lua_pop(l, 1); // font name.
			}
			lua_pop(l, 1); // table.
		} else {
			lua_pop(l, 1);
			LuaError(l, "unknow condition '%s'"_C_ key);
		}
	} else {
		LuaError(l, "Parse Error in ParseString");
	}
	lua_pop(l, 1);
	return res;
}

/**
**  compute the Unit expression
**
**  @param unitdesc  struct with definition of the calculation.
**
**  @return          the result unit.
*/
CUnit *EvalUnit(const UnitDesc *unitdesc)
{
	Assert(unitdesc);

	if (NumSelected > 0) {
		TriggerData.Active = Selected[0];
	} else {
		TriggerData.Active = UnitUnderCursor;
	}
	switch (unitdesc->e) {
		case EUnit_Ref :
			return *unitdesc->D.AUnit;
	}
	return NULL;
}

/**
**  compute the number expression
**
**  @param number  struct with definition of the calculation.
**
**  @return        the result number.
**
**  @todo Manage better the error (div/0, unit==NULL, ...).
*/
int EvalNumber(const NumberDesc *number)
{
	CUnit *unit;
	char *s;
	char *s2;
	int a;
	int b;

	Assert(number);
	switch (number->e) {
		case ENumber_Lua :     // a lua function.
			return CallLuaNumberFunction(number->D.Index);
		case ENumber_Dir :     // directly a number.
			return number->D.Val;
		case ENumber_Add :     // a + b.
			return EvalNumber(number->D.BinOp.Left) + EvalNumber(number->D.BinOp.Right);
		case ENumber_Sub :     // a - b.
			return EvalNumber(number->D.BinOp.Left) - EvalNumber(number->D.BinOp.Right);
		case ENumber_Mul :     // a * b.
			return EvalNumber(number->D.BinOp.Left) * EvalNumber(number->D.BinOp.Right);
		case ENumber_Div :     // a / b.
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			if (!b) { // FIXME : manage better this.
				return 0;
			}
			return a / b;
		case ENumber_Min :     // a <= b ? a : b
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a <= b ? a : b);
		case ENumber_Max :     // a >= b ? a : b
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a >= b ? a : b);
		case ENumber_Gt  :     // a > b  ? 1 : 0
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a > b ? 1 : 0);
		case ENumber_GtEq :    // a >= b ? 1 : 0
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a >= b ? 1 : 0);
		case ENumber_Lt  :     // a < b  ? 1 : 0
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a < b ? 1 : 0);
		case ENumber_LtEq :    // a <= b ? 1 : 0
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a <= b ? 1 : 0);
		case ENumber_Eq  :     // a == b ? 1 : 0
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a == b ? 1 : 0);
		case ENumber_NEq  :    // a != b ? 1 : 0
			a = EvalNumber(number->D.BinOp.Left);
			b = EvalNumber(number->D.BinOp.Right);
			return (a != b ? 1 : 0);

		case ENumber_Rand :    // random(a) [0..a-1]
			a = EvalNumber(number->D.N);
			return SyncRand() % a;
		case ENumber_UnitStat : // property of unit.
			unit = EvalUnit(number->D.UnitStat.Unit);
			if (unit != NULL) {
				return GetComponent(unit, number->D.UnitStat.Index,
					number->D.UnitStat.Component, number->D.UnitStat.Loc).i;
			} else { // ERROR.
				return 0;
			}
		case ENumber_VideoTextLength : // VideoTextLength(font, s)
			if (number->D.VideoTextLength.String != NULL &&
					(s = EvalString(number->D.VideoTextLength.String)) != NULL) {
				a = number->D.VideoTextLength.Font->Width(s);
				delete s;
				return a;
			} else { // ERROR.
				return 0;
			}
		case ENumber_StringFind : // strchr(s, c) - s
			if (number->D.StringFind.String != NULL &&
					(s = EvalString(number->D.StringFind.String)) != NULL) {
				s2 = strchr(s, number->D.StringFind.C);
				a = s2 ? s2 - s : -1;
				delete s;
				return a;
			} else { // ERROR.
				return 0;
			}
	}
	return 0;
}

/**
**  compute the string expression
**
**  @param s  struct with definition of the calculation.
**
**  @return   the result string.
**
**  @todo Manage better the error.
*/
char *EvalString(const StringDesc *s)
{
	char *res;   // Result string.
	int i;       // Iterator.
	char *tmp1;  // Temporary string.
	char *tmp2;  // Temporary string.
	const CUnit *unit;  // Temporary unit
	char *str;

	Assert(s);
	switch (s->e) {
		case EString_Lua :     // a lua function.
			return CallLuaStringFunction(s->D.Index);
		case EString_Dir :     // directly a string.
			return new_strdup(s->D.Val);
		case EString_Concat :     // a + b -> "ab"
			tmp1 = EvalString(s->D.Concat.Strings[0]);
			if (!tmp1) {
				tmp1 = new char[1];
				tmp1[0] = '\0';
			}
			res = tmp1;
			for (i = 1; i < s->D.Concat.n; i++) {
				tmp2 = EvalString(s->D.Concat.Strings[i]);
				if (tmp2) {
					res = strdcat(tmp1, tmp2);
					delete[] tmp1;
					delete[] tmp2;
					tmp1 = res;
				}
			}
			return res;
		case EString_String :     // 42 -> "42".
			res = new char[10]; // Should be enough ?
			sprintf(res, "%d", EvalNumber(s->D.Number));
			return res;
		case EString_InverseVideo : // "a" -> "~<a~>"
			tmp1 = EvalString(s->D.String);
			// FIXME replace existing "~<" by "~>" in tmp1.
			res = strdcat3("~<", tmp1, "~>");
			delete[] tmp1;
			return res;
		case EString_UnitName : // name of the UnitType
			unit = EvalUnit(s->D.Unit);
			if (unit != NULL) {
				return new_strdup(unit->Type->Name.c_str());
			} else { // ERROR.
				return NULL;
			}
		case EString_If : // cond ? True : False;
			if (EvalNumber(s->D.If.Cond)) {
				return EvalString(s->D.If.True);
			} else if (s->D.If.False) {
				return EvalString(s->D.If.False);
			} else {
				str = new char[1];
				str[0] = '\0';
				return str;
			}
		case EString_SubString : // substring(s, begin, end)
			if (s->D.SubString.String != NULL &&
					(tmp1 = EvalString(s->D.SubString.String)) != NULL) {
				int begin;
				int end;

				begin = EvalNumber(s->D.SubString.Begin);
				if ((unsigned) begin > strlen(tmp1) && begin > 0) {
					delete[] tmp1;
					str = new char[1];
					str[0] = '\0';
					return str;
				}
				res = new_strdup(tmp1 + begin);
				delete[] tmp1;
				if (s->D.SubString.End) {
					end = EvalNumber(s->D.SubString.End);
				} else {
					end = -1;
				}
				if ((unsigned)end < strlen(res) && end >= 0) {
					res[end] = '\0';
				}
				return res;
			} else { // ERROR.
				str = new char[1];
				str[0] = '\0';
				return str;
			}
		case EString_Line : // line n of the string
			if (s->D.Line.String == NULL ||
					(tmp1 = EvalString(s->D.Line.String)) == NULL) {
				str = new char[1];
				str[0] = '\0';
				return str; // ERROR.
			} else {
				int line;
				int maxlen;
				CFont *font;

				line = EvalNumber(s->D.Line.Line);
				if (line <= 0) {
					delete[] tmp1;
					str = new char[1];
					str[0] = '\0';
					return str;
				}
				if (s->D.Line.MaxLen) {
					maxlen = EvalNumber(s->D.Line.MaxLen);
					if (maxlen < 0) {
						maxlen = 0;
					}
				} else {
					maxlen = 0;
				}
				font = s->D.Line.Font;
				res = GetLineFont(line, tmp1, maxlen, font);
				delete[] tmp1;
				if (!res) { // ERROR.
					str = new char[1];
					str[0] = '\0';
					res = str;
				}
				return res;
			}
	}
	return NULL;
}


/**
**  Free the unit expression content. (not the pointer itself).
**
**  @param unitdesc  struct to free
*/
void FreeUnitDesc(UnitDesc *unitdesc)
{
#if 0 // Nothing to free mow.
	if (!unitdesc) {
		return;
	}
#endif
}

/**
**  Free the number expression content. (not the pointer itself).
**
**  @param number  struct to free
*/
void FreeNumberDesc(NumberDesc *number)
{
	if (number == 0) {
		return;
	}
	switch (number->e) {
		case ENumber_Lua :     // a lua function.
			// FIXME: when lua table should be freed ?
		case ENumber_Dir :     // directly a number.
			break;
		case ENumber_Add :     // a + b.
		case ENumber_Sub :     // a - b.
		case ENumber_Mul :     // a * b.
		case ENumber_Div :     // a / b.
		case ENumber_Min :     // a <= b ? a : b
		case ENumber_Max :     // a >= b ? a : b
		case ENumber_Gt  :     // a > b  ? 1 : 0
		case ENumber_GtEq :    // a >= b ? 1 : 0
		case ENumber_Lt  :     // a < b  ? 1 : 0
		case ENumber_LtEq :    // a <= b ? 1 : 0
		case ENumber_NEq  :    // a <> b ? 1 : 0
		case ENumber_Eq  :     // a == b ? 1 : 0
			FreeNumberDesc(number->D.BinOp.Left);
			FreeNumberDesc(number->D.BinOp.Right);
			delete number->D.BinOp.Left;
			delete number->D.BinOp.Right;
			break;
		case ENumber_Rand :    // random(a) [0..a-1]
			FreeNumberDesc(number->D.N);
			delete number->D.N;
			break;
		case ENumber_UnitStat : // property of unit.
			FreeUnitDesc(number->D.UnitStat.Unit);
			delete number->D.UnitStat.Unit;
			break;
		case ENumber_VideoTextLength : // VideoTextLength(font, s)
			FreeStringDesc(number->D.VideoTextLength.String);
			delete number->D.VideoTextLength.String;
			break;
		case ENumber_StringFind : // strchr(s, c) - s.
			FreeStringDesc(number->D.StringFind.String);
			delete number->D.StringFind.String;
			break;
	}
}

/**
**  Free the String expression content. (not the pointer itself).
**
**  @param s  struct to free
*/
void FreeStringDesc(StringDesc *s)
{
	int i;

	if (s == 0) {
		return;
	}
	switch (s->e) {
		case EString_Lua :     // a lua function.
			// FIXME: when lua table should be freed ?
			break;
		case EString_Dir :     // directly a string.
			delete[] s->D.Val;
			break;
		case EString_Concat :  // "a" + "b" -> "ab"
			for (i = 0; i < s->D.Concat.n; i++) {
				FreeStringDesc(s->D.Concat.Strings[i]);
				delete s->D.Concat.Strings[i];
			}
			delete[] s->D.Concat.Strings;

			break;
		case EString_String : // 42 -> "42"
			FreeNumberDesc(s->D.Number);
			delete s->D.Number;
			break;
		case EString_InverseVideo : // "a" -> "~<a~>"
			FreeStringDesc(s->D.String);
			delete s->D.String;
			break;
		case EString_UnitName : // Name of the UnitType
			FreeUnitDesc(s->D.Unit);
			delete s->D.Unit;
			break;
		case EString_If : // cond ? True : False;
			FreeNumberDesc(s->D.If.Cond);
			delete s->D.If.Cond;
			FreeStringDesc(s->D.If.True);
			delete s->D.If.True;
			FreeStringDesc(s->D.If.False);
			delete s->D.If.False;
			break;
		case EString_SubString : // substring(s, begin, end)
			FreeStringDesc(s->D.SubString.String);
			delete s->D.SubString.String;
			FreeNumberDesc(s->D.SubString.Begin);
			delete s->D.SubString.Begin;
			FreeNumberDesc(s->D.SubString.End);
			delete s->D.SubString.End;
			break;
		case EString_Line : // line n of the string
			FreeStringDesc(s->D.Line.String);
			delete s->D.Line.String;
			FreeNumberDesc(s->D.Line.Line);
			delete s->D.Line.Line;
			FreeNumberDesc(s->D.Line.MaxLen);
			delete s->D.Line.MaxLen;
			break;
	}
}

/*............................................................................
..  Aliases
............................................................................*/

/**
**  Make alias for some unit Variable function.
**
**  @param l  lua State.
**  @param s  FIXME: docu
**
**  @return   the lua table {"UnitVar", {Unit = s, Variable = arg1,
**                           Component = "Value" or arg2, Loc = [012]}
*/
static int AliasUnitVar(lua_State *l, const char *s)
{
	int nargs; // number of args in lua.

	Assert(0 < lua_gettop(l) && lua_gettop(l) <= 3);
	nargs = lua_gettop(l);
	lua_newtable (l);
	lua_pushnumber(l, 1);
	lua_pushstring(l, "UnitVar");
	lua_rawset(l, -3);
	lua_pushnumber(l, 2);
	lua_newtable (l);

	lua_pushstring(l, "Unit");
	lua_pushstring(l, s);
	lua_rawset(l, -3);
	lua_pushstring(l, "Variable");
	lua_pushvalue(l, 1);
	lua_rawset(l, -3);
	lua_pushstring(l, "Component");
	if (nargs >= 2) {
		lua_pushvalue(l, 2);
	} else {
		lua_pushstring(l, "Value");
	}
	lua_rawset(l, -3);
	lua_pushstring(l, "Loc");
	if (nargs >= 3) {
		//  Warning: type is for unit->Stats->Var...
		//           and Initial is for unit->Type->Var... (no upgrade modification)
		char *sloc[] = {"Unit", "Initial", "Type", NULL};
		int i;
		const char *key;

		key = LuaToString(l, 3);
		for (i = 0; sloc[i] != NULL; i++) {
			if (!strcmp(key, sloc[i])) {
				lua_pushnumber(l, i);
				break ;
			}
		}
		if (sloc[i] == NULL) {
			LuaError(l, "Bad loc :'%s'" _C_ key);
		}
	} else {
		lua_pushnumber(l, 0);
	}
	lua_rawset(l, -3);

	lua_rawset(l, -3);
	return 1;
}

/**
**  Return equivalent lua table for .
**  {"Unit", {Unit = "Attacker", Variable = arg1, Component = "Value" or arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclUnitAttackerVar(lua_State *l)
{
	if (lua_gettop(l) == 0 || lua_gettop(l) > 3) {
		LuaError(l, "Bad number of arg for AttackerVar()\n");
	}
	return AliasUnitVar(l, "Attacker");
}

/**
**  Return equivalent lua table for .
**  {"Unit", {Unit = "Defender", Variable = arg1, Component = "Value" or arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclUnitDefenderVar(lua_State *l)
{
	if (lua_gettop(l) == 0 || lua_gettop(l) > 3) {
		LuaError(l, "Bad number of arg for DefenderVar()\n");
	}
	return AliasUnitVar(l, "Defender");
}

/**
**  Return equivalent lua table for .
**  {"Unit", {Unit = "Active", Variable = arg1, Component = "Value" or arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclActiveUnitVar(lua_State *l)
{
	if (lua_gettop(l) == 0 || lua_gettop(l) > 3) {
		LuaError(l, "Bad number of arg for ActiveUnitVar()\n");
	}
	return AliasUnitVar(l, "Active");
}


/**
**  Make alias for some function.
**
**  @param l  lua State.
**  @param s  FIXME: docu
**
**  @return the lua table {s, {arg1, arg2, ..., argn}} or {s, arg1}
*/
static int Alias(lua_State *l, const char *s)
{
	int i;     // iterator on argument.
	int narg;  // number of argument

	narg = lua_gettop(l);
	Assert(narg);
	lua_newtable (l);
	lua_pushnumber(l, 1);
	lua_pushstring(l, s);
	lua_rawset(l, -3);
	lua_pushnumber(l, 2);
	if (narg > 1) {
		lua_newtable (l);
		for (i = 1; i <= narg; i++) {
			lua_pushnumber(l, i);
			lua_pushvalue(l, i);
			lua_rawset(l, -3);
		}
	} else {
		lua_pushvalue(l, 1);
	}
	lua_rawset(l, -3);
	return 1;
}

/**
**  Return equivalent lua table for add.
**  {"Add", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclAdd(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Add");
}

/**
**  Return equivalent lua table for add.
**  {"Div", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclSub(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Sub");
}
/**
**  Return equivalent lua table for add.
**  {"Mul", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclMul(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Mul");
}
/**
**  Return equivalent lua table for add.
**  {"Div", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclDiv(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Div");
}
/**
**  Return equivalent lua table for add.
**  {"Min", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclMin(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Min");
}
/**
**  Return equivalent lua table for add.
**  {"Max", {arg1, arg2, argn}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclMax(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Max");
}
/**
**  Return equivalent lua table for add.
**  {"Rand", {arg1}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclRand(lua_State *l)
{
	LuaCheckArgs(l, 1);
	return Alias(l, "Rand");
}
/**
**  Return equivalent lua table for GreaterThan.
**  {"GreaterThan", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclGreaterThan(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "GreaterThan");
}
/**
**  Return equivalent lua table for GreaterThanOrEq.
**  {"GreaterThanOrEq", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclGreaterThanOrEq(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "GreaterThanOrEq");
}
/**
**  Return equivalent lua table for LessThan.
**  {"LessThan", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclLessThan(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "LessThan");
}
/**
**  Return equivalent lua table for LessThanOrEq.
**  {"LessThanOrEq", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclLessThanOrEq(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "LessThanOrEq");
}
/**
**  Return equivalent lua table for Equal.
**  {"Equal", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclEqual(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "Equal");
}
/**
**  Return equivalent lua table for NotEqual.
**  {"NotEqual", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclNotEqual(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "NotEqual");
}



/**
**  Return equivalent lua table for Concat.
**  {"Concat", {arg1}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclConcat(lua_State *l)
{
	if (lua_gettop(l) < 1) { // FIXME do extra job for 1.
		LuaError(l, "Bad number of arg for Concat()\n");
	}
	return Alias(l, "Concat");
}

/**
**  Return equivalent lua table for String.
**  {"String", {arg1}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclString(lua_State *l)
{
	LuaCheckArgs(l, 1);
	return Alias(l, "String");
}
/**
**  Return equivalent lua table for InverseVideo.
**  {"InverseVideo", {arg1}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclInverseVideo(lua_State *l)
{
	LuaCheckArgs(l, 1);
	return Alias(l, "InverseVideo");
}
/**
**  Return equivalent lua table for UnitName.
**  {"UnitName", {arg1}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclUnitName(lua_State *l)
{
	LuaCheckArgs(l, 1);
	return Alias(l, "UnitName");
}
/**
**  Return equivalent lua table for If.
**  {"If", {arg1}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclIf(lua_State *l)
{
	if (lua_gettop(l) != 2 && lua_gettop(l) != 3) {
		LuaError(l, "Bad number of arg for If()\n");
	}
	return Alias(l, "If");
}

/**
**  Return equivalent lua table for SubString.
**  {"SubString", {arg1, arg2, arg3}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclSubString(lua_State *l)
{
	if (lua_gettop(l) != 2 && lua_gettop(l) != 3) {
		LuaError(l, "Bad number of arg for SubString()\n");
	}
	return Alias(l, "SubString");
}

/**
**  Return equivalent lua table for Line.
**  {"Line", {arg1, arg2[, arg3]}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclLine(lua_State *l)
{
	if (lua_gettop(l) < 2 || lua_gettop(l) > 4) {
		LuaError(l, "Bad number of arg for Line()\n");
	}
	return Alias(l, "Line");
}

/**
**  Return equivalent lua table for Line.
**  {"Line", "arg1"}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclGameInfo(lua_State *l)
{
	LuaCheckArgs(l, 1);
	return Alias(l, "GameInfo");
}

/**
**  Return equivalent lua table for VideoTextLength.
**  {"VideoTextLength", {Text = arg1, Font = arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclVideoTextLength(lua_State *l)
{
	LuaCheckArgs(l, 2);
	lua_newtable (l);
	lua_pushnumber(l, 1);
	lua_pushstring(l, "VideoTextLength");
	lua_rawset(l, -3);
	lua_pushnumber(l, 2);

	lua_newtable (l);
	lua_pushstring(l, "Font");
	lua_pushvalue(l, 1);
	lua_rawset(l, -3);
	lua_pushstring(l, "Text");
	lua_pushvalue(l, 2);
	lua_rawset(l, -3);

	lua_rawset(l, -3);
	return 1;
}

/**
**  Return equivalent lua table for StringFind.
**  {"StringFind", {arg1, arg2}}
**
**  @param l  Lua state.
**
**  @return   equivalent lua table.
*/
static int CclStringFind(lua_State *l)
{
	LuaCheckArgs(l, 2);
	return Alias(l, "StringFind");
}


static void AliasRegister()
{
	// Number.
	lua_register(Lua, "Add", CclAdd);
	lua_register(Lua, "Sub", CclSub);
	lua_register(Lua, "Mul", CclMul);
	lua_register(Lua, "Div", CclDiv);
	lua_register(Lua, "Min", CclMin);
	lua_register(Lua, "Max", CclMax);
	lua_register(Lua, "Rand", CclRand);

	lua_register(Lua, "GreaterThan", CclGreaterThan);
	lua_register(Lua, "LessThan", CclLessThan);
	lua_register(Lua, "Equal", CclEqual);
	lua_register(Lua, "GreaterThanOrEq", CclGreaterThanOrEq);
	lua_register(Lua, "LessThanOrEq", CclLessThanOrEq);
	lua_register(Lua, "NotEqual", CclNotEqual);
	lua_register(Lua, "VideoTextLength", CclVideoTextLength);
	lua_register(Lua, "StringFind", CclStringFind);


	// Unit
	lua_register(Lua, "AttackerVar", CclUnitAttackerVar);
	lua_register(Lua, "DefenderVar", CclUnitDefenderVar);
	lua_register(Lua, "ActiveUnitVar", CclActiveUnitVar);


	// String.
	lua_register(Lua, "Concat", CclConcat);
	lua_register(Lua, "String", CclString);
	lua_register(Lua, "InverseVideo", CclInverseVideo);
	lua_register(Lua, "UnitName", CclUnitName);
	lua_register(Lua, "SubString", CclSubString);
	lua_register(Lua, "Line", CclLine);
	lua_register(Lua, "GameInfo", CclGameInfo);

	lua_register(Lua, "If", CclIf);
}

/*............................................................................
..  Config
............................................................................*/

/**
**  Return the stratagus library path.
**
**  @param l  Lua state.
**
**  @return   Current libray path.
*/
static int CclStratagusLibraryPath(lua_State *l)
{
	lua_pushstring(l, StratagusLibPath.c_str());
	return 1;
}

/**
**  Return a table with the filtered items found in the subdirectory.
*/
static int CclFilteredListDirectory(lua_State *l, int type, int mask)
{
	char directory[256];
	const char *userdir;
	std::vector<FileList> flp;
	int n;
	int i;
	int j;
	int pathtype;
#ifndef WIN32
	const char *s;
#endif

	LuaCheckArgs(l, 1);
	userdir = lua_tostring(l, 1);
	n = strlen(userdir);

	pathtype = 0; // path relative to stratagus dir
	if (n > 0 && *userdir == '~') {
		// path relative to user preferences directory
		pathtype = 1;
	}

	// security: disallow all special characters
	for (i = pathtype; i < n; i++) {
		if (!(isalnum(userdir[i]) || userdir[i]=='/')) {
			LuaError(l, "Forbidden directory");
		}
	}

	if (pathtype == 1) {
		++userdir;
#ifndef WIN32
		if ((s = getenv("HOME")) && !GameName.empty()) {
			sprintf(directory, "%s/%s/%s/%s",
				s, STRATAGUS_HOME_PATH, GameName.c_str(), userdir);
		} else
#endif
		{
			sprintf(directory, "%s/%s", GameName.c_str(), userdir);
		}
	} else {
		sprintf(directory, "%s/%s", StratagusLibPath.c_str(), userdir);
	}
	lua_pop(l, 1);
	lua_newtable(l);
	n = ReadDataDirectory(directory, NULL, flp);
	for (i = 0, j = 0; i < n; i++) {
		if ((flp[i].type & mask) == type) {
			lua_pushnumber(l, j + 1);
			lua_pushstring(l, flp[i].name);
			lua_settable(l, 1);
			++j;
		}
		delete[] flp[i].name;
	}

	return 1;
}

/**
**  Return a table with the files or directories found in the subdirectory.
*/
static int CclListDirectory(lua_State *l)
{
	return CclFilteredListDirectory(l, 0, 0);
}

/**
**  Return a table with the files found in the subdirectory.
*/
static int CclListFilesInDirectory(lua_State *l)
{
	return CclFilteredListDirectory(l, 0x1, 0x1);
}

/**
**  Return a table with the files found in the subdirectory.
*/
static int CclListDirsInDirectory(lua_State *l)
{
	return CclFilteredListDirectory(l, 0x0, 0x1);
}

/**
**  Return the stratagus game-cycle
**
**  @param l  Lua state.
**
**  @return   Current game cycle.
*/
static int CclGameCycle(lua_State *l)
{
	lua_pushnumber(l, GameCycle);
	return 1;
}

/**
**  Return of game name.
**
**  @param l  Lua state.
*/
static int CclSetGameName(lua_State *l)
{
	int args;

	args = lua_gettop(l);
	if (args > 1 || (args == 1 && (!lua_isnil(l, 1) && !lua_isstring(l, 1)))) {
		LuaError(l, "incorrect argument");
	}
	if (args == 1 && !lua_isnil(l, 1)) {
		GameName = lua_tostring(l, 1);
	}

	return 0;
}

/**
**  Set the stratagus game-cycle
**
**  @param l  Lua state.
*/
static int CclSetGameCycle(lua_State *l)
{
	LuaCheckArgs(l, 1);
	GameCycle = LuaToNumber(l, 1);
	return 0;
}

/**
**  Set the game paused or unpaused
**
**  @param l  Lua state.
*/
static int CclSetGamePaused(lua_State *l)
{
	LuaCheckArgs(l, 1);
	if (!lua_isnumber(l, 1) && !lua_isboolean(l, 1)) {
		LuaError(l, "incorrect argument");
	}
	if (lua_isboolean(l, 1)) {
		GamePaused = lua_toboolean(l, 1) ? true : false;
	} else {
		GamePaused = lua_tonumber(l, 1) ? true : false;
	}
	return 0;
}

/**
**  Set the video sync speed
**
**  @param l  Lua state.
*/
static int CclSetVideoSyncSpeed(lua_State *l)
{
	LuaCheckArgs(l, 1);
	VideoSyncSpeed = LuaToNumber(l, 1);
	return 0;
}

/**
**  Set the game speed
**
**  @param l  Lua state.
*/
static int CclSetGameSpeed(lua_State *l)
{
	LuaCheckArgs(l, 1);
	VideoSyncSpeed = LuaToNumber(l, 1) * 100 / CYCLES_PER_SECOND;
	SetVideoSync();
	return 0;
}

/**
**  Get the game speed
**
**  @param l  Lua state.
*/
static int CclGetGameSpeed(lua_State *l)
{
	LuaCheckArgs(l, 0);
	lua_pushnumber(l, CYCLES_PER_SECOND * VideoSyncSpeed / 100);
	return 1;
}

/**
**  Set the local player name
**
**  @param l  Lua state.
*/
static int CclSetLocalPlayerName(lua_State *l)
{
	const char *str;

	LuaCheckArgs(l, 1);
	str = LuaToString(l, 1);
	strncpy_s(LocalPlayerName, sizeof(LocalPlayerName), str, _TRUNCATE);
	return 0;
}

/**
**  Get the local player name
**
**  @param l  Lua state.
*/
static int CclGetLocalPlayerName(lua_State *l)
{
	LuaCheckArgs(l, 0);
	lua_pushstring(l, LocalPlayerName);
	return 1;
}


/**
**  Affect UseHPForXp.
**
**  @param l  Lua state.
**
**  @return 0.
*/
static int ScriptSetUseHPForXp(lua_State *l)
{
	LuaCheckArgs(l, 1);
	UseHPForXp = LuaToBoolean(l, 1);
	return 0;
}

/**
**  Removes Randomization of Player position in Multiplayer mode
**
**  @param l  Lua state.
*/
static int CclNoRandomPlacementMultiplayer(lua_State *l)
{
	LuaCheckArgs(l, 0);
	NoRandomPlacementMultiplayer = 1;
	return 0;
}

/**
**  Set damage computation method.
**
**  @param l  Lua state.
*/
static int CclSetDamageFormula(lua_State *l)
{
	Assert(l);
	if (Damage) {
		FreeNumberDesc(Damage);
		delete Damage;
	}
	Damage = CclParseNumberDesc(l);
	return 0;
}

/**
**  Set God mode.
**
**  @param l  Lua state.
**
**  @return   The old mode.
*/
static int CclSetGodMode(lua_State *l)
{
	LuaCheckArgs(l, 1);
	GodMode = LuaToBoolean(l, 1);
	return 0;
}

/**
**  Set resource harvesting speed.
**
**  @param l  Lua state.
*/
static int CclSetSpeedResourcesHarvest(lua_State *l)
{
	int i;
	const char *resource;

	LuaCheckArgs(l, 2);
	resource = LuaToString(l, 1);
	for (i = 0; i < MaxCosts; ++i) {
		if (!strcmp(resource, DefaultResourceNames[i])) {
			SpeedResourcesHarvest[i] = LuaToNumber(l, 2);
			return 0;
		}
	}
	LuaError(l, "Resource not found: %s" _C_ resource);

	return 0;
}

/**
**  Set resource returning speed.
**
**  @param l  Lua state.
*/
static int CclSetSpeedResourcesReturn(lua_State *l)
{
	int i;
	const char *resource;

	LuaCheckArgs(l, 2);
	resource = LuaToString(l, 1);
	for (i = 0; i < MaxCosts; ++i) {
		if (!strcmp(resource, DefaultResourceNames[i])) {
			SpeedResourcesReturn[i] = LuaToNumber(l, 2);
			return 0;
		}
	}
	LuaError(l, "Resource not found: %s" _C_ resource);

	return 0;
}

/**
**  For debug increase building speed.
**
**  @param l  Lua state.
*/
static int CclSetSpeedBuild(lua_State *l)
{
	LuaCheckArgs(l, 1);
	SpeedBuild = LuaToNumber(l, 1);

	lua_pushnumber(l, SpeedBuild);
	return 1;
}

/**
**  For debug increase training speed.
**
**  @param l  Lua state.
*/
static int CclSetSpeedTrain(lua_State *l)
{
	LuaCheckArgs(l, 1);
	SpeedTrain = LuaToNumber(l, 1);

	lua_pushnumber(l, SpeedTrain);
	return 1;
}

/**
**  For debug increase upgrading speed.
**
**  @param l  Lua state.
*/
static int CclSetSpeedUpgrade(lua_State *l)
{
	LuaCheckArgs(l, 1);
	SpeedUpgrade = LuaToNumber(l, 1);

	lua_pushnumber(l, SpeedUpgrade);
	return 1;
}

/**
**  For debug increase researching speed.
**
**  @param l  Lua state.
*/
static int CclSetSpeedResearch(lua_State *l)
{
	LuaCheckArgs(l, 1);
	SpeedResearch = LuaToNumber(l, 1);

	lua_pushnumber(l, SpeedResearch);
	return 1;
}

/**
**  For debug increase all speeds.
**
**  @param l  Lua state.
*/
static int CclSetSpeeds(lua_State *l)
{
	int i;
	int s;

	LuaCheckArgs(l, 1);
	s = LuaToNumber(l, 1);
	for (i = 0; i < MaxCosts; ++i) {
		SpeedResourcesHarvest[i] = s;
		SpeedResourcesReturn[i] = s;
	}
	SpeedBuild = SpeedTrain = SpeedUpgrade = SpeedResearch = s;

	lua_pushnumber(l, s);
	return 1;
}

/**
**  Define default incomes for a new player.
**
**  @param l  Lua state.
*/
static int CclDefineDefaultIncomes(lua_State *l)
{
	int i;
	int args;

	args = lua_gettop(l);
	for (i = 0; i < MaxCosts && i < args; ++i) {
		DefaultIncomes[i] = LuaToNumber(l, i + 1);
	}
	return 0;
}

/**
**  Define default action for the resources.
**
**  @param l  Lua state.
*/
static int CclDefineDefaultActions(lua_State *l)
{
	int i;
	int args;

	for (i = 0; i < MaxCosts; ++i) {
		delete[] DefaultActions[i];
		DefaultActions[i] = NULL;
	}
	args = lua_gettop(l);
	for (i = 0; i < MaxCosts && i < args; ++i) {
		DefaultActions[i] = new_strdup(LuaToString(l, i + 1));
	}
	return 0;
}

/**
**  Define default names for the resources.
**
**  @param l  Lua state.
*/
static int CclDefineDefaultResourceNames(lua_State *l)
{
	int i;
	int args;

	for (i = 0; i < MaxCosts; ++i) {
		delete[] DefaultResourceNames[i];
		DefaultResourceNames[i] = NULL;
	}
	args = lua_gettop(l);
	for (i = 0; i < MaxCosts && i < args; ++i) {
		DefaultResourceNames[i] = new_strdup(LuaToString(l, i + 1));
	}
	return 0;
}

/**
**  Define default names for the resources.
**
**  @param l  Lua state.
*/
static int CclDefineDefaultResourceAmounts(lua_State *l)
{
	int i;
	int j;
	const char *value;
	int args;

	args = lua_gettop(l);
	if (args & 1) {
		LuaError(l, "incorrect argument");
	}
	for (j = 0; j < args; ++j) {
		value = LuaToString(l, j + 1);
		for (i = 0; i < MaxCosts; ++i) {
			if (!strcmp(value, DefaultResourceNames[i])) {
				++j;
				DefaultResourceAmounts[i] = LuaToNumber(l, j + 1);
				break;
			}
		}
		if (i == MaxCosts) {
			LuaError(l, "Resource not found: %s" _C_ value);
		}
	}
	return 0;
}

/**
**  Compiled with sound.
**
**  @param l  Lua state.
*/
static int CclGetCompileFeature(lua_State *l)
{
	const char *str;

	LuaCheckArgs(l, 1);

	str = LuaToString(l, 1);
	if (CompileOptions.find(str) != std::string::npos) {
		DebugPrint("I have %s\n" _C_ str);
		lua_pushboolean(l, 1);
	} else {
		DebugPrint("I don't have %s\n" _C_ str);
		lua_pushboolean(l, 0);
	}

	return 1;
}

/**
**  Get a value from the Stratagus syncronized random number generator.
**
**  @param l  Lua state.
*/
static int CclSyncRand(lua_State *l)
{
	LuaCheckArgs(l, 1);

	lua_pushnumber(l, SyncRand() % (int)LuaToNumber(l, -1));
	return 1;
}

/*............................................................................
..  Tables
............................................................................*/

/**
**  Load a map. (Try in library path first)
**
**  @param l  Lua state.
*/
static int CclLoadMap(lua_State *l)
{
	const char *name;

	LuaCheckArgs(l, 1);
	name = LuaToString(l, 1);

	// TODO Check if there a map has already been loaded. 
	//  If true, memory needs to be freed.

	//MAPTODO load stratagus map !!!!!!!!!!!

	LuaError(l, "unknown map format");
	return 0;
}

/*............................................................................
..  Commands
............................................................................*/

/**
**  Send command to ccl.
**
**  @param command  Zero terminated command string.
*/
int CclCommand(const char *command)
{
	int status;

	if (!(status = luaL_loadbuffer(Lua, command, strlen(command), command))) {
		LuaCall(0, 1);
	} else {
		report(status);
	}
	return status;
}

/*............................................................................
..  Setup
............................................................................*/

int tolua_stratagus_open(lua_State *tolua_S);

/**
**  Initialize ccl and load the config file(s).
*/
void InitCcl(void)
{
	Lua = lua_open();
	luaopen_base(Lua);
	luaopen_table(Lua);
	luaopen_string(Lua);
	luaopen_math(Lua);
	luaopen_debug(Lua);
	tolua_stratagus_open(Lua);
	lua_settop(Lua, 0);  // discard any results

	lua_register(Lua, "CompileFeature", CclGetCompileFeature);
	lua_register(Lua, "LibraryPath", CclStratagusLibraryPath);
	lua_register(Lua, "ListDirectory", CclListDirectory);
	lua_register(Lua, "ListFilesInDirectory", CclListFilesInDirectory);
	lua_register(Lua, "ListDirsInDirectory", CclListDirsInDirectory);
	lua_register(Lua, "GameCycle", CclGameCycle);
	lua_register(Lua, "SetGameName", CclSetGameName);
	lua_register(Lua, "SetGameCycle", CclSetGameCycle);
	lua_register(Lua, "SetGamePaused", CclSetGamePaused);
	lua_register(Lua, "SetVideoSyncSpeed", CclSetVideoSyncSpeed);
	lua_register(Lua, "SetGameSpeed", CclSetGameSpeed);
	lua_register(Lua, "GetGameSpeed", CclGetGameSpeed);
	lua_register(Lua, "SetLocalPlayerName", CclSetLocalPlayerName);
	lua_register(Lua, "GetLocalPlayerName", CclGetLocalPlayerName);
	lua_register(Lua, "SetGodMode", CclSetGodMode);

	lua_register(Lua, "SetSpeedResourcesHarvest", CclSetSpeedResourcesHarvest);
	lua_register(Lua, "SetSpeedResourcesReturn", CclSetSpeedResourcesReturn);
	lua_register(Lua, "SetSpeedBuild", CclSetSpeedBuild);
	lua_register(Lua, "SetSpeedTrain", CclSetSpeedTrain);
	lua_register(Lua, "SetSpeedUpgrade", CclSetSpeedUpgrade);
	lua_register(Lua, "SetSpeedResearch", CclSetSpeedResearch);
	lua_register(Lua, "SetSpeeds", CclSetSpeeds);
	lua_register(Lua, "SetUseHPForXp", ScriptSetUseHPForXp);
	lua_register(Lua, "SetDamageFormula", CclSetDamageFormula);

	lua_register(Lua, "DefineDefaultIncomes", CclDefineDefaultIncomes);
	lua_register(Lua, "DefineDefaultActions", CclDefineDefaultActions);
	lua_register(Lua, "DefineDefaultResourceNames", CclDefineDefaultResourceNames);
	lua_register(Lua, "DefineDefaultResourceAmounts", CclDefineDefaultResourceAmounts);
	lua_register(Lua, "NoRandomPlacementMultiplayer", CclNoRandomPlacementMultiplayer);

	lua_register(Lua, "SetMetaServer", CclSetMetaServer);

	lua_register(Lua, "SavePreferences", CclSavePreferences);
	lua_register(Lua, "Load", CclLoad);
	lua_register(Lua, "LoadBuffer", CclLoadBuffer);
	lua_register(Lua, "GetCurrentLuaPath", CclGetCurrentLuaPath);
	lua_register(Lua, "SavedGameInfo", CclSavedGameInfo);

	AliasRegister();
	NetworkCclRegister();
	IconCclRegister();
	MissileCclRegister();
	PlayerCclRegister();
	TilesetCclRegister();
	MapCclRegister();
	PathfinderCclRegister();
	ConstructionCclRegister();
	DecorationCclRegister();
	UnitTypeCclRegister();
	UpgradesCclRegister();
	DependenciesCclRegister();
	SelectionCclRegister();
	GroupCclRegister();
	UnitCclRegister();
	SoundCclRegister();
	UserInterfaceCclRegister();
	AiCclRegister();
	TriggerCclRegister();
	SpellCclRegister();

	EditorCclRegister();

	lua_register(Lua, "LoadMap", CclLoadMap);
	lua_register(Lua, "SyncRand", CclSyncRand);
}

/**
**  Save user preferences
*/
void SavePreferences(void)
{
	FILE *fd;
	char buf[PATH_MAX];

	lua_pushstring(Lua, "preferences");
	lua_gettable(Lua, LUA_GLOBALSINDEX);
	if (lua_type(Lua, -1) == LUA_TTABLE) {
#ifdef USE_WIN32
		strcpy_s(buf, sizeof(buf), GameName.c_str());
		mkdir(buf);
		strcat_s(buf, sizeof(buf), "/preferences.lua");
#else
		sprintf(buf, "%s/%s", getenv("HOME"), STRATAGUS_HOME_PATH);
		mkdir(buf, 0777);
		strcat_s(buf, sizeof(buf), "/");
		strcat_s(buf, sizeof(buf), GameName.c_str());
		mkdir(buf, 0777);
		strcat_s(buf, sizeof(buf), "/preferences.lua");
#endif

		fd = fopen(buf, "w");
		if (!fd) {
			return;
		}

		char *s = SaveGlobal(Lua, false);
		fprintf(fd, "preferences = {\n%s}\n", s);
		delete[] s;

		fclose(fd);
	}
}

/**
**  Load stratagus config file.
*/
void LoadCcl(void)
{
	char *file;
	char buf[PATH_MAX];

	//
	//  Load and evaluate configuration file
	//
	CclInConfigFile = 1;
	file = LibraryFileName(CclStartFile, buf, sizeof(buf));
	if (access(buf, R_OK)) {
		printf("Maybe you need to specify another gamepath with '-d /path/to/datadir'?\n");
		ExitFatal(-1);
	}

	ShowLoadProgress("Script %s\n", file);
	LuaLoadFile(file);
	CclInConfigFile = 0;
	CclGarbageCollect(0);  // Cleanup memory after load
}

/**
**  Save CCL Module.
**
**  @param file  Save file.
*/
void SaveCcl(CFile *file)
{
	int i;

	file->printf("SetGodMode(%s)\n", GodMode ? "true" : "false");

	for (i = 0; i < MaxCosts; ++i) {
		file->printf("SetSpeedResourcesHarvest(\"%s\", %d)\n",
			DefaultResourceNames[i], SpeedResourcesHarvest[i]);
		file->printf("SetSpeedResourcesReturn(\"%s\", %d)\n",
			DefaultResourceNames[i], SpeedResourcesReturn[i]);
	}
	file->printf("SetSpeedBuild(%d)\n", SpeedBuild);
	file->printf("SetSpeedTrain(%d)\n", SpeedTrain);
	file->printf("SetSpeedUpgrade(%d)\n", SpeedUpgrade);
	file->printf("SetSpeedResearch(%d)\n", SpeedResearch);
}

//@}
