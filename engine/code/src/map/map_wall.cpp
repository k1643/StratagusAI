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
/**@name map_wall.cpp - The map wall handling. */
//
//      (c) Copyright 1999-2005 by Vladi Shabanski
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
//      $Id: map_wall.cpp 7028 2005-10-18 18:32:20Z jarod42 $

//@{

/*----------------------------------------------------------------------------
-- Includes
----------------------------------------------------------------------------*/

#include <stdio.h>

#include "stratagus.h"
#include "map.h"
#include "tileset.h"
#include "ui.h"
#include "player.h"
#include "unittype.h"

/*----------------------------------------------------------------------------
-- Declarations
----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------
-- Variables
----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------
-- Functions
----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------
-- Fix walls (connections)
----------------------------------------------------------------------------*/

/*
  Vladi:
  NOTE: this is not the original behaviour of the wall demolishing,
  instead I'm replacing tiles just as the wood fixing, so if part of
  a wall is down side neighbours are fixed just as current tile is
  empty one. It is still nice... :)

  For the connecting new walls -- all's fine.
*/

/**
** Check if the seen tile-type is wall.
**
** @param x Map X tile-position.
** @param y Map Y tile-position.
** @param walltype Walltype to check. (-1 any kind)
*/
static int MapIsSeenTileWall(int x, int y, int walltype)
{
	int t;

	t = Map.Tileset.TileTypeTable[
		Map.Fields[x + y * Map.Info.MapWidth].SeenTile];
	if (walltype == -1) {
		return t == TileTypeHumanWall || t == TileTypeOrcWall;
	}
	return t == walltype;
}

/**
** Correct the seen wall field, depending on the surrounding.
**
** @param x Map X tile-position.
** @param y Map Y tile-position.
*/
void MapFixSeenWallTile(int x, int y)
{
	int t;
	int tile;
	CMapField *mf;

	//  Outside of map or no wall.
	if (x < 0 || y < 0 || x >= Map.Info.MapWidth || y >= Map.Info.MapHeight) {
		return;
	}
	mf = Map.Fields + x + y * Map.Info.MapWidth;
	t = Map.Tileset.TileTypeTable[mf->SeenTile];
	if (t != TileTypeHumanWall && t != TileTypeOrcWall) {
		return;
	}

	//
	//  Calculate the correct tile. Depends on the surrounding.
	//
	tile = 0;
	if ((y - 1) < 0 || MapIsSeenTileWall(x, y - 1, t)) {
		tile |= 1 << 0;
	}
	if ((x + 1) >= Map.Info.MapWidth || MapIsSeenTileWall(x + 1, y, t)) {
		tile |= 1 << 1;
	}
	if ((y + 1) >= Map.Info.MapHeight || MapIsSeenTileWall(x, y + 1, t)) {
		tile |= 1 << 2;
	}
	if ((x - 1) < 0 || MapIsSeenTileWall(x - 1, y, t)) {
		tile |= 1 << 3;
	}

	if (t == TileTypeHumanWall) {
		tile = Map.Tileset.HumanWallTable[tile];
		if (UnitTypeHumanWall && mf->Value <= UnitTypeHumanWall->Variable[HP_INDEX].Max / 2) {
			while (Map.Tileset.Table[tile]) { // Skip good tiles
				++tile;
			}
			while (!Map.Tileset.Table[tile]) { // Skip separator
				++tile;
			}
		}
	} else {
		tile = Map.Tileset.OrcWallTable[tile];
		if (UnitTypeOrcWall && mf->Value <= UnitTypeOrcWall->Variable[HP_INDEX].Max / 2) {
			while (Map.Tileset.Table[tile]) { // Skip good tiles
				++tile;
			}
			while (!Map.Tileset.Table[tile]) { // Skip separator
				++tile;
			}
		}
	}
	if (mf->Value == 0) {
		while (Map.Tileset.Table[tile]) { // Skip good tiles
			++tile;
		}
		while (!Map.Tileset.Table[tile]) { // Skip separator
			++tile;
		}
	}
	tile = Map.Tileset.Table[tile];

	if (mf->SeenTile != tile) { // Already there!
		mf->SeenTile = tile;

		// FIXME: can this only happen if seen?
		if (Map.IsFieldVisible(ThisPlayer, x, y)) {
			UI.Minimap.UpdateSeenXY(x, y);
		}
	}
}

/**
** Correct the surrounding seen wall fields.
**
** @param x Map X tile-position.
** @param y Map Y tile-position.
*/
void MapFixSeenWallNeighbors(int x, int y)
{
	MapFixSeenWallTile(x + 1, y); // side neighbors
	MapFixSeenWallTile(x - 1, y);
	MapFixSeenWallTile(x, y + 1);
	MapFixSeenWallTile(x, y - 1);
}

/**
** Correct the real wall field, depending on the surrounding.
**
** @param x Map X tile-position.
** @param y Map Y tile-position.
*/
void MapFixWallTile(int x, int y)
{
	int tile;
	CMapField *mf;
	int t;

	//  Outside of map or no wall.
	if (x < 0 || y < 0 || x >= Map.Info.MapWidth || y >= Map.Info.MapHeight) {
		return;
	}
	mf = Map.Fields + x + y * Map.Info.MapWidth;
	if (!(mf->Flags & MapFieldWall)) {
		return;
	}

	t = mf->Flags & (MapFieldHuman | MapFieldWall);
	//
	//  Calculate the correct tile. Depends on the surrounding.
	//
	tile = 0;
	if ((y - 1) < 0 || (Map.Fields[x + (y - 1) * Map.Info.MapWidth].
			Flags & (MapFieldHuman | MapFieldWall)) == t) {
		tile |= 1 << 0;
	}
	if ((x + 1) >= Map.Info.MapWidth || (Map.Fields[x + 1 + y * Map.Info.MapWidth].
			Flags & (MapFieldHuman | MapFieldWall)) == t) {
		tile |= 1 << 1;
	}
	if ((y + 1) >= Map.Info.MapHeight || (Map.Fields[x + (y + 1) * Map.Info.MapWidth].
			Flags & (MapFieldHuman | MapFieldWall)) == t) {
		tile |= 1 << 2;
	}
	if ((x - 1) < 0 || (Map.Fields[x - 1 + y * Map.Info.MapWidth].
			Flags & (MapFieldHuman | MapFieldWall)) == t) {
		tile |= 1 << 3;
	}

	if (t & MapFieldHuman) {
		tile = Map.Tileset.HumanWallTable[tile];
		if (UnitTypeHumanWall && mf->Value <= UnitTypeHumanWall->Variable[HP_INDEX].Max / 2) {
			while (Map.Tileset.Table[tile]) { // Skip good tiles
				++tile;
			}
			while (!Map.Tileset.Table[tile]) { // Skip separator
				++tile;
			}
		}
	} else {
		tile = Map.Tileset.OrcWallTable[tile];
		if (UnitTypeOrcWall && mf->Value <= UnitTypeOrcWall->Variable[HP_INDEX].Max / 2) {
			while (Map.Tileset.Table[tile]) { // Skip good tiles
				++tile;
			}
			while (!Map.Tileset.Table[tile]) { // Skip separator
				++tile;
			}
		}
	}
	if (mf->Value == 0) {
		while (Map.Tileset.Table[tile]) { // Skip good tiles
			++tile;
		}
		while (!Map.Tileset.Table[tile]) { // Skip separator
			++tile;
		}
	}
	tile = Map.Tileset.Table[tile];

	if (mf->Tile != tile) {
		mf->Tile = tile;
		UI.Minimap.UpdateXY(x, y);

		if (Map.IsFieldVisible(ThisPlayer, x, y)) {
			UI.Minimap.UpdateSeenXY(x, y);
			Map.MarkSeenTile(x, y);
		}
	}
}

/**
** Correct the surrounding real wall fields.
**
** @param x Map X tile-position.
** @param y Map Y tile-position.
*/
static void MapFixWallNeighbors(int x, int y)
{
	MapFixWallTile(x + 1, y); // side neighbors
	MapFixWallTile(x - 1, y);
	MapFixWallTile(x, y + 1);
	MapFixWallTile(x, y - 1);
}

/**
** Remove wall from the map.
**
** @param x  Map X position.
** @param y  Map Y position.
*/
void CMap::RemoveWall(unsigned x, unsigned y)
{
	CMapField *mf;

	mf = this->Fields + x + y * this->Info.MapWidth;
	mf->Value = 0;
	// FIXME: support more walls of different races.
	mf->Flags &= ~(MapFieldHuman | MapFieldWall | MapFieldUnpassable);

	UI.Minimap.UpdateXY(x, y);
	MapFixWallTile(x, y);
	MapFixWallNeighbors(x, y);

	if (Map.IsFieldVisible(ThisPlayer, x, y)) {
		UI.Minimap.UpdateSeenXY(x, y);
		this->MarkSeenTile(x, y);
	}
}

/**
** Set wall onto the map.
**
** @param x  Map X position.
** @param y  Map Y position.
** @param humanwall Flag, if true set a human wall.
**
** @todo FIXME: support for more races.
*/
void CMap::SetWall(unsigned x, unsigned y, int humanwall)
{
	CMapField *mf;

	mf = this->Fields + x + y * this->Info.MapWidth;

	// FIXME: support more walls of different races.
	if (humanwall) {
		// FIXME: Set random walls
		mf->Tile = this->Tileset.Table[this->Tileset.HumanWallTable[0]];
		mf->Flags |= MapFieldWall | MapFieldUnpassable | MapFieldHuman;
		mf->Value = UnitTypeHumanWall->Variable[HP_INDEX].Max;
	} else {
		// FIXME: Set random walls
		mf->Tile = this->Tileset.Table[this->Tileset.OrcWallTable[0]];
		mf->Flags |= MapFieldWall | MapFieldUnpassable;
		mf->Value = UnitTypeOrcWall->Variable[HP_INDEX].Max;
	}

	UI.Minimap.UpdateXY(x, y);
	MapFixWallTile(x, y);
	MapFixWallNeighbors(x, y);

	if (Map.IsFieldVisible(ThisPlayer, x, y)) {
		UI.Minimap.UpdateSeenXY(x, y);
		this->MarkSeenTile(x, y);
	}
}

/**
** Wall is hit with damage.
**
** @param x       Map X tile-position of wall.
** @param y       Map Y tile-position of wall.
** @param damage  Damage done to wall.
*/
void CMap::HitWall(unsigned x, unsigned y, unsigned damage)
{
	unsigned v;

	v = this->Fields[x + y * this->Info.MapWidth].Value;
	if (v <= damage) {
		RemoveWall(x, y);
	} else {
		this->Fields[x + y * this->Info.MapWidth].Value = v - damage;
		MapFixWallTile(x, y);
	}
}

//@}
