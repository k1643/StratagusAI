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
/**@name script_unit.cpp - The unit ccl functions. */
//
//      (c) Copyright 2001-2005 by Lutz Sammer and Jimmy Salmon
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
//      $Id: script_unit.cpp 7847 2006-12-22 06:37:06Z jsalmon3 $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "stratagus.h"
#include "unit.h"
#include "unittype.h"
#include "animation.h"
#include "upgrade.h"
#include "player.h"
#include "script.h"
#include "spells.h"
#include "pathfinder.h"
#include "trigger.h"
#include "actions.h"
#include "construct.h"

/*----------------------------------------------------------------------------
--  Variables
----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------
--  Functions
----------------------------------------------------------------------------*/

	/// Get resource by name
extern unsigned CclGetResourceByName(lua_State *l);

/**
**  Set xp damage
**
**  @param l  Lua state.
**
**  @return   The old state of the xp damage
*/
static int CclSetXpDamage(lua_State *l)
{
	LuaCheckArgs(l, 1);
	XpDamage = LuaToBoolean(l, 1);
	return 0;
}

/**
**  Set training queue
**
**  @param l  Lua state.
**
**  @return  The old state of the training queue
*/
static int CclSetTrainingQueue(lua_State *l)
{
	LuaCheckArgs(l, 1);
	EnableTrainingQueue = LuaToBoolean(l, 1);
	return 0;
}

/**
**  Set capture buildings
**
**  @param l  Lua state.
**
**  @return   The old state of the flag
*/
static int CclSetBuildingCapture(lua_State *l)
{
	LuaCheckArgs(l, 1);
	EnableBuildingCapture = LuaToBoolean(l, 1);
	return 0;
}

/**
**  Set reveal attacker
**
**  @param l  Lua state.
**
**  @return   The old state of the flag
*/
static int CclSetRevealAttacker(lua_State *l)
{
	LuaCheckArgs(l, 1);
	RevealAttacker = LuaToBoolean(l, 1);
	return 0;
}

/**
**  Get a unit pointer
**
**  @param l  Lua state.
**
**  @return   The unit pointer
*/
static CUnit *CclGetUnit(lua_State *l)
{
	return UnitSlots[(int)LuaToNumber(l, -1)];
}

/**
**  Parse order
**
**  @param l      Lua state.
**  @param order  OUT: resulting order.
*/
void CclParseOrder(lua_State *l, COrder *order)
{
	const char *value;
	int args;
	int j;

	//
	// Parse the list: (still everything could be changed!)
	//
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		if (!strcmp(value, "action-none")) {
			order->Action = UnitActionNone;
		} else if (!strcmp(value, "action-still")) {
			order->Action = UnitActionStill;
		} else if (!strcmp(value, "action-stand-ground")) {
			order->Action = UnitActionStandGround;
		} else if (!strcmp(value, "action-follow")) {
			order->Action = UnitActionFollow;
		} else if (!strcmp(value, "action-move")) {
			order->Action = UnitActionMove;
		} else if (!strcmp(value, "action-attack")) {
			order->Action = UnitActionAttack;
		} else if (!strcmp(value, "action-attack-ground")) {
			order->Action = UnitActionAttackGround;
		} else if (!strcmp(value, "action-die")) {
			order->Action = UnitActionDie;
		} else if (!strcmp(value, "action-spell-cast")) {
			order->Action = UnitActionSpellCast;
		} else if (!strcmp(value, "action-train")) {
			order->Action = UnitActionTrain;
		} else if (!strcmp(value, "action-upgrade-to")) {
			order->Action = UnitActionUpgradeTo;
		} else if (!strcmp(value, "action-research")) {
			order->Action = UnitActionResearch;
		} else if (!strcmp(value, "action-built")) {
			order->Action = UnitActionBuilt;
		} else if (!strcmp(value, "action-board")) {
			order->Action = UnitActionBoard;
		} else if (!strcmp(value, "action-unload")) {
			order->Action = UnitActionUnload;
		} else if (!strcmp(value, "action-patrol")) {
			order->Action = UnitActionPatrol;
		} else if (!strcmp(value, "action-build")) {
			order->Action = UnitActionBuild;
		} else if (!strcmp(value, "action-repair")) {
			order->Action = UnitActionRepair;
		} else if (!strcmp(value, "action-resource")) {
			order->Action = UnitActionResource;
		} else if (!strcmp(value, "action-return-goods")) {
			order->Action = UnitActionReturnGoods;
		} else if (!strcmp(value, "action-transform-into")) {
			order->Action = UnitActionTransformInto;
		} else if (!strcmp(value, "range")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Range = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "min-range")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->MinRange = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "width")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Width = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "height")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Height = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "goal")) {
			int slot;

			++j;
			lua_rawgeti(l, -1, j + 1);
			value = LuaToString(l, -1);
			lua_pop(l, 1);

			slot = strtol(value + 1, NULL, 16);
			order->Goal = UnitSlots[slot];
			if (!UnitSlots[slot]) {
				DebugPrint("FIXME: Forward reference not supported\n");
			}
			//++UnitSlots[slot]->Refs;

		} else if (!strcmp(value, "tile")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			if (!lua_istable(l, -1) || luaL_getn(l, -1) != 2) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, -1, 1);
			order->X = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, -1, 2);
			order->Y = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_pop(l, 1);

		} else if (!strcmp(value, "type")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Type = UnitTypeByIdent(LuaToString(l, -1));
			lua_pop(l, 1);

		} else if (!strcmp(value, "patrol")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			if (!lua_istable(l, -1) || luaL_getn(l, -1) != 2) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, -1, 1);
			order->Arg1.Patrol.X = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, -1, 2);
			order->Arg1.Patrol.Y = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_pop(l, 1);

		} else if (!strcmp(value, "spell")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Arg1.Spell = SpellTypeByIdent(LuaToString(l, -1));
			lua_pop(l, 1);

		} else if (!strcmp(value, "upgrade")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Arg1.Upgrade = CUpgrade::Get(LuaToString(l, -1));
			lua_pop(l, 1);

		} else if (!strcmp(value, "mine")) {
			++j;
			lua_rawgeti(l, -1, j + 1);
			order->Arg1.ResourcePos = LuaToNumber(l, -1);
			lua_pop(l, 1);

		} else {
		   // This leaves a half initialized unit
		   LuaError(l, "Unsupported tag: %s" _C_ value);
		}
	}
}

/**
**  Parse orders.
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should get the orders.
*/
static void CclParseOrders(lua_State *l, CUnit *unit)
{
	for (std::vector<COrder *>::iterator order = unit->Orders.begin();
			order != unit->Orders.end();
			++order) {
		delete *order;
	}
	unit->Orders.clear();
	for (int j = 0; j < unit->OrderCount; ++j) {
		lua_rawgeti(l, -1, j + 1);
		unit->Orders.push_back(new COrder);
		CclParseOrder(l, unit->Orders[j]);
		lua_pop(l, 1);
	}
}

/**
**  Parse built
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should be filled with the data.
*/
static void CclParseBuilt(lua_State *l, CUnit *unit)
{
	const char *value;
	int args;
	int j;

	if (!lua_istable(l, -1)) {
		LuaError(l, "incorrect argument");
	}
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		++j;
		if (!strcmp(value, "worker")) {
			int slot;

			lua_rawgeti(l, -1, j + 1);
			value = LuaToString(l, -1);
			lua_pop(l, 1);
			slot = strtol(value + 1, NULL, 16);
			Assert(UnitSlots[slot]);
			unit->Data.Built.Worker = UnitSlots[slot];
			//++UnitSlots[slot]->Refs;
		} else if (!strcmp(value, "progress")) {
			lua_rawgeti(l, -1, j + 1);
			unit->Data.Built.Progress = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "cancel")) {
			unit->Data.Built.Cancel = 1;
			--j;
		} else if (!strcmp(value, "frame")) {
			int frame;
			CConstructionFrame *cframe;

			lua_rawgeti(l, -1, j + 1);
			frame = LuaToNumber(l, -1);
			lua_pop(l, 1);
			cframe = unit->Type->Construction->Frames;
			while (frame--) {
				cframe = cframe->Next;
			}
			unit->Data.Built.Frame = cframe;
		}
	}
}

/**
**  Parse res worker data
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should be filled with the data.
*/
static void CclParseResWorker(lua_State *l, CUnit *unit)
{
	const char *value;
	int args;
	int j;

	if (!lua_istable(l, -1)) {
		LuaError(l, "incorrect argument");
	}
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		++j;
		if (!strcmp(value, "time-to-harvest")) {
			lua_rawgeti(l, -1, j + 1);
			unit->Data.ResWorker.TimeToHarvest = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "done-harvesting")) {
			unit->Data.ResWorker.DoneHarvesting = 1;
			--j;
		}
	}
}

/**
**  Parse research
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should be filled with the data.
*/
static void CclParseResearch(lua_State *l, CUnit *unit)
{
	const char *value;
	int args;
	int j;

	if (!lua_istable(l, -1)) {
		LuaError(l, "incorrect argument");
	}
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		++j;
		if (!strcmp(value, "ident")) {
			lua_rawgeti(l, -1, j + 1);
			value = LuaToString(l, -1);
			lua_pop(l, 1);
			unit->Data.Research.Upgrade = CUpgrade::Get(value);
		}
	}
}

/**
**  Parse upgrade to
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should be filled with the data.
*/
static void CclParseUpgradeTo(lua_State *l, CUnit *unit)
{
	const char *value;
	int args;
	int j;

	if (!lua_istable(l, -1)) {
		LuaError(l, "incorrect argument");
	}
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		++j;
		if (!strcmp(value, "ticks")) {
			lua_rawgeti(l, -1, j + 1);
			unit->Data.UpgradeTo.Ticks = LuaToNumber(l, -1);
			lua_pop(l, 1);
		}
	}
}

/**
**  Parse stored data for train order
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should be filled with the data.
*/
static void CclParseTrain(lua_State *l, CUnit *unit)
{
	const char *value;
	int args;
	int j;

	if (!lua_istable(l, -1)) {
		LuaError(l, "incorrect argument");
	}
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		++j;
		if (!strcmp(value, "ticks")) {
			lua_rawgeti(l, -1, j + 1);
			unit->Data.Train.Ticks = LuaToNumber(l, -1);
			lua_pop(l, 1);
		}
	}
}

/**
**  Parse stored data for move order
**
**  @param l     Lua state.
**  @param unit  Unit pointer which should be filled with the data.
*/
static void CclParseMove(lua_State *l, CUnit *unit)
{
	const char *value;
	int args;
	int j;

	if (!lua_istable(l, -1)) {
		LuaError(l, "incorrect argument");
	}
	args = luaL_getn(l, -1);
	for (j = 0; j < args; ++j) {
		lua_rawgeti(l, -1, j + 1);
		value = LuaToString(l, -1);
		lua_pop(l, 1);
		++j;
		if (!strcmp(value, "fast")) {
			unit->Data.Move.Fast = 1;
			--j;
		} else if (!strcmp(value, "path")) {
			int subargs;
			int k;

			lua_rawgeti(l, -1, j + 1);
			if (!lua_istable(l, -1)) {
				LuaError(l, "incorrect argument");
			}
			subargs = luaL_getn(l, -1);
			for (k = 0; k < subargs; ++k) {
				lua_rawgeti(l, -1, k + 1);
				unit->Data.Move.Path[k] = LuaToNumber(l, -1);
				lua_pop(l, 1);
			}
			unit->Data.Move.Length = subargs;
			lua_pop(l, 1);
		}
	}
}

/**
**  Parse unit
**
**  @param l  Lua state.
**
**  @todo  Verify that vision table is always correct (transporter)
**  @todo (PlaceUnit() and host-info).
*/
static int CclUnit(lua_State *l)
{
	const char *value;
	CUnit *unit;
	CUnitType *type;
	CUnitType *seentype;
	CPlayer *player;
	int slot;
	int i;
	const char *s;
	int args;
	int j;

	args = lua_gettop(l);
	j = 0;

	slot = LuaToNumber(l, j + 1);
	++j;

	unit = NULL;
	type = NULL;
	seentype = NULL;
	player = NULL;
	i = 0;

	//
	// Parse the list: (still everything could be changed!)
	//
	for (; j < args; ++j) {
		value = LuaToString(l, j + 1);
		++j;

		if (!strcmp(value, "type")) {
			type = UnitTypeByIdent(LuaToString(l, j + 1));
		} else if (!strcmp(value, "seen-type")) {
			seentype = UnitTypeByIdent(LuaToString(l, j + 1));
		} else if (!strcmp(value, "player")) {
			player = &Players[(int)LuaToNumber(l, j + 1)];

			// During a unit's death animation (when action is "die" but the
			// unit still has its original type, i.e. it's still not a corpse)
			// the unit is already removed from map and from player's
			// unit list (=the unit went through LetUnitDie() which
			// calls RemoveUnit() and UnitLost()).  Such a unit should not
			// be put on player's unit list!  However, this state is not
			// easily detected from this place.  It seems that it is
			// characterized by
			// unit->Orders[0]->Action==UnitActionDie so we have to wait
			// until we parsed at least Unit::Orders[].
			Assert(type);
			unit = UnitSlots[slot];
			unit->Init(type);
			unit->Seen.Type = seentype;
			unit->Active = 0;
			unit->Removed = 0;
			Assert(unit->Slot == slot);
		} else if (!strcmp(value, "next")) {
			unit->Next = UnitSlots[(int)LuaToNumber(l, j + 1)];
		} else if (!strcmp(value, "current-sight-range")) {
			unit->CurrentSightRange = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "refs")) {
			unit->Refs = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "host-info")) {
			int x;
			int y;
			int w;
			int h;

			if (!lua_istable(l, j + 1) || luaL_getn(l, j + 1) != 4) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, j + 1, 1);
			x = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, j + 1, 2);
			y = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, j + 1, 3);
			w = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, j + 1, 4);
			h = LuaToNumber(l, -1);
			lua_pop(l, 1);
			MapSight(player, x, y, w, h, unit->CurrentSightRange, MapMarkTileSight);
			// Detectcloak works in container
			if (unit->Type->DetectCloak) {
				MapSight(player, x, y, w, h, unit->CurrentSightRange, MapMarkTileDetectCloak);
			}
			// Radar(Jammer) not.

		} else if (!strcmp(value, "tile")) {
			if (!lua_istable(l, j + 1) || luaL_getn(l, j + 1) != 2) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, j + 1, 1);
			unit->X = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, j + 1, 2);
			unit->Y = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "stats")) {
			unit->Stats = &type->Stats[(int)LuaToNumber(l, j + 1)];
		} else if (!strcmp(value, "pixel")) {
			if (!lua_istable(l, j + 1) || luaL_getn(l, j + 1) != 2) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, j + 1, 1);
			unit->IX = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, j + 1, 2);
			unit->IY = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "seen-pixel")) {
			if (!lua_istable(l, j + 1) || luaL_getn(l, j + 1) != 2) {
				LuaError(l, "incorrect argument");
			}
			lua_rawgeti(l, j + 1, 1);
			unit->Seen.IX = LuaToNumber(l, -1);
			lua_pop(l, 1);
			lua_rawgeti(l, j + 1, 2);
			unit->Seen.IY = LuaToNumber(l, -1);
			lua_pop(l, 1);
		} else if (!strcmp(value, "frame")) {
			unit->Frame = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "seen")) {
			unit->Seen.Frame = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "not-seen")) {
			unit->Seen.Frame = UnitNotSeen;
			--j;
		} else if (!strcmp(value, "direction")) {
			unit->Direction = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "attacked")) {
			// FIXME : unsigned long should be better handled
			unit->Attacked = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "auto-repair")) {
			unit->AutoRepair = 1;
			--j;
		} else if (!strcmp(value, "burning")) {
			unit->Burning = 1;
			--j;
		} else if (!strcmp(value, "destroyed")) {
			unit->Destroyed = 1;
			--j;
		} else if (!strcmp(value, "removed")) {
			unit->Removed = 1;
			--j;
		} else if (!strcmp(value, "selected")) {
			unit->Selected = 1;
			--j;
		} else if (!strcmp(value, "rescued-from")) {
			unit->RescuedFrom = &Players[(int)LuaToNumber(l, j + 1)];
		} else if (!strcmp(value, "seen-by-player")) {
			s = LuaToString(l, j + 1);
			unit->Seen.ByPlayer = 0;
			for (i = 0; i < PlayerMax && *s; ++i, ++s) {
				if (*s == '-' || *s == '_' || *s == ' ') {
					unit->Seen.ByPlayer &= ~(1 << i);
				} else {
					unit->Seen.ByPlayer |= (1 << i);
				}
			}
		} else if (!strcmp(value, "seen-destroyed")) {
			s = LuaToString(l, j + 1);
			unit->Seen.Destroyed = 0;
			for (i = 0; i < PlayerMax && *s; ++i, ++s) {
				if (*s == '-' || *s == '_' || *s == ' ') {
					unit->Seen.Destroyed &= ~(1 << i);
				} else {
					unit->Seen.Destroyed |= (1 << i);
				}
			}
		} else if (!strcmp(value, "constructed")) {
			unit->Constructed = 1;
			--j;
		} else if (!strcmp(value, "seen-constructed")) {
			unit->Seen.Constructed = 1;
			--j;
		} else if (!strcmp(value, "seen-state")) {
			unit->Seen.State = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "active")) {
			unit->Active = 1;
			--j;
		} else if (!strcmp(value, "resource-active")) {
			unit->Data.Resource.Active = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "ttl")) {
			// FIXME : unsigned long should be better handled
			unit->TTL = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "group-id")) {
			unit->GroupId = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "last-group")) {
			unit->LastGroup = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "resources-held")) {
			unit->ResourcesHeld = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "current-resource")) {
			lua_pushvalue(l, j + 1);
			unit->CurrentResource = CclGetResourceByName(l);
			lua_pop(l, 1);
		} else if (!strcmp(value, "sub-action")) {
			unit->SubAction = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "wait")) {
			unit->Wait = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "state")) {
			unit->State = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "anim-wait")) {
			unit->Anim.Wait = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "curr-anim")) {
			unit->Anim.CurrAnim = AnimationsArray[(int)LuaToNumber(l, j + 1)];
		} else if (!strcmp(value, "anim")) {
			unit->Anim.Anim = unit->Anim.CurrAnim + (int)LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "unbreakable")) {
			unit->Anim.Unbreakable = 1;
			--j;
		} else if (!strcmp(value, "blink")) {
			unit->Blink = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "moving")) {
			unit->Moving = 1;
			--j;
		} else if (!strcmp(value, "re-cast")) {
			unit->ReCast = 1;
			--j;
		} else if (!strcmp(value, "boarded")) {
			unit->Boarded = 1;
			--j;
		} else if (!strcmp(value, "units-boarded-count")) {
			unit->BoardCount = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "units-contained")) {
			int subargs;
			int k;

			if (!lua_istable(l, j + 1)) {
				LuaError(l, "incorrect argument");
			}
			subargs = luaL_getn(l, j + 1);
			for (k = 0; k < subargs; ++k) {
				lua_rawgeti(l, j + 1, k + 1);
				value = LuaToString(l, -1);
				lua_pop(l, 1);
				slot = strtol(value + 1, NULL, 16);
				UnitSlots[slot]->AddInContainer(unit);
				Assert(UnitSlots[slot]);
				//++UnitSlots[slot]->Refs;
			}
		} else if (!strcmp(value, "order-count")) {
			unit->OrderCount = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "order-flush")) {
			unit->OrderFlush = LuaToNumber(l, j + 1);
		} else if (!strcmp(value, "orders")) {
			lua_pushvalue(l, j + 1);
			CclParseOrders(l, unit);
			lua_pop(l, 1);
			// now we know unit's action so we can assign it to a player
			unit->AssignToPlayer (player);
			if (unit->Orders[0]->Action == UnitActionBuilt) {
				DebugPrint("HACK: the building is not ready yet\n");
				// HACK: the building is not ready yet
				unit->Player->UnitTypesCount[type->Slot]--;
			}
		} else if (!strcmp(value, "critical-order")) {
			lua_pushvalue(l, j + 1);
			CclParseOrder(l, &unit->CriticalOrder);
			lua_pop(l, 1);
		} else if (!strcmp(value, "saved-order")) {
			lua_pushvalue(l, j + 1);
			CclParseOrder(l, &unit->SavedOrder);
			lua_pop(l, 1);
		} else if (!strcmp(value, "new-order")) {
			lua_pushvalue(l, j + 1);
			CclParseOrder(l, &unit->NewOrder);
			lua_pop(l, 1);
		} else if (!strcmp(value, "data-built")) {
			lua_pushvalue(l, j + 1);
			CclParseBuilt(l, unit);
			lua_pop(l, 1);
		} else if (!strcmp(value, "data-res-worker")) {
			lua_pushvalue(l, j + 1);
			CclParseResWorker(l, unit);
			lua_pop(l, 1);
		} else if (!strcmp(value, "data-research")) {
			lua_pushvalue(l, j + 1);
			CclParseResearch(l, unit);
			lua_pop(l, 1);
		} else if (!strcmp(value, "data-upgrade-to")) {
			lua_pushvalue(l, j + 1);
			CclParseUpgradeTo(l, unit);
			lua_pop(l, 1);
		} else if (!strcmp(value, "data-train")) {
			lua_pushvalue(l, j + 1);
			CclParseTrain(l, unit);
			lua_pop(l, 1);
		} else if (!strcmp(value, "data-move")) {
			lua_pushvalue(l, j + 1);
			CclParseMove(l, unit);
			lua_pop(l, 1);
		} else if (!strcmp(value, "goal")) {
			unit->Goal = UnitSlots[(int)LuaToNumber(l, j + 1)];
		} else if (!strcmp(value, "auto-cast")) {
			s = LuaToString(l, j + 1);
			Assert(SpellTypeByIdent(s));
			if (!unit->AutoCastSpell) {
				unit->AutoCastSpell = new char[SpellTypeTable.size()];
				memset(unit->AutoCastSpell, 0, SpellTypeTable.size());
			}
			unit->AutoCastSpell[SpellTypeByIdent(s)->Slot] = 1;
		} else {
			i = GetVariableIndex(value); // User variables
			if (i != -1) { // Valid index
				DefineVariableField(l, unit->Variable + i, j + 1);
				continue;
			}
		   LuaError(l, "Unsupported tag: %s" _C_ value);
		}
	}

	// Unit may not have been assigned to a player before now. If not,
	// do so now. It is only assigned earlier if we have orders.
	// for loading of units from a MAP, and not a savegame, we won't
	// have orders for those units.  They should appear here as if
	// they were just created.
	if (!unit->Player) {
		unit->AssignToPlayer(player);
		UpdateForNewUnit(unit, 0);
	}

	//  Revealers are units that can see while removed
	if (unit->Removed && unit->Type->Revealer) {
		MapMarkUnitSight(unit);
	}

	// Fix Colors for rescued units.
	if (unit->RescuedFrom) {
		unit->Colors = &unit->RescuedFrom->UnitColors;
	}

	return 0;
}

/**
**  Move a unit on map.
**
**  @param l  Lua state.
**
**  @return   Returns the slot number of the made placed.
*/
static int CclMoveUnit(lua_State *l)
{
	CUnit *unit;
	int heading;
	int ix;
	int iy;

	LuaCheckArgs(l, 2);

	lua_pushvalue(l, 1);
	unit = CclGetUnit(l);
	lua_pop(l, 1);

	lua_rawgeti(l, 2, 1);
	ix = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 2, 2);
	iy = LuaToNumber(l, -1);
	lua_pop(l, 1);

	heading = SyncRand() % 256;
	if (UnitCanBeAt(unit, ix, iy)) {
		unit->Place(ix, iy);
	} else {
		unit->X = ix;
		unit->Y = iy;
		DropOutOnSide(unit, heading, 1, 1);
	}

//	PlaceUnit(unit, ix, iy);
	lua_pushvalue(l, 1);
	return 1;
}

/**
**  Create a unit and place it on the map
**
**  @param l  Lua state.
**
**  @return   Returns the slot number of the made unit.
*/
static int CclCreateUnit(lua_State *l)
{
	CUnitType *unittype;
	CUnit *unit;
	int heading;
	int playerno;
	int ix;
	int iy;

	LuaCheckArgs(l, 3);

	lua_pushvalue(l, 1);
	unittype = CclGetUnitType(l);
	lua_pop(l, 1);
	if (!lua_istable(l, 3) || luaL_getn(l, 3) != 2) {
		LuaError(l, "incorrect argument !!");
	}
	lua_rawgeti(l, 3, 1);
	ix = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 3, 2);
	iy = LuaToNumber(l, -1);
	lua_pop(l, 1);

	heading = SyncRand() % 256;
	lua_pushvalue(l, 2);
	playerno = TriggerGetPlayer(l);
	lua_pop(l, 1);
	if (playerno == -1) {
		printf("CreateUnit: You cannot use 'any in create-unit, specify a player\n");
		LuaError(l, "bad player");
		return 0;
	}
	if (Players[playerno].Type == PlayerNobody) {
		printf("CreateUnit: player %d does not exist\n", playerno);
		LuaError(l, "bad player");
		return 0;
	}

	unit = MakeUnit(unittype, &Players[playerno]);
	if (unit == NoUnitP) {
		DebugPrint("Unable to allocate unit");
		return 0;
	} else {
		if (UnitCanBeAt(unit, ix, iy)) {
			unit->Place(ix, iy);
		} else {
			unit->X = ix;
			unit->Y = iy;
			DropOutOnSide(unit, heading, 1, 1);
		}
		UpdateForNewUnit(unit, 0);

		lua_pushnumber(l, unit->Slot);
		return 1;
	}
}

/**
**  Set resources held by a unit
**
**  @param l  Lua state.
*/
static int CclSetResourcesHeld(lua_State *l)
{
	CUnit *unit;
	int value;

	LuaCheckArgs(l, 2);

	if (lua_isnil(l, 1)) {
		return 0;
	}

	lua_pushvalue(l, 1);
	unit = CclGetUnit(l);
	lua_pop(l, 1);
	value = LuaToNumber(l, 2);
	unit->ResourcesHeld = value;

	return 0;
}

/**
**  Order a unit
**
**  @param l  Lua state.
**
**  OrderUnit(player, unit-type, sloc, dloc, order)
*/
static int CclOrderUnit(lua_State *l)
{
	int plynr;
	int x1;
	int y1;
	int x2;
	int y2;
	int dx1;
	int dy1;
	int dx2;
	int dy2;
	const CUnitType *unittype;
	CUnit *table[UnitMax];
	CUnit *unit;
	int an;
	int j;
	const char *order;

	LuaCheckArgs(l, 5);

	lua_pushvalue(l, 1);
	plynr = TriggerGetPlayer(l);
	lua_pop(l, 1);
	lua_pushvalue(l, 2);
	unittype = TriggerGetUnitType(l);
	lua_pop(l, 1);
	if (!lua_istable(l, 3)) {
		LuaError(l, "incorrect argument");
	}
	lua_rawgeti(l, 3, 1);
	x1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 3, 2);
	y1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	if (luaL_getn(l, 3) == 4) {
		lua_rawgeti(l, 3, 3);
		x2 = LuaToNumber(l, -1);
		lua_pop(l, 1);
		lua_rawgeti(l, 3, 4);
		y2 = LuaToNumber(l, -1);
		lua_pop(l, 1);
	} else {
		x2 = x1;
		y2 = y1;
	}
	if (!lua_istable(l, 4)) {
		LuaError(l, "incorrect argument");
	}
	lua_rawgeti(l, 4, 1);
	dx1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 4, 2);
	dy1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	if (luaL_getn(l, 4) == 4) {
		lua_rawgeti(l, 4, 3);
		dx2 = LuaToNumber(l, -1);
		lua_pop(l, 1);
		lua_rawgeti(l, 4, 4);
		dy2 = LuaToNumber(l, -1);
		lua_pop(l, 1);
	} else {
		dx2 = dx1;
		dy2 = dy1;
	}
	order = LuaToString(l, 5);

	an = UnitCacheSelect(x1, y1, x2 + 1, y2 + 1, table);
	for (j = 0; j < an; ++j) {
		unit = table[j];
		if (unittype == ANY_UNIT ||
				(unittype == ALL_FOODUNITS && !unit->Type->Building) ||
				(unittype == ALL_BUILDINGS && unit->Type->Building) ||
				unittype == unit->Type) {
			if (plynr == -1 || plynr == unit->Player->Index) {
				if (!strcmp(order,"move")) {
					CommandMove(unit, (dx1 + dx2) / 2, (dy1 + dy2) / 2, 1);
				} else if (!strcmp(order, "attack")) {
					CUnit *attack;

					attack = TargetOnMap(unit, dx1, dy1, dx2 + 1, dy2 + 1);
					CommandAttack(unit, (dx1 + dx2) / 2, (dy1 + dy2) / 2, attack, 1);
				} else if (!strcmp(order, "patrol")) {
					CommandPatrolUnit(unit, (dx1 + dx2) / 2, (dy1 + dy2) / 2, 1);
				} else {
					LuaError(l, "Unsupported order: %s" _C_ order);
				}
			}
		}
	}

	return 0;
}

/**
**  Kill a unit
**
**  @param l  Lua state.
**
**  @return   Returns true if a unit was killed.
*/
static int CclKillUnit(lua_State *l)
{
	int j;
	int plynr;
	const CUnitType *unittype;
	CUnit *unit;
	CUnit **table;

	LuaCheckArgs(l, 2);

	lua_pushvalue(l, 1);
	unittype = TriggerGetUnitType(l);
	lua_pop(l, 1);
	plynr = TriggerGetPlayer(l);
	if (plynr == -1) {
		table = Units;
		j = NumUnits - 1;
	} else {
		table = Players[plynr].Units;
		j = Players[plynr].TotalNumUnits - 1;
	}

	for (; j >= 0; --j) {
		unit = table[j];
		if (unittype == ANY_UNIT ||
				(unittype == ALL_FOODUNITS && !unit->Type->Building) ||
				(unittype == ALL_BUILDINGS && unit->Type->Building) ||
				unittype == unit->Type) {
			LetUnitDie(unit);
			lua_pushboolean(l, 1);
			return 1;
		}
	}

	lua_pushboolean(l, 0);
	return 1;
}

/**
**  Kill a unit at a location
**
**  @param l  Lua state.
**
**  @return   Returns the number of units killed.
*/
static int CclKillUnitAt(lua_State *l)
{
	int plynr;
	int q;
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

	LuaCheckArgs(l, 2);

	lua_pushvalue(l, 2);
	plynr = TriggerGetPlayer(l);
	lua_pop(l, 1);
	q = LuaToNumber(l, 3);
	lua_pushvalue(l, 1);
	unittype = TriggerGetUnitType(l);
	lua_pop(l, 1);
	if (!lua_istable(l, 4)) {
		LuaError(l, "incorrect argument");
	}
	lua_rawgeti(l, 4, 1);
	x1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 4, 2);
	y1 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 4, 3);
	x2 = LuaToNumber(l, -1);
	lua_pop(l, 1);
	lua_rawgeti(l, 4, 4);
	y2 = LuaToNumber(l, -1);
	lua_pop(l, 1);

	an = UnitCacheSelect(x1, y1, x2 + 1, y2 + 1, table);
	for (j = s = 0; j < an && s < q; ++j) {
		unit = table[j];
		if (unittype == ANY_UNIT ||
				(unittype == ALL_FOODUNITS && !unit->Type->Building) ||
				(unittype == ALL_BUILDINGS && unit->Type->Building) ||
				unittype==unit->Type) {
			if (plynr == -1 || plynr == unit->Player->Index) {
				LetUnitDie(unit);
				++s;
			}
		}
	}

	lua_pushnumber(l, s);
	return 1;
}

/**
**  Get a player's units
**
**  @param l  Lua state.
**
**  @return   Array of units.
*/
static int CclGetUnits(lua_State *l)
{
	int plynr;
	int i;

	LuaCheckArgs(l, 1);

	plynr = TriggerGetPlayer(l);

	lua_newtable(l);
	if (plynr == -1) {
		for (i = 0; i < NumUnits; ++i) {
			lua_pushnumber(l, Units[i]->Slot);
			lua_rawseti(l, -2, i + 1);
		}
	} else {
		for (i = 0; i < Players[plynr].TotalNumUnits; ++i) {
			lua_pushnumber(l, Players[plynr].Units[i]->Slot);
			lua_rawseti(l, -2, i + 1);
		}
	}
	return 1;
}

/**
**  Get the value of the unit variable.
**
**  @param l  Lua state.
**
**  @return   The value of the variable of the unit.
*/
static int CclGetUnitVariable(lua_State *l)
{
	const CUnit *unit;
	int index;

	LuaCheckArgs(l, 2);

	lua_pushvalue(l, 1);
	unit = CclGetUnit(l);
	lua_pop(l, 1);

	index = GetVariableIndex(LuaToString(l, 2));
	if (index == -1) {
		LuaError(l, "Bad variable name '%s'\n" _C_ LuaToString(l, 2));
	}
	lua_pushnumber(l, unit->Variable[index].Value);
	return 1;
}

/**
**  Set the value of the unit variable.
**
**  @param l  Lua state.
**
**  @return The new value of the unit.
*/
static int CclSetUnitVariable(lua_State *l)
{
	CUnit *unit;
	int index;
	int value;

	LuaCheckArgs(l, 3);

	lua_pushvalue(l, 1);
	unit = CclGetUnit(l);
	lua_pop(l, 1);
	index = GetVariableIndex(LuaToString(l, 2));
	if (index == -1) {
		LuaError(l, "Bad variable name '%s'\n" _C_ LuaToString(l, 2));
	}
	value = LuaToNumber(l, 3);
	if (value > unit->Variable[index].Max) {
		unit->Variable[index].Value = unit->Variable[index].Max;
	} else {
		unit->Variable[index].Value = value;
	}
	lua_pushnumber(l, value);
	return 1;
}

/**
**  Get the usage of unit slots during load to allocate memory
**
**  @param l  Lua state.
*/
static int CclSlotUsage(lua_State *l)
{
	unsigned int args;
	unsigned int i;
	const char *key;
	int unit_index;

	args = lua_gettop(l);
	if (args == 0) {
		UnitSlotFree = 0;
		return 0;
	}
	UnitSlotFree = LuaToNumber(l, 1);
	for (i = 0; i < UnitSlotFree; i++) {
		UnitSlots[i] = new CUnit;
		UnitSlots[i]->Slot = i;
	}
	for (i = 2; i <= args; i++) {
		unit_index = -1;
		for (lua_pushnil(l); lua_next(l, i); lua_pop(l, 1)) {
			key = LuaToString(l, -2);
			if (!strcmp(key, "Slot")) {
				unit_index = LuaToNumber(l, -1);
			} else if (!strcmp(key, "FreeCycle")) {
				if (unit_index != -1) {
					UnitSlots[unit_index]->Refs = LuaToNumber(l, -1);
				}
			} else {
				LuaError(l, "Wrong key %s" _C_ key);
			}
		}
		if (ReleasedHead) {
			ReleasedTail->Next = UnitSlots[unit_index];
			ReleasedTail = UnitSlots[unit_index];
		} else {
			ReleasedHead = ReleasedTail = UnitSlots[unit_index];
		}
	}
	return 0;
}

/**
**  Register CCL features for unit.
*/
void UnitCclRegister(void)
{
	lua_register(Lua, "SetXPDamage", CclSetXpDamage);
	lua_register(Lua, "SetTrainingQueue", CclSetTrainingQueue);
	lua_register(Lua, "SetBuildingCapture", CclSetBuildingCapture);
	lua_register(Lua, "SetRevealAttacker", CclSetRevealAttacker);

	lua_register(Lua, "Unit", CclUnit);

	lua_register(Lua, "MoveUnit", CclMoveUnit);
	lua_register(Lua, "CreateUnit", CclCreateUnit);
	lua_register(Lua, "SetResourcesHeld", CclSetResourcesHeld);
	lua_register(Lua, "OrderUnit", CclOrderUnit);
	lua_register(Lua, "KillUnit", CclKillUnit);
	lua_register(Lua, "KillUnitAt", CclKillUnitAt);

	lua_register(Lua, "GetUnits", CclGetUnits);

	// unit member access functions
	lua_register(Lua, "GetUnitVariable", CclGetUnitVariable);
	lua_register(Lua, "SetUnitVariable", CclSetUnitVariable);

	lua_register(Lua, "SlotUsage", CclSlotUsage);
}

//@}
