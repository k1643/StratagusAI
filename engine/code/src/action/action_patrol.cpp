//       _________ __                 __
//      /   _____//  |_____________ _/  |______     ____  __ __  ______
//      \_____  \\   __\_  __ \__  \\   __\__  \   / ___\|  |  \/  ___/
//      /        \|  |  |  | \// __ \|  |  / __ \_/ /_/  >  |  /\___ |
//     /_______  /|__|  |__|  (____  /__| (____  /\___  /|____//____  >
//             \/                  \/          \//_____/            \/
//  ______________________                           ______________________
//                        T H E   W A R   B E G I N S
//           Stratagus - A free fantasy real time strategy game engine
//
/**@name action_patrol.cpp - The patrol action. */
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
//      $Id: action_patrol.cpp 7686 2006-11-09 03:59:54Z jsalmon3 $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>

#include "stratagus.h"
#include "unit.h"
#include "unittype.h"
#include "actions.h"
#include "pathfinder.h"

/*----------------------------------------------------------------------------
--  Functions
----------------------------------------------------------------------------*/

/**
**  Swap the patrol points.
*/
static void SwapPatrolPoints(CUnit *unit)
{
	int tmp;

	tmp = unit->Orders[0]->Arg1.Patrol.X;
	unit->Orders[0]->Arg1.Patrol.X = unit->Orders[0]->X;
	unit->Orders[0]->X = tmp;
	tmp = unit->Orders[0]->Arg1.Patrol.Y;
	unit->Orders[0]->Arg1.Patrol.Y = unit->Orders[0]->Y;
	unit->Orders[0]->Y = tmp;

	NewResetPath(unit);
}

/**
**  Unit Patrol:
**    The unit patrols between two points.
**    Any enemy unit in reaction range is attacked.
**  @todo FIXME:
**    Should do some tries to reach the end-points.
**    Should support patrol between more points!
**    Patrol between units.
**
**  @param unit  Patroling unit pointer.
*/
void HandleActionPatrol(CUnit *unit)
{
	if (unit->Wait) {
		unit->Wait--;
		return;
	}

	if (!unit->SubAction) { // first entry.
		NewResetPath(unit);
		unit->SubAction = 1;
	}

	switch (DoActionMove(unit)) {
		case PF_FAILED:
			unit->SubAction = 1;
			break;
		case PF_UNREACHABLE:
			// Increase range and try again
			unit->SubAction = 1;
			if (unit->Orders[0]->Range <= Map.Info.MapWidth ||
					unit->Orders[0]->Range <= Map.Info.MapHeight) {
				unit->Orders[0]->Range++;
				break;
			}
			// FALL THROUGH
		case PF_REACHED:
			unit->SubAction = 1;
			unit->Orders[0]->Range = 0;
			SwapPatrolPoints(unit);
			break;
		case PF_WAIT:
			// Wait for a while then give up
			unit->SubAction++;
			if (unit->SubAction == 5) {
				unit->SubAction = 1;
				unit->Orders[0]->Range = 0;
				SwapPatrolPoints(unit);
			}
			break;
		default: // moving
			unit->SubAction = 1;
			break;
	}

	if (!unit->Anim.Unbreakable) {
		//
		// Attack any enemy in reaction range.
		//  If don't set the goal, the unit can then choose a
		//  better goal if moving nearer to enemy.
		//
		if (unit->Type->CanAttack) {
			const CUnit *goal = AttackUnitsInReactRange(unit);
			if (goal) {
				DebugPrint("Patrol attack %d\n" _C_ UnitNumber(goal));
				CommandAttack(unit, goal->X, goal->Y, NULL, FlushCommands);
				// Save current command to come back.
				unit->SavedOrder = *unit->Orders[0];
				unit->Orders[0]->Action = UnitActionStill;
				unit->Orders[0]->Goal = NoUnitP;
				unit->SubAction = 0;
			}
		}
	}
}

//@}
