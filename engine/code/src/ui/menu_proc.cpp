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
/**@name menu_proc.cpp - The menu processing code. */
//
//      (c) Copyright 1999-2006 by Andreas Arens, Jimmy Salmon, Nehal Mistry
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
//      $Id: menu_proc.cpp 7815 2006-12-09 03:55:32Z jsalmon3 $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include "stratagus.h"
#include "ui.h"
#include "video.h"
#include "font.h"
#include "menus.h"

/*----------------------------------------------------------------------------
-- Variables
----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------
-- Menu operation functions
----------------------------------------------------------------------------*/

/**
**  Draw menu button 'button' on x,y
**
**  @param style  Button style
**  @param flags  State of Button (clicked, mouse over...)
**  @param x      X display position
**  @param y      Y display position
**  @param text   text to print on button
*/
void DrawMenuButton(ButtonStyle *style, unsigned flags, int x, int y,
	const std::string &text)
{
	std::string nc;
	std::string rc;
	std::string oldnc;
	std::string oldrc;
	int i;
	ButtonStyleProperties *p;
	ButtonStyleProperties *pimage;

	if (flags & MI_FLAGS_CLICKED) {
		p = &style->Clicked;
	} else if (flags & MI_FLAGS_ACTIVE) {
		p = &style->Hover;
	} else {
		p = &style->Default;
	}

	//
	//  Image
	//
	pimage = p;
	if (!p->Sprite) {
		// No image.  Try hover, selected, then default
		if ((flags & MI_FLAGS_ACTIVE) && style->Hover.Sprite) {
			pimage = &style->Hover;
		} else if (style->Default.Sprite) {
			pimage = &style->Default;
		}
	}
	if (pimage->Sprite) {
		pimage->Sprite->Load();
	}
	if (pimage->Sprite) {
		pimage->Sprite->DrawFrame(pimage->Frame, x, y);
	}

	//
	//  Text
	//
	if (!text.empty()) {
		GetDefaultTextColors(oldnc, oldrc);
		nc = !p->TextNormalColor.empty() ? p->TextNormalColor :
			!style->TextNormalColor.empty() ? style->TextNormalColor : oldnc;
		rc = !p->TextReverseColor.empty() ? p->TextReverseColor :
			!style->TextReverseColor.empty() ? style->TextReverseColor : oldrc;
		SetDefaultTextColors(nc, rc);

		if (p->TextAlign == TextAlignCenter || p->TextAlign == TextAlignUndefined) {
			VideoDrawTextCentered(x + p->TextX, y + p->TextY,
				style->Font, text);
		} else if (p->TextAlign == TextAlignLeft) {
			VideoDrawText(x + p->TextX, y + p->TextY, style->Font, text);
		} else {
			VideoDrawText(x + p->TextX - style->Font->Width(text.c_str()), y + p->TextY,
				style->Font, text);
		}

		SetDefaultTextColors(oldnc, oldrc);
	}

	//
	//  Border
	//
	if (!p->BorderColor) {
		p->BorderColor = Video.MapRGB(TheScreen->format,
			p->BorderColorRGB.r, p->BorderColorRGB.g, p->BorderColorRGB.b);
	}
	if (p->BorderSize) {
		for (i = 0; i < p->BorderSize; ++i) {
			Video.DrawRectangleClip(p->BorderColor, x - i, y - i,
				style->Width + 2 * i, style->Height + 2 * i);
		}
	}
}

#if 0
/**
** Process a menu.
**
** @param menu_id The menu number to process
** @param loop Indicates to setup handlers and really 'Process'
**
** @todo FIXME: This function is called from the event handler!!
*/
void ProcessMenu(const char *menu_id, int loop)
{
	int oldncr;

	CancelBuildingMode();

	// Recursion protection:
	if (loop) {
		InterfaceState = IfaceStateMenu;
	}

	ButtonUnderCursor = -1;

	if (loop) {
		while (CurrentMenu != NULL) {
			CheckMusicFinished();

			InterfaceState = IfaceStateNormal;
			UpdateDisplay();
			InterfaceState = IfaceStateMenu;

			RealizeVideoMemory();
			oldncr = NetConnectRunning;
//			WaitEventsOneFrame(&MenuCallbacks);
			if (NetConnectRunning == 2) {
				NetworkProcessClientRequest();
			}
			if (NetConnectRunning == 1) {
				NetworkProcessServerRequest();
			}
		}
	}
}
#endif

//@}
