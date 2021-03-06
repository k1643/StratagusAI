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
/**@name command.cpp - Give units a command. */
//
//      (c) Copyright 1998-2005 by Lutz Sammer and Jimmy Salmon
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
//      $Id: command.cpp 7882 2007-01-13 22:41:52Z nehalmistry $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "stratagus.h"
#include "unittype.h"
#include "player.h"
#include "unit.h"
#include "actions.h"
#include "tileset.h"
#include "map.h"
#include "upgrade.h"
#include "pathfinder.h"
#include "spells.h"
#include "interface.h"
#include "ui.h"
#include "socket-new.h"

// stratagusai ===============================================================
extern SocketInterface *socketInterface;

/*----------------------------------------------------------------------------
--  Functions
----------------------------------------------------------------------------*/

/**
**  Release an order.
**
**  @param order  Pointer to order.
*/
static void ReleaseOrder(COrder *order)
{
	if (order->Goal) {
		order->Goal->RefsDecrease();
		order->Goal = NoUnitP;
	}
}

/**
**  Release all orders of an unit.
**
**  @param unit  Pointer to unit.
*/
static void ReleaseOrders(CUnit *unit)
{
	int n;

	if ((n = unit->OrderCount) > 1) {
		while (--n) {
			ReleaseOrder(unit->Orders[n]);
			delete unit->Orders[n];
			unit->Orders.pop_back();
		}
		unit->OrderCount = 1;
	}
	unit->OrderFlush = 1;
	// Order 0 must be stopped in the action loop.
}

/**
**  Get next free order slot.
**
**  @param unit   pointer to unit.
**  @param flush  if true, flush order queue.
**
**  @return       Pointer to next free order slot.
*/
static COrder *GetNextOrder(CUnit *unit, int flush)
{
	if (flush) {
		// empty command queue
		ReleaseOrders(unit);
	}
	if (unit->OrderCount == 0x7F) {
		return NULL;
	}

	unit->Orders.push_back(new COrder);

	return unit->Orders[(int)unit->OrderCount++];
}

/**
**  Remove an order from the list of orders pending
**
**  @param unit   pointer to unit
**  @param order  number of the order to remove
*/
static void RemoveOrder(CUnit *unit, int order)
{
	int i;
	
	Assert(0 <= order && order < unit->OrderCount);
	if (order != 0) {
		delete unit->Orders[order];
	}
	i = order;
	while (i < unit->OrderCount - 1) {
		unit->Orders[i] = unit->Orders[i + 1];
		++i;
	}

	if (unit->OrderCount > 1) {
		unit->Orders.pop_back();
		--unit->OrderCount;
	} else {
		Assert(i == 0);
		unit->Orders[0]->Init();
		unit->Orders[0]->Action = UnitActionStill;
		unit->SubAction = 0;
	}
}

/**
**  Clear the saved action.
**
**  @param unit  Unit pointer, that get the saved action cleared.
**
**  @note        If we make a new order, we must clear any saved actions.
**  @note        Internal functions, must protect it, if needed.
*/
static void ClearSavedAction(CUnit *unit)
{
	ReleaseOrder(&unit->SavedOrder);

	unit->SavedOrder.Init();
	unit->SavedOrder.Action = UnitActionStill; // clear saved action
}

/*----------------------------------------------------------------------------
--  Commands
----------------------------------------------------------------------------*/

/**
**  Stop unit.
**
**  @param unit  pointer to unit.
*/
void CommandStopUnit(CUnit *unit)
{
	COrder *order;

	// Ignore that the unit could be removed.

	order = GetNextOrder(unit, FlushCommands); // Flush them.
	Assert(order);
	order->Init();

	order->Action = UnitActionStill;
	ReleaseOrder(&unit->SavedOrder);
	ReleaseOrder(&unit->NewOrder);
	unit->SavedOrder = unit->NewOrder = *order;
}

/**
**  Order an already formed Order structure
**
**  @param unit      pointer to unit
**  @param cpyorder  pointer to valid order
**  @param flush     if true, flush command queue.
*/
void CommandAnyOrder(CUnit *unit, COrder *cpyorder, int flush)
{
	COrder *order;

	if (!(order = GetNextOrder(unit, flush))) {
		return;
	}

	*order = *cpyorder;
	if (order->Goal) {
		order->Goal->RefsIncrease();
	}
	ClearSavedAction(unit);
}

/**
**  Move an order in the order queue.
**  ( Cannot move the order 0 ! )
**
**  @param unit  pointer to unit
**  @param src   the order to move
**  @param dst   the new position of the order
*/
void CommandMoveOrder(CUnit *unit, int src, int dst)
{
	COrder *tmp;
	int i;

	Assert(src != 0 && dst != 0 && src < unit->OrderCount && dst < unit->OrderCount);

	if (src == dst) {
		return;
	}

	if (src < dst) {
		tmp = unit->Orders[src];
		for (i = src; i < dst; ++i) {
			unit->Orders[i] = unit->Orders[i+1];
		}
		unit->Orders[dst] = tmp;
	} else {
		// dst < src
		tmp = unit->Orders[src];
		for (i = src - 1 ; i >= dst; --i) {
			unit->Orders[i + 1] = unit->Orders[i];
		}
		unit->Orders[dst] = tmp;
	}
}

/**
**  Stand ground.
**
**  @param unit   pointer to unit.
**  @param flush  if true, flush command queue.
*/
void CommandStandGround(CUnit *unit, int flush)
{
	COrder *order;

	// Ignore that the unit could be removed.

	if (unit->Type->Building) {
		// FIXME: should find a better way for pending orders.
		order = &unit->NewOrder;
		ReleaseOrder(order);
	} else if (!(order = GetNextOrder(unit, flush))) {
		return;
	}
	order->Init();
	order->Action = UnitActionStandGround;
	ClearSavedAction(unit);
}

/**
**  Follow unit to new position
**
**  @param unit   pointer to unit.
**  @param dest   unit to be followed
**  @param flush  if true, flush command queue.
*/
void CommandFollow(CUnit *unit, CUnit *dest, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (!CanMove(unit)) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionFollow;
		//
		// Destination could be killed.
		// Should be handled in action, but is not possible!
		// Unit::Refs is used as timeout counter.
		//
		if (dest->Destroyed) {
			order->X = dest->X + dest->Type->TileWidth / 2;
			order->Y = dest->Y + dest->Type->TileHeight / 2;
		} else {
			order->Goal = dest;
			dest->RefsIncrease();
			order->Range = 1;
		}
	}
	ClearSavedAction(unit);
}

/**
**  Move unit to new position
**
**  @param unit   pointer to unit.
**  @param x      X map position to move to.
**  @param y      Y map position to move to.
**  @param flush  if true, flush command queue.
*/
void CommandMove(CUnit *unit, int x, int y, int flush)
{
	COrder *order;

	Assert(x >= 0 && y >= 0 && x < Map.Info.MapWidth && y < Map.Info.MapHeight);

	//
	//  Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (!CanMove(unit)) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionMove;
		order->X = x;
		order->Y = y;
	}
	ClearSavedAction(unit);
}

/**
**  Repair unit
**
**  @param unit   pointer to unit.
**  @param x      X map position to repair.
**  @param y      Y map position to repair.
**  @param dest   or unit to be repaired. FIXME: not supported
**  @param flush  if true, flush command queue.
*/
void CommandRepair(CUnit *unit, int x, int y, CUnit *dest, int flush)
{
	COrder *order;

	//
	//  Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionRepair;
		//
		//  Destination could be killed.
		//  Should be handled in action, but is not possible!
		//  Unit::Refs is used as timeout counter.
		//
		if (dest) {
			if (dest->Destroyed) {
				order->X = dest->X + dest->Type->TileWidth / 2;
				order->Y = dest->Y + dest->Type->TileHeight / 2;
			} else {
				order->Goal = dest;
				dest->RefsIncrease();
				order->Range = unit->Type->RepairRange;
			}
		} else {
			order->X = x;
			order->Y = y;
		}
	}
	ClearSavedAction(unit);
}

/**
**  Auto repair.
**
**  @param unit     pointer to unit.
**  @param on       1 for auto repair on, 0 for off.
*/
void CommandAutoRepair(CUnit *unit, int on)
{
	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		unit->AutoRepair = on;
	}
}

/**
**  Attack with unit at new position
**
**  @param unit    pointer to unit.
**  @param x       X map position to attack.
**  @param y       Y map position to attack.
**  @param attack  or unit to be attacked.
**  @param flush   if true, flush command queue.
*/
void CommandAttack(CUnit *unit, int x, int y, CUnit *attack, int flush)
{
	COrder *order;

	Assert(x >= 0 && y >= 0 && x < Map.Info.MapWidth && y < Map.Info.MapHeight);

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (!unit->Type->CanAttack) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionAttack;
		if (attack) {
			//
			// Destination could be killed.
			// Should be handled in action, but is not possible!
			// Unit::Refs is used as timeout counter.
			//
			if (attack->Destroyed) {
				order->X = attack->X + attack->Type->TileWidth / 2;
				order->Y = attack->Y + attack->Type->TileHeight / 2;
			} else {
				// Removed, Dying handled by action routine.
				order->Goal = attack;
				attack->RefsIncrease();
				order->Range = unit->Stats->Variables[ATTACKRANGE_INDEX].Max;
				order->MinRange = unit->Type->MinAttackRange;
			}
		} else if (Map.WallOnMap(x,y)) {
			// FIXME: look into action_attack.c about this ugly problem
			order->X = x;
			order->Y = y;
			order->Range = unit->Stats->Variables[ATTACKRANGE_INDEX].Max;
			order->MinRange = unit->Type->MinAttackRange;
		} else {
			order->X = x;
			order->Y = y;
		}
	}
	ClearSavedAction(unit);
}

/**
**  Attack ground with unit.
**
**  @param unit   pointer to unit.
**  @param x      X map position to fire on.
**  @param y      Y map position to fire on.
**  @param flush  if true, flush command queue.
*/
void CommandAttackGround(CUnit *unit, int x, int y, int flush)
{
	COrder *order;

	Assert(x >= 0 && y >= 0 && x < Map.Info.MapWidth && y < Map.Info.MapHeight);

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionAttackGround;
		order->X = x;
		order->Y = y;
		order->Range = unit->Stats->Variables[ATTACKRANGE_INDEX].Max;
		order->MinRange = unit->Type->MinAttackRange;

		DebugPrint("FIXME this next\n");
	}
	ClearSavedAction(unit);
}

/**
**  Let an unit patrol from current to new position
**
**  FIXME: want to support patroling between units.
**
**  @param unit   pointer to unit.
**  @param x      X map position to patrol between.
**  @param y      Y map position to patrol between.
**  @param flush  if true, flush command queue.
*/
void CommandPatrolUnit(CUnit *unit, int x, int y, int flush)
{
	COrder *order;

	Assert(x >= 0 && y >= 0 && x < Map.Info.MapWidth && y < Map.Info.MapHeight);

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (!CanMove(unit)) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionPatrol;
		order->X = x;
		order->Y = y;
		Assert(!(unit->X & ~0xFFFF) && !(unit->Y & ~0xFFFF));
		order->Arg1.Patrol.X = unit->X;
		order->Arg1.Patrol.Y = unit->Y;
	}
	ClearSavedAction(unit);
}

/**
**  Board a transporter with unit.
**
**  @param unit   pointer to unit.
**  @param dest   unit to be boarded.
**  @param flush  if true, flush command queue.
*/
void CommandBoard(CUnit *unit, CUnit *dest, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		//
		// Destination could be killed.
		// Should be handled in action, but is not possible!
		// Unit::Refs is used as timeout counter.
		//
		if (dest->Destroyed) {
			return;
		}

		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionBoard;
		order->Goal = dest;
		dest->RefsIncrease();
		order->Range = 1;
	}
	ClearSavedAction(unit);
}

/**
**  Unload a transporter.
**
**  @param unit   pointer to unit.
**  @param x      X map position to unload.
**  @param y      Y map position to unload.
**  @param what   unit to be unloaded, NoUnitP all.
**  @param flush  if true, flush command queue.
*/
void CommandUnload(CUnit *unit, int x, int y, CUnit *what, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionUnload;
		order->X = x;
		order->Y = y;
		//
		// Destination could be killed.
		// Should be handled in action, but is not possible!
		// Unit::Refs is used as timeout counter.
		//
		if (what && !what->Destroyed) {
			order->Goal = what;
			what->RefsIncrease();
		}
	}
	ClearSavedAction(unit);
}

/**
**  Send a unit building
**
**  @param unit   pointer to unit.
**  @param x      X map position to build.
**  @param y      Y map position to build.
**  @param what   Unit type to build.
**  @param flush  if true, flush command queue.
*/
void CommandBuildBuilding(CUnit *unit, int x, int y,
	CUnitType *what, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionBuild;
		order->X = x;
		order->Y = y;
		order->Width = what->TileWidth;
		order->Height = what->TileHeight;
		if (what->BuilderOutside) {
			order->Range = unit->Type->RepairRange;
		} else {
			// If building inside, but be next to stop
			if (what->ShoreBuilding && unit->Type->UnitType == UnitTypeLand) {
					// Peon won't dive :-)
				order->Range = 1;
			}
		}
		order->Type = what;
		if (what->BuilderOutside) {
			order->MinRange = 1;
		}
	}
	ClearSavedAction(unit);
}

/**
**  Cancel the building construction, or kill an unit.
**
**  @param unit  pointer to unit.
*/
void CommandDismiss(CUnit *unit)
{
	//
	// Check if building is still under construction? (NETWORK!)
	//
	if (unit->Orders[0]->Action == UnitActionBuilt) {
		unit->Data.Built.Cancel = 1;
	} else {
		DebugPrint("Suicide unit ... \n");
		LetUnitDie(unit);
	}
	ClearSavedAction(unit);
}

/**
**  Send unit harvest a location
**
**  @param unit   pointer to unit.
**  @param x      X map position for harvest.
**  @param y      Y map position for harvest.
**  @param flush  if true, flush command queue.
*/
void CommandResourceLoc(CUnit *unit, int x, int y, int flush)
{
	COrder *order;
	int nx;
	int ny;

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionResource;

		//  Find the closest piece of wood next to a tile where the unit can move
		if (!FindTerrainType(0, (unit->Type->MovementMask), 1, 20,
				unit->Player, x, y, &nx, &ny)) {
			DebugPrint("FIXME: Give up???\n");
		}

		// Max Value > 1
		if ((abs(nx - x) | abs(ny - y)) > 1) {
			if (!FindTerrainType(0, MapFieldForest, 0, 20, unit->Player,
					nx, ny, &nx, &ny)) {
				DebugPrint("FIXME: Give up???\n");
			}
		} else {
			// The destination is next to a reacahble tile.
			nx = x;
			ny = y;
		}
		order->X = nx;
		order->Y = ny;

		order->Range = 1;
	}
	ClearSavedAction(unit);
}

/**
**  Send unit to harvest resources
**
**  @param unit   pointer to unit.
**  @param dest   destination unit.
**  @param flush  if true, flush command queue.
*/
void CommandResource(CUnit *unit, CUnit *dest, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid and Goal still alive? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie &&
			!dest->Destroyed) {
		// FIXME: more races, could happen with many orders in queue.
		if (!unit->Type->Building && !unit->Type->Harvester) {
			ClearSavedAction(unit);
			return;
		}

		// FIXME: if low-level supports searching, pass NoUnitP down.

		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();
		order->Action = UnitActionResource;
		order->Goal = dest;
		dest->RefsIncrease();
		order->Range = 1;
	}
	ClearSavedAction(unit);
}

/**
**  Let unit returning goods.
**
**  @param unit   pointer to unit.
**  @param goal   bring goods to this depot.
**  @param flush  if true, flush command queue.
*/
void CommandReturnGoods(CUnit *unit, CUnit *goal, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid and Goal still alive? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		// FIXME: more races, could happen with many orders in queue.
		if (!unit->Type->Building && !unit->Type->Harvester && !unit->ResourcesHeld) {
			ClearSavedAction(unit);
			return;
		}

		if (unit->Type->Building) {
			// FIXME: should find a better way for pending orders.
			order = &unit->NewOrder;
			ReleaseOrder(order);
		} else if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionReturnGoods;
		//
		// Destination could be killed. NETWORK!
		//
		if (goal && !goal->Destroyed) {
			order->Goal = goal;
			goal->RefsIncrease();
		}
		order->Range = 1;
	}
	ClearSavedAction(unit);
}

/**
**  Building starts training an unit.
**
**  @param unit   pointer to unit.
**  @param type   unit type to train.
**  @param flush  if true, flush command queue.
*/
void CommandTrainUnit(CUnit *unit, CUnitType *type, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		//
		// Check if enough resources remains? (NETWORK!)
		// FIXME: wrong if append to message queue!!!
		//
		if (unit->Player->CheckLimits(type) < 0 ||
				unit->Player->CheckUnitType(type)) {
			// StratagusAI MOD =============================================
			socketInterface->addEvent(SocketInterface::ERROR_TRAIN,
						UnitNumber(unit), 0);
			// =============================================================
			return;
		}

		//
		// Not already training?
		//
		if (!EnableTrainingQueue && unit->Orders[0]->Action == UnitActionTrain) {
			DebugPrint("Unit queue full!\n");
			return;
		}
		if (!(order = GetNextOrder(unit, 0))) {
			return;
		}
		order->Init();

		order->Action = UnitActionTrain;
		order->Type = type;
		// FIXME: if you give quick an other order, the resources are lost!
		unit->Player->SubUnitType(type);
	}
	ClearSavedAction(unit);
}

/**
**  Cancel the training of an unit.
**
**  @param unit  pointer to unit.
**  @param slot  slot number to cancel.
**  @param type  Unit-type to cancel.
*/
void CommandCancelTraining(CUnit *unit, int slot, const CUnitType *type)
{
	DebugPrint("Cancel %d type: %s\n" _C_ slot _C_
		type ? type->Ident.c_str() : "-any-");

	ClearSavedAction(unit);

	//
	// Check if unit is still training 'slot'? (NETWORK!)
	//

	if (slot == -1) {
		// Cancel All training
		while (unit->Orders[0]->Action == UnitActionTrain) {
			unit->Player->AddCostsFactor(
				unit->Orders[0]->Type->Stats[unit->Player->Index].Costs,
				CancelTrainingCostsFactor);
			RemoveOrder(unit, 0);
		}
		unit->Data.Train.Ticks = 0;
		if (unit->Player == ThisPlayer && unit->Selected) {
			SelectedUnitChanged();
		}
	} else if (unit->OrderCount <= slot) {
		// Order has moved
		return;
	} else if (unit->Orders[slot]->Action != UnitActionTrain) {
		// Order has moved, we are not training
		return;
	} else if (unit->Orders[slot]->Action == UnitActionTrain) {
		// Still training this order, same unit?
		if (type && unit->Orders[slot]->Type != type) {
			// Different unit being trained
			return;
		}

		DebugPrint("Cancel training\n");

		unit->Player->AddCostsFactor(
			unit->Orders[slot]->Type->Stats[unit->Player->Index].Costs,
			CancelTrainingCostsFactor);

	
		if (!slot) { // Canceled in work slot
			unit->Data.Train.Ticks = 0;
		}
		RemoveOrder(unit, slot);

		//
		// Update interface.
		//
		if (unit->Player == ThisPlayer && unit->Selected) {
			SelectedUnitChanged();
		}
	}
}

/**
**  Building starts upgrading to.
**
**  @param unit   pointer to unit.
**  @param type   upgrade to type
**  @param flush  if true, flush command queue.
*/
void CommandUpgradeTo(CUnit *unit, CUnitType *type, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid and Goal still alive? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		//
		// Check if enough resources remains? (NETWORK!)
		//
		if (unit->Player->CheckUnitType(type)) {
			return;
		}

		if (!flush) {
			DebugPrint("FIXME: must support order queing!!");
		}
		if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		// FIXME: if you give quick an other order, the resources are lost!
		unit->Player->SubUnitType(type);

		order->Action = UnitActionUpgradeTo;
		order->Type = type;
	}
	ClearSavedAction(unit);
}

/**
**  Immediate transforming unit into type.
**
**  @param unit   pointer to unit.
**  @param type   upgrade to type
*/
void CommandTransformIntoType(CUnit *unit, CUnitType *type)
{
	COrder *order;

	Assert(unit->CriticalOrder.Action == UnitActionStill);
	order = &unit->CriticalOrder;
	order->Init();

	order->Action = UnitActionTransformInto;
	order->Type = type;
}

/**
**  Cancel building upgrading to.
**
**  @param unit  pointer to unit.
*/
void CommandCancelUpgradeTo(CUnit *unit)
{
	ReleaseOrders(unit); // empty command queue

	//
	// Check if unit is still upgrading? (NETWORK!)
	//
	if (unit->Orders[0]->Action == UnitActionUpgradeTo) {

		unit->Player->AddCostsFactor(
			unit->Orders[0]->Type->Stats[unit->Player->Index].Costs,
			CancelUpgradeCostsFactor);

		unit->Orders[0]->Init();

		unit->Orders[0]->Action = UnitActionStill;

		unit->SubAction = 0;

		//
		// Update interface.
		//
		if (unit->Player == ThisPlayer && unit->Selected) {
			SelectedUnitChanged();
		}
	}
	ClearSavedAction(unit);
}

/**
**  Building starts researching.
**
**  @param unit   pointer to unit.
**  @param what   what to research.
**  @param flush  if true, flush command queue.
*/
void CommandResearch(CUnit *unit, CUpgrade *what, int flush)
{
	COrder *order;

	//
	// Check if unit is still valid and Goal still alive? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		//
		// Check if enough resources remains? (NETWORK!)
		//
		if (unit->Player->CheckCosts(what->Costs)) {
			return;
		}

		if (!flush) {
			DebugPrint("FIXME: must support order queing!!");
		} else {
			if (unit->Orders[0]->Action == UnitActionResearch) {
				const CUpgrade *upgrade;

				// Cancel current research
				upgrade = unit->Data.Research.Upgrade;
				unit->Player->UpgradeTimers.Upgrades[upgrade->ID] = 0;
				unit->Player->AddCostsFactor(upgrade->Costs,
					CancelResearchCostsFactor);
				unit->SubAction = 0;
			}
		}

		if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		// FIXME: if you give quick an other order, the resources are lost!
		unit->Player->SubCosts(what->Costs);

		order->Action = UnitActionResearch;
		order->X = order->Y = -1;
		order->Arg1.Upgrade = what;
	}
	ClearSavedAction(unit);
}

/**
**  Cancel Building researching.
**
**  @param unit  Pointer to unit.
*/
void CommandCancelResearch(CUnit *unit)
{
	ReleaseOrders(unit); // empty command queue

	//
	// Check if unit is still researching? (NETWORK!)
	//
	if (unit->Orders[0]->Action == UnitActionResearch) {
		const CUpgrade *upgrade;

		upgrade = unit->Data.Research.Upgrade;
		unit->Player->UpgradeTimers.Upgrades[upgrade->ID] = 0;

		unit->Player->AddCostsFactor(upgrade->Costs,
			CancelResearchCostsFactor);
		unit->Orders[0]->Init();

		unit->Orders[0]->Action = UnitActionStill;

		unit->SubAction = 0;

		//
		// Update interface.
		//
		if (unit->Player == ThisPlayer && unit->Selected) {
			SelectedUnitChanged();
		}
	}
	ClearSavedAction(unit);
}

/**
**  Cast a spell at position or unit.
**
**  @param unit   Pointer to unit.
**  @param x      X map position to spell cast on.
**  @param y      Y map position to spell cast on.
**  @param dest   Spell cast on unit (if exist).
**  @param spell  Spell type pointer.
**  @param flush  If true, flush command queue.
*/
void CommandSpellCast(CUnit *unit, int x, int y, CUnit *dest,
	SpellType *spell, int flush)
{
	COrder *order;

	Assert(x >= 0 && y >= 0 && x < Map.Info.MapWidth && y < Map.Info.MapHeight);

	DebugPrint(": %d casts %s at %d %d on %d\n" _C_
		UnitNumber(unit) _C_ spell->Ident.c_str() _C_ x _C_ y _C_ dest ? UnitNumber(dest) : 0);
	Assert(unit->Type->CanCastSpell[spell->Slot]);

	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		// FIXME: should I check here, if there is still enough mana?

		if (!(order = GetNextOrder(unit, flush))) {
			return;
		}
		order->Init();

		order->Action = UnitActionSpellCast;
		order->Range = spell->Range;
		if (dest) {
			//
			// Destination could be killed.
			// Should be handled in action, but is not possible!
			// Unit::Refs is used as timeout counter.
			//
			if (dest->Destroyed) {
				// FIXME: where check if spell needs an unit as destination?
				// FIXME: dest->Type is now set to 0. maybe we shouldn't bother.
				order->X = dest->X /*+ dest->Type->TileWidth / 2*/  - order->Range;
				order->Y = dest->Y /*+ dest->Type->TileHeight / 2*/ - order->Range;
				order->Range <<= 1;
			} else {
				order->Goal = dest;
				dest->RefsIncrease();
			}
		} else {
			order->X = x;
			order->Y = y;
		}
		order->Arg1.Spell = spell;
	}
	ClearSavedAction(unit);
}

/**
**  Auto spell cast.
**
**  @param unit     pointer to unit.
**  @param spellid  Spell id.
**  @param on       1 for auto cast on, 0 for off.
*/
void CommandAutoSpellCast(CUnit *unit, int spellid, int on)
{
	//
	// Check if unit is still valid? (NETWORK!)
	//
	if (!unit->Removed && unit->Orders[0]->Action != UnitActionDie) {
		unit->AutoCastSpell[spellid] = on;
	}
}

/**
**  Diplomacy changed.
**
**  @param player    Player which changes his state.
**  @param state     New diplomacy state.
**  @param opponent  Opponent.
*/
void CommandDiplomacy(int player, int state, int opponent)
{
	switch (state) {
		case DiplomacyNeutral:
			Players[player].Enemy &= ~(1 << opponent);
			Players[player].Allied &= ~(1 << opponent);
			break;
		case DiplomacyAllied:
			Players[player].Enemy &= ~(1 << opponent);
			Players[player].Allied |= 1 << opponent;
			break;
		case DiplomacyEnemy:
			Players[player].Enemy |= 1 << opponent;
			Players[player].Allied &= ~(1 << opponent);
			break;
		case DiplomacyCrazy:
			Players[player].Enemy |= 1 << opponent;
			Players[player].Allied |= 1 << opponent;
			break;
	}
	// FIXME: Should we display a message?
}

/**
**  Shared vision changed.
**
**  @param player    Player which changes his state.
**  @param state     New shared vision state.
**  @param opponent  Opponent.
*/
void CommandSharedVision(int player, bool state, int opponent)
{
	int before;
	int after;
	int x;
	int y;
	int i;

	//
	// Do a real hardcore seen recount. First we unmark EVERYTHING.
	//
	for (i = 0; i < NumUnits; ++i) {
		if (!Units[i]->Destroyed) {
			MapUnmarkUnitSight(Units[i]);
		}
	}

	//
	// Compute Before and after.
	//
	before = Players[player].IsBothSharedVision(&Players[opponent]);
	if (state == false) {
		Players[player].SharedVision &= ~(1 << opponent);
	} else {
		Players[player].SharedVision |= (1 << opponent);
	}
	after = Players[player].IsBothSharedVision(&Players[opponent]);

	if (before && !after) {
		//
		// Don't share vision anymore. Give each other explored terrain for good-bye.
		//
		for (x = 0; x < Map.Info.MapWidth; ++x) {
			for (y = 0; y < Map.Info.MapHeight; ++y) {
				i = x + y * Map.Info.MapWidth;
				if (Map.Fields[i].Visible[player] && !Map.Fields[i].Visible[opponent]) {
					Map.Fields[i].Visible[opponent] = 1;
					if (opponent == ThisPlayer->Index) {
						Map.MarkSeenTile(x, y);
					}
				}
				if (Map.Fields[i].Visible[opponent] && !Map.Fields[i].Visible[player]) {
					Map.Fields[i].Visible[player] = 1;
					if (player == ThisPlayer->Index) {
						Map.MarkSeenTile(x, y);
					}
				}
			}
		}
	}

	//
	// Do a real hardcore seen recount. Now we remark EVERYTHING
	//
	for (i = 0; i < NumUnits; ++i) {
		if (!Units[i]->Destroyed) {
			MapMarkUnitSight(Units[i]);
		}
	}
}

/**
**  Player quit.
**
**  @param player  Player number that quit.
*/
void CommandQuit(int player)
{
	int i;

	// Set player to neutral, remove allied/enemy/shared vision status
	// If the player doesn't have any units then this is pointless?
	Players[player].Type = PlayerNeutral;
	for (i = 0; i < NumPlayers; ++i) {
		if (i != player && Players[i].Team != Players[player].Team) {
			Players[i].Allied &= ~(1 << player);
			Players[i].Enemy &= ~(1 << player);
			Players[player].Enemy &= ~(1 << i);
			Players[player].Allied &= ~(1 << i);
			//  We clear Shared vision by sending fake shared vision commands.
			//  We do this because Shared vision is a bit complex.
			CommandSharedVision(i, 0, player);
			CommandSharedVision(player, 0, i);
			// Remove Selection from Quit Player
			ChangeTeamSelectedUnits(&Players[player], NULL, 0, 0);
		}
	}

	if (Players[player].TotalNumUnits != 0) {
		SetMessage(_("Player \"%s\" has left the game"), Players[player].Name.c_str());
	} else {
		SetMessage(_("Player \"%s\" has been killed"), Players[player].Name.c_str());
	}
}

//@}
