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
/**@name translate.cpp - Translate languages. */
//
//      (c) Copyright 2005 by Jimmy Salmon
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
//      $Id: translate.cpp 7726 2006-11-18 20:58:36Z jsalmon3 $

//@{

/*----------------------------------------------------------------------------
--  Includes
----------------------------------------------------------------------------*/

#include "stratagus.h"

#include <map>
#include <string>

#include "translate.h"
#include "iolib.h"
#include "stdio.h"

/*----------------------------------------------------------------------------
-- Variables
----------------------------------------------------------------------------*/

typedef std::map<std::string, std::string> EntriesType;
static EntriesType Entries;
std::string StratagusTranslation;
std::string GameTranslation;

/*----------------------------------------------------------------------------
--  Functions
----------------------------------------------------------------------------*/

/**
**  Translate a string
*/
const char *Translate(const char *str)
{
	EntriesType::iterator i = Entries.find(str);
	if (i != Entries.end() && !i->second.empty()) {
		return i->second.c_str();
	} else {
		return str;
	}
}

/**
**  Add a translation
*/
void AddTranslation(const char *str1, const char *str2)
{
	Entries[str1] = str2;
}

/**
**  Load a .po file
*/
void LoadPO(const char *file)
{
	FILE *fd;
	char buf[4096];
	enum { MSGNONE, MSGID, MSGSTR } state;
	char msgid[16 * 1024];
	char msgstr[16 * 1024];
	char *s;
	char *currmsg = NULL;
	char fullfile[1024];

	if (!file || !*file) {
		return;
	}

	LibraryFileName(file, fullfile, sizeof(fullfile));
	fd = fopen(fullfile, "rb");
	if (!fd) {
		fprintf(stderr, "Could not open file: %s\n", file);
		return;
	}

	state = MSGNONE;
	msgid[0] = msgstr[0] = '\0';

	// skip 0xEF utf8 intro if found
	char c = fgetc(fd);
	if (c == (char)0xEF) {
		fgetc(fd);
		fgetc(fd);
	} else {
		rewind(fd);
	}

	while (fgets(buf, sizeof(buf), fd)) {
		// Comment
		if (buf[0] == '#') {
			continue;
		}

		s = buf;

		// msgid or msgstr
		if (!strncmp(s, "msgid ", 6)) {
			if (state == MSGSTR) {
				*currmsg = '\0';
				if (*msgid != '\0') {
					AddTranslation(msgid, msgstr);
				}
			}
			state = MSGID;
			currmsg = msgid;
			*currmsg = '\0';
			s += 6;
			while (*s == ' ') ++s;
		} else if (!strncmp(s, "msgstr ", 7)) {
			if (state == MSGID) {
				*currmsg = '\0';
			}
			state = MSGSTR;
			currmsg = msgstr;
			*currmsg = '\0';
			s += 7;
			while (*s == ' ') ++s;
		}

		// String
		if (*s == '"') {
			++s;
			while (*s && *s != '"') {
				if (*s == '\\') {
					++s;
					if (*s) {
						if (*s == 'n') {
							*currmsg++ = '\n';
						} else if (*s == 't') {
							*currmsg++ = '\t';
						} else if (*s == 'r') {
							*currmsg++ = '\r';
						} else if (*s == '"') {
							*currmsg++ = '"';
						} else if (*s == '\\') {
							*currmsg++ = '\\';
						} else {
							fprintf(stderr, "Invalid escape character: %c\n", *s);
						}
						++s;
					} else {
						fprintf(stderr, "Unterminated string\n");
					}
				} else {
					*currmsg++ = *s++;
				}
			}
			continue;
		}
	}
	if (state == MSGSTR) {
		*currmsg = '\0';
		AddTranslation(msgid, msgstr);
	}

	fclose(fd);
}

/** Set the stratagus and game translations
**
** Those filenames will be saved in the preferences when SavePreferences will be called.
**/
void SetTranslationsFiles(const char *stratagusfile, const char *gamefile)
{
	LoadPO(stratagusfile);
	LoadPO(gamefile);
	StratagusTranslation = stratagusfile;
	GameTranslation = gamefile;
}
//@}

