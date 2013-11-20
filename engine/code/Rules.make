##       _________ __                 __                               
##      /   _____//  |_____________ _/  |______     ____  __ __  ______
##      \_____  \\   __\_  __ \__  \\   __\__  \   / ___\|  |  \/  ___/
##      /        \|  |  |  | \// __ \|  |  / __ \_/ /_/  >  |  /\___ \ 
##     /_______  /|__|  |__|  (____  /__| (____  /\___  /|____//____  >
##             \/                  \/          \//_____/            \/ 
##  ______________________                           ______________________
##			  T H E   W A R   B E G I N S
##	   Stratagus - A free fantasy real time strategy game engine
##

# Compile commands
CXX=g++
RM=rm -f
MAKE=make

# Prefix for 'make install'
PREFIX=/usr/local

CPPFLAGS=-DHAVE_CONFIG_H  -I/usr/include/lua50 -I/usr/include/lua5.1 -DUSE_BZ2LIB -DUSE_VORBIS -I/usr/include/SDL -D_GNU_SOURCE=1 -D_REENTRANT -DUSE_ZLIB -I$(TOPDIR) -I$(TOPDIR)/src/include -I$(TOPDIR)/src/guichan/include

CXXFLAGS= -O2 -pipe -fsigned-char -fomit-frame-pointer -fexpensive-optimizations -ffast-math
LDFLAGS= -lX11 -lbz2 -lvorbis -logg -ldl -llua50  -llualib50 -llua50  -L/usr/lib -lSDL -lpng -lz -lm

OBJDIR=obj

DISTLIST=$(TOPDIR)/distlist
TAGS=$(TOPDIR)/src/tags
