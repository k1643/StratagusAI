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
/**@name trigger.cpp - The trigger handling. */
//
//      (c) Copyright 2002-2007 by Lutz Sammer and Jimmy Salmon
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
//      $Id: trigger.cpp 7900 2007-03-03 18:18:11Z jsalmon3 $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <setjmp.h>

#include "stratagus.h"
#include "script.h"
#include "unittype.h"
#include "player.h"
#include "trigger.h"
#include "results.h"
#include "interface.h"
#include "unit.h"
#include "iolib.h"


/*----------------------------------------------------------------------------
--  Variables
----------------------------------------------------------------------------*/

CTimer GameTimer;               /// The game timer
static unsigned long WaitFrame; /// Frame to wait for
static int Trigger;
static int WaitTrigger;
static bool *ActiveTriggers;

/// Some data accessible for script during the game.
TriggerDataType TriggerData;


/*----------------------------------------------------------------------------
--  Functions
----------------------------------------------------------------------------*/

/**
**  Get player number.
**
**  @param l  Lua state.
**
**  @return   The player number, -1 matches any.
*/
int TriggerGetPlayer(lua_State *l)
{
	const char *player;
	int ret;

	if (lua_isnumber(l, -1)) {
		ret = LuaToNumber(l, -1);
		if (ret < 0 || ret > PlayerMax) {
			LuaError(l, "bad player: %d" _C_ ret);
		}
		return ret;
	}
	player = LuaToString(l, -1);
	if (!strcmp(player, "any")) {
		return -1;
	} else if (!strcmp(player, "this")) {
		return ThisPlayer->Index;
	}
	LuaError(l, "bad player: %s" _C_ player);

	return 0;
}

/**
**  Get the unit-type.
**
**  @param l  Lua state.
**
**  @return   The unit-type pointer.
*/
const CUnitType *TriggerGetUnitType(lua_State *l)
{
	const char *unit;

	unit = LuaToString(l, -1);
	if (!strcmp(unit, "any")) {
		return ANY_UNIT;
	} else if (!strcmp(unit, "all")) {
		return ALL_UNITS;
	} else if (!strcmp(unit, "units")) {
		return ALL_FOODUNITS;
	} else if (!strcmp(unit, "buildings")) {
		return ALL_BUILDINGS;
	}

	return CclGetUnitType(l);
}

/*--------------------------------------------------------------------------
--  Conditions
--------------------------------------------------------------------------*/
static int CompareEq(int a, int b)
{
	return a == b;
}
static int CompareNEq(int a, int b)
{
	return a != b;
}
static int CompareGrEq(int a, int b)
{
	return a >= b;
}
static int CompareGr(int a, int b)
{
	return a > b;
}
static int CompareLeEq(int a, int b)
{
	return a <= b;
}
static int CompareLe(int a, int b)
{
	return a < b;
}

typedef int (*CompareFunction)(int, int);

/**
**  Returns a function pointer to the comparison function
**
**  @param op  The operation
**
**  @return    Function pointer to the compare function
*/
static CompareFunction GetCompareFunction(const char *op)
{
	if (op[0] == '=') {
		if ((op[1] == '=' && op[2] == '\0') || (op[1] == '\0')) {
			return &CompareEq;
		}
	} else if (op[0] == '>') {
		if (op[1] == '=' && op[2] == '\0') {
			return &CompareGrEq;
		} else if (op[1] == '\0') {
			return &CompareGr;
		}
	} else if (op[0] == '<') {
		if (op[1] == '=' && op[2] == '\0') {
			return &CompareLeEq;
		} else if (op[1] == '\0') {
			return &CompareLe;
		}
	} else if (op[0] == '!' && op[1] == '=' && op[2] == '\0') {
		return &CompareNEq;
	}
	return NULL;
}

/**
**  Return the number of units of a giver unit-type and player at a location.
*/
static int CclGetNumUnitsAt(lua_State *l)
{
	int plynr;
	int x1;
	int y1;
	int x2;
	int y2;
	const CUnitType *unittype;
	CUnit *table[UnitMax];
	CUnit *unit;
	int an;
	int j;
	int s;

	LuaCheckArgs(l, 4);

	plynr = LuaToNumber(l, 1);
	lua_pushvalue(l, 2);
	unittype = TriggerGetUnitType(l);
	lua_pop(l, 1);
	if (!lua_istable(l, 3) || luaL_getn(l, 3) != 2) {
		LuaError(l, "incorrect argument");
	}
	lua_rawgeti(l, 3, 1);
	x1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 3, 2);
	y1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	if (!lua_istable(l, 4) || luaL_getn(l, 4) != 2) {
		LuaError(l, "incorrect argument");
	}
	lua_rawgeti(l, 4, 1);
	x2 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 4, 2);
	y2 = LuaToNumber(l, -1);
	lua_pop(l, 1);

	//
	// Get all unit types in location.
	//
	// FIXME: I hope SelectUnits checks bounds?
	// FIXME: Yes, but caller should check.
	// NOTE: +1 right,bottom isn't inclusive :(
	an = UnitCacheSelect(x1, y1, x2 + 1, y2 + 1, table);
	//
	// Count the requested units
	//
	for (j = s = 0; j < an; ++j) {
		unit = table[j];
		//
		// Check unit type
		//
		// FIXME: ALL_UNITS
		if (unittype == ANY_UNIT ||
				(unittype == ALL_FOODUNITS && !unit->Type->Building) ||
				(unittype == ALL_BUILDINGS && unit->Type->Building) ||
				(unittype == unit->Type && !unit->Constructed)) {
			//
			// Check the player
			//
			if (plynr == -1 || plynr == unit->Player->Index) {
				++s;
			}
		}
	}
	lua_pushnumber(l, s);
	return 1;
}

/**
**  Player has the quantity of unit-type near to unit-type.
*/
static int CclIfNearUnit(lua_State *l)
{
	int plynr;
	int q;
	int n;
	int i;
	const CUnitType *unittype;
	const CUnitType *ut2;
	const char *op;
	CUnit *table[UnitMax];
	CompareFunction compare;

	LuaCheckArgs(l, 5);

	lua_pushvalue(l, 1);
	plynr = TriggerGetPlayer(l);
	lua_pop(l, 1);
	op = LuaToString(l, 2);
	q = LuaToNumber(l, 3);
	lua_pushvalue(l, 4);
	unittype = TriggerGetUnitType(l);
	lua_pop(l, 1);
	ut2 = CclGetUnitType(l);

	compare = GetCompareFunction(op);
	if (!compare) {
		LuaError(l, "Illegal comparison operation in if-near-unit: %s" _C_ op);
	}

	//
	// Get all unit types 'near'.
	//
	n = FindUnitsByType(ut2, table);
	for (i = 0; i < n; ++i) {
		CUnit *unit;
		CUnit *around[UnitMax];
		int an;
		int j;
		int s;

		unit = table[i];

		// FIXME: I hope SelectUnits checks bounds?
		// FIXME: Yes, but caller should check.
		// NOTE: +1 right,bottom isn't inclusive :(
		if (unit->Type->UnitType == UnitTypeLand) {
			an = UnitCacheSelect(unit->X - 1, unit->Y - 1,
				unit->X + unit->Type->TileWidth + 1,
				unit->Y + unit->Type->TileHeight + 1, around);
		} else {
			an = UnitCacheSelect(unit->X - 2, unit->Y - 2,
				unit->X + unit->Type->TileWidth + 2,
				unit->Y + unit->Type->TileHeight + 2, around);
		}
		//
		// Count the requested units
		//
		for (j = s = 0; j < an; ++j) {
			unit = around[j];
			//
			// Check unit type
			//
			// FIXME: ALL_UNITS
			if (unittype == ANY_UNIT ||
					(unittype == ALL_FOODUNITS && !unit->Type->Building) ||
					(unittype == ALL_BUILDINGS && unit->Type->Building) ||
					(unittype == unit->Type)) {
				//
				// Check the player
				//
				if (plynr == -1 || plynr == unit->Player->Index) {
					++s;
				}
			}
		}
		// Check if we counted the unit near itself
		if (unittype == ANY_UNIT ||
				(unittype == ALL_FOODUNITS && ut2->Building) ||
				(unittype == ALL_BUILDINGS && ut2->Building)) {
			--s;
		}
		if (compare(s, q)) {
			lua_pushboolean(l, 1);
			return 1;
		}
	}

	lua_pushboolean(l, 0);
	return 1;
}

/**
** Player has the quantity of rescued unit-type near to unit-type.
*/
static int CclIfRescuedNearUnit(lua_State *l)
{
	int plynr;
	int q;
	int n;
	int i;
	const CUnitType *unittype;
	const CUnitType *ut2;
	const char *op;
	CUnit *table[UnitMax];
	CompareFunction compare;

	LuaCheckArgs(l, 5);

	lua_pushvalue(l, 1);
	plynr = TriggerGetPlayer(l);
	lua_pop(l, 1);
	op = LuaToString(l, 2);
	q = LuaToNumber(l, 3);
	lua_pushvalue(l, 4);
	unittype = TriggerGetUnitType(l);
	lua_pop(l, 1);
	ut2 = CclGetUnitType(l);

	compare = GetCompareFunction(op);
	if (!compare) {
		LuaError(l, "Illegal comparison operation in if-rescued-near-unit: %s" _C_ op);
	}

	//
	// Get all unit types 'near'.
	//
	n = FindUnitsByType(ut2, table);
	for (i = 0; i < n; ++i) {
		CUnit *unit;
		CUnit *around[UnitMax];
		int an;
		int j;
		int s;

		unit = table[i];

		// FIXME: I hope SelectUnits checks bounds?
		// FIXME: Yes, but caller should check.
		// NOTE: +1 right,bottom isn't inclusive :(
		if (unit->Type->UnitType == UnitTypeLand) {
			an = UnitCacheSelect(unit->X - 1, unit->Y - 1,
				unit->X + unit->Type->TileWidth + 1,
				unit->Y + unit->Type->TileHeight + 1, around);
		} else {
			an = UnitCacheSelect(unit->X - 2, unit->Y - 2,
				unit->X + unit->Type->TileWidth + 2,
				unit->Y + unit->Type->TileHeight + 2, around);
		}
		//
		// Count the requested units
		//
		for (j = s = 0; j < an; ++j) {
			unit = around[j];
			if (unit->RescuedFrom) { // only rescued units
				//
				// Check unit type
				//
				// FIXME: ALL_UNITS
				if (unittype == ANY_UNIT ||
						(unittype == ALL_FOODUNITS && !unit->Type->Building) ||
						(unittype == ALL_BUILDINGS && unit->Type->Building) ||
						(unittype == unit->Type)) {
					//
					// Check the player
					//
					if (plynr == -1 || plynr == unit->Player->Index) {
						++s;
					}
				}
			}
		}
		// Check if we counted the unit near itself
		if (unittype == ANY_UNIT ||
				(unittype == ALL_FOODUNITS && ut2->Building) ||
				(unittype == ALL_BUILDINGS && ut2->Building)) {
			--s;
		}
		if (compare(s, q)) {
			lua_pushboolean(l, 1);
			return 1;
		}
	}

	lua_pushboolean(l, 0);
	return 1;
}

/**
**  Returns the number of opponents of a given player.
*/
static int CclGetNumOpponents(lua_State *l)
{
	int plynr;
	int n;
	int i;

	LuaCheckArgs(l, 1);

	plynr = LuaToNumber(l, 1);

	// Check the player opponents
	n = 0;
	for (i = 0; i < PlayerMax; ++i) {
		// This player is our enemy and has units left.
		if (((Players[plynr].Enemy & (1 << i)) || (Players[i].Enemy & (1 << plynr))) &&
				Players[i].TotalNumUnits) {
			++n;
		}
	}

	lua_pushnumber(l, n);
	return 1;
}

/**
**  Check the timer value
*/
static int CclGetTimer(lua_State *l)
{
	LuaCheckArgs(l, 0);

	if (!GameTimer.Init) {
		lua_pushnumber(l, 0);
		return 1;
	}

	lua_pushnumber(l, GameTimer.Cycles);
	return 1;
}

/*---------------------------------------------------------------------------
-- Actions
---------------------------------------------------------------------------*/
/**
**  Stop the running game with a given result
*/
void StopGame(GameResults result)
{
	GameResult = result;
	GamePaused = true;
	GameRunning = false;
}

/**
**  Action condition player wins.
*/
static int CclActionVictory(lua_State *l)
{
	LuaCheckArgs(l, 0);

	StopGame(GameVictory);
	return 0;
}

/**
**  Action condition player lose.
*/
static int CclActionDefeat(lua_State *l)
{
	LuaCheckArgs(l, 0);

	StopGame(GameDefeat);
	return 0;
}

/**
**  Action condition player draw.
*/
static int CclActionDraw(lua_State *l)
{
	LuaCheckArgs(l, 0);

	StopGame(GameDraw);
	return 0;
}

/**
**  Action set timer
*/
static int CclActionSetTimer(lua_State *l)
{
	LuaCheckArgs(l, 2);

	GameTimer.Cycles = LuaToNumber(l, 1);
	GameTimer.Increasing = LuaToBoolean(l, 2);
	GameTimer.Init = true;
	GameTimer.LastUpdate = GameCycle;

	return 0;
}

/**
**  Action start timer
*/
static int CclActionStartTimer(lua_State *l)
{
	LuaCheckArgs(l, 0);

	GameTimer.Running = true;
	GameTimer.Init = true;
	return 0;
}

/**
**  Action stop timer
*/
static int CclActionStopTimer(lua_State *l)
{
	LuaCheckArgs(l, 0);

	GameTimer.Running = false;
	return 0;
}

/**
**  Action wait
*/
static int CclActionWait(lua_State *l)
{
	LuaCheckArgs(l, 1);

	WaitFrame = FrameCounter +
		(FRAMES_PER_SECOND * VideoSyncSpeed / 100 * (int)LuaToNumber(l, 1) + 999) / 1000;
	return 0;
}

/**
**  Add a trigger.
*/
static int CclAddTrigger(lua_State *l)
{
	int i;

	LuaCheckArgs(l, 2);
	if (!lua_isfunction(l, 1) ||
			(!lua_isfunction(l, 2) && !lua_istable(l, 2))) {
		LuaError(l, "incorrect argument");
	}

	//
	// Make a list of all triggers.
	// A trigger is a pair of condition and action
	//
	lua_pushstring(l, "_triggers_");
	lua_gettable(l, LUA_GLOBALSINDEX);

	if (lua_isnil(l, -1)) {
		puts("Trigger not set, defining trigger");
		lua_pop(l, 1);
		lua_pushstring(l, "_triggers_");
		lua_newtable(l);
		lua_settable(l, LUA_GLOBALSINDEX);
		lua_pushstring(l, "_triggers_");
		lua_gettable(l, LUA_GLOBALSINDEX);
	}

	i = luaL_getn(l, -1);
	if (ActiveTriggers && !ActiveTriggers[i / 2]) {
		lua_pushnil(l);
		lua_rawseti(l, -2, i + 1);
		lua_pushnil(l);
		lua_rawseti(l, -2, i + 2);
	} else {
		lua_pushvalue(l, 1);
		lua_rawseti(l, -2, i + 1);
		lua_newtable(l);
		lua_pushvalue(l, 2);
		lua_rawseti(l, -2, 1);
		lua_rawseti(l, -2, i + 2);
	}
	lua_pop(l, 1);

	return 0;
}

/**
**  Set the trigger values
*/
static int CclSetTriggers(lua_State *l)
{
	LuaCheckArgs(l, 3);
	Trigger = LuaToNumber(l, 1);
	WaitTrigger = LuaToNumber(l, 2);
	WaitFrame = LuaToNumber(l, 3);

	return 0;
}

/**
**  Set the active triggers
*/
static int CclSetActiveTriggers(lua_State *l)
{
	int args;

	args = lua_gettop(l);
	ActiveTriggers = new bool[args];
	for (int j = 0; j < args; ++j) {
		ActiveTriggers[j] = LuaToBoolean(l, j + 1);
	}

	return 0;
}

/**
**  Execute a trigger action
**
**  @param script  Script to execute
**
**  @return        1 if the trigger should be removed
*/
static int TriggerExecuteAction(int script)
{
	int ret;
	int args;
	int j;
	int base = lua_gettop(Lua);

	ret = 0;

	lua_rawgeti(Lua, -1, script + 1);
	args = luaL_getn(Lua, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(Lua, -1, j + 1);
		LuaCall(0, 0);
		if (lua_gettop(Lua) > base + 1 && lua_toboolean(Lua, -1)) {
			ret = 1;
		} else {
			ret = 0;
		}
		lua_settop(Lua, base + 1);
		if (WaitFrame > FrameCounter) {
			lua_pop(Lua, 1);
			return 0;
		}
	}
	lua_pop(Lua, 1);

	// If action returns false remove it
	return !ret;
}

/**
**  Remove a trigger
**
**  @param trig  Current trigger
*/
static void TriggerRemoveTrigger(int trig)
{
	lua_pushnumber(Lua, -1);
	lua_rawseti(Lua, -2, trig + 1);
	lua_pushnumber(Lua, -1);
	lua_rawseti(Lua, -2, trig + 2);
}

/**
**  Check trigger each game cycle.
*/
void TriggersEachCycle(void)
{
	int triggers;
	int base = lua_gettop(Lua);

	lua_pushstring(Lua, "_triggers_");
	lua_gettable(Lua, LUA_GLOBALSINDEX);
	triggers = luaL_getn(Lua, -1);

	if (Trigger >= triggers) {
		Trigger = 0;
	}

	if (WaitFrame > FrameCounter) {
		lua_pop(Lua, 1);
		return;
	}
	if (WaitFrame && WaitFrame <= FrameCounter) {
		WaitFrame = 0;
		if (TriggerExecuteAction(WaitTrigger + 1)) {
			TriggerRemoveTrigger(WaitTrigger);
		}
		lua_pop(Lua, 1);
		return;
	}

	if (GamePaused) {
		lua_pop(Lua, 1);
		return;
	}

	// Skip to the next trigger
	while (Trigger < triggers) {
		lua_rawgeti(Lua, -1, Trigger + 1);
		if (!lua_isnumber(Lua, -1)) {
			break;
		}
		lua_pop(Lua, 1);
		Trigger += 2;
	}
	if (Trigger < triggers) {
		WaitTrigger = Trigger;
		Trigger += 2;
		LuaCall(0, 0);
		// If condition is true execute action
		if (lua_gettop(Lua) > base + 1 && lua_toboolean(Lua, -1)) {
			lua_settop(Lua, base + 1);
			if (TriggerExecuteAction(WaitTrigger + 1)) {
				TriggerRemoveTrigger(WaitTrigger);
			}
		}
		lua_settop(Lua, base + 1);
	}
	lua_pop(Lua, 1);
}

/**
**  Register CCL features for triggers.
*/
void TriggerCclRegister(void)
{
	lua_register(Lua, "AddTrigger", CclAddTrigger);
	lua_register(Lua, "SetTriggers", CclSetTriggers);
	lua_register(Lua, "SetActiveTriggers", CclSetActiveTriggers);
	// Conditions
	lua_register(Lua, "GetNumUnitsAt", CclGetNumUnitsAt);
	lua_register(Lua, "IfNearUnit", CclIfNearUnit);
	lua_register(Lua, "IfRescuedNearUnit", CclIfRescuedNearUnit);
	lua_register(Lua, "GetNumOpponents", CclGetNumOpponents);
	lua_register(Lua, "GetTimer", CclGetTimer);
	// Actions
	lua_register(Lua, "ActionVictory", CclActionVictory);
	lua_register(Lua, "ActionDefeat", CclActionDefeat);
	lua_register(Lua, "ActionDraw", CclActionDraw);
	lua_register(Lua, "ActionSetTimer", CclActionSetTimer);
	lua_register(Lua, "ActionStartTimer", CclActionStartTimer);
	lua_register(Lua, "ActionStopTimer", CclActionStopTimer);
	lua_register(Lua, "ActionWait", CclActionWait);

}

/**
**  Save the trigger module.
**
**  @param file  Open file to print to
*/
void SaveTriggers(CFile *file)
{
	int i;
	int triggers;

	lua_pushstring(Lua, "_triggers_");
	lua_gettable(Lua, LUA_GLOBALSINDEX);
	triggers = luaL_getn(Lua, -1);

	file->printf("SetActiveTriggers(");
	for (i = 0; i < triggers; i += 2) {
		lua_rawgeti(Lua, -1, i + 1);
		if (i) {
			file->printf(", ");
		}
		if (!lua_isnil(Lua, -1)) {
			file->printf("true");
		} else {
			file->printf("false");
		}
		lua_pop(Lua, 1);
	}
	file->printf(")\n");

	file->printf("SetTriggers(%d, %d, %d)\n", Trigger, WaitTrigger, WaitFrame);

	if (GameTimer.Init) {
		file->printf("ActionSetTimer(%ld, %s)\n",
			GameTimer.Cycles, (GameTimer.Increasing ? "true" : "false"));
		if (GameTimer.Running) {
			file->printf("ActionStartTimer()\n");
		}
	}
}

/**
**  Initialize the trigger module.
*/
void InitTriggers(void)
{
	//
	// Setup default triggers
	//
	WaitFrame = 0;

	// FIXME: choose the triggers for game type

	lua_pushstring(Lua, "_triggers_");
	lua_gettable(Lua, LUA_GLOBALSINDEX);
	if (lua_isnil(Lua, -1)) {
		lua_pushstring(Lua, "SinglePlayerTriggers");
		lua_gettable(Lua, LUA_GLOBALSINDEX);
		LuaCall(0, 1);
	}
	lua_pop(Lua, 1);
}

/**
**  Clean up the trigger module.
*/
void CleanTriggers(void)
{
	lua_pushstring(Lua, "_triggers_");
	lua_pushnil(Lua);
	lua_settable(Lua, LUA_GLOBALSINDEX);

	Trigger = 0;

	delete[] ActiveTriggers;
	ActiveTriggers = NULL;

	GameTimer.Reset();
}

//@}
