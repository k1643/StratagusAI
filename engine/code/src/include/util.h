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
/**@name util.h - General utilities. */
//
//      (c) Copyright 1998-2006 by Lutz Sammer and Jimmy Salmon
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
//      $Id: util.h 7783 2006-11-26 20:19:46Z nehalmistry $

#ifndef __UTIL_H__
#define __UTIL_H__

//@{

/*----------------------------------------------------------------------------
--  Random
----------------------------------------------------------------------------*/

extern unsigned SyncRandSeed;               /// Sync random seed value

extern void InitSyncRand(void);             /// Initialize the syncron rand
extern int SyncRand(void);                  /// Syncron rand

/*----------------------------------------------------------------------------
--  Math
----------------------------------------------------------------------------*/

	///  rand only used on this computer.
#define MyRand() rand()

	/// Compute a square root using ints
extern long isqrt(long num);

/*----------------------------------------------------------------------------
--  Strings
----------------------------------------------------------------------------*/

#if !defined(_MSC_VER) || _MSC_VER < 1400
#define _TRUNCATE ((size_t)-1)
extern unsigned int strcpy_s(char *dst, size_t dstsize, const char *src);
extern unsigned int strncpy_s(char *dst, size_t dstsize, const char *src, size_t count);
extern unsigned int strcat_s(char *dst, size_t dstsize, const char *src);
#endif

	/// strdup + strcat
extern char *strdcat(const char *l, const char *r);
	/// strdup + strcat + strcat
extern char *strdcat3(const char *l, const char *m, const char *r);

#ifndef HAVE_STRCASESTR
	/// case insensitive strstr
extern char *strcasestr(const char *str, const char *substr);
#endif // !HAVE_STRCASESTR

#ifndef HAVE_STRNLEN
	/// determine length of a fixed-length string
extern size_t strnlen(const char *str, size_t strsize);
#endif // !HAVE_STRNLEN

/*----------------------------------------------------------------------------
--  Clipboard
----------------------------------------------------------------------------*/

#include <string>

int GetClipboard(std::string &str);

/*----------------------------------------------------------------------------
--  UTF8
----------------------------------------------------------------------------*/

int UTF8GetNext(const std::string &text, int curpos);
int UTF8GetPrev(const std::string &text, int curpos);

//@}

#endif /* __UTIL_H__ */
