#!/bin/env pythonp
#
# generate maps for small-scale combat
#

from string import Template
import gzip
import random

def compress(filename):
    f_in = open(filename, 'rb')
    f_out = gzip.open(filename + '.gz', 'wb')
    f_out.writelines(f_in)
    f_out.close()
    f_in.close()

def player_type(code):
    if code == "P":
        return "person"
    elif code == "C":
        return "computer"
    return "unknown"

def gen_presentation_script(p0code, units0, p1code, units1, rows, cols):
    # PresentMap(description, nplayers, w, h, id)
    description = "\"{units0}v{units1}\"".format(**vars())
    p0type = player_type(p0code)
    p1type = player_type(p1code)
    filename = '{units0}v{units1}_{p0code}v{p1code}_{rows}x{cols}.smp'.format(**vars())
    file= open(filename,'w')
    file.write(
    """DefinePlayerTypes("{p0type}","{p1type}")
PresentMap({description}, 2, {cols}, {rows}, 1)
""".format(**vars()))
    file.close()
    compress(filename)

def gen_map_script(p0code, units0, p1code, units1, rows, cols):
    # SetStartView(player,X,Y)
    filename = '{units0}v{units1}_{p0code}v{p1code}_{rows}x{cols}.sms'.format(**vars())
    file = open(filename,'w')
    file.write(
    """SetStartView(0, 0, 2089877979)
SetPlayerData(0, "Resources", "gold", 2000)
SetPlayerData(0, "Resources", "wood", 1000)
SetPlayerData(0, "Resources", "oil", 1000)
SetPlayerData(0, "RaceName", "human")
SetAiType(0, "wc2-land-attack")
SetStartView(1, 0, 3548375)
SetPlayerData(1, "Resources", "gold", 2000)
SetPlayerData(1, "Resources", "wood", 1000)
SetPlayerData(1, "Resources", "oil", 1000)
SetPlayerData(1, "RaceName", "human")
SetAiType(1, "wc2-land-attack")
SetPlayerData(15, "RaceName", "neutral")

LoadTileModels("scripts/tilesets/summer.lua")

""")
    # tile set defines types of tiles available and associates type number.
    # for example, for the summer.lua tileset,
    # a grassy tile is 80, and a human-closed-wall is 144.

    # write map tiles.
    # tile indexes start at the top left of the map. y increases downward,
    # x increases rightward.
    #
    occupied = []
    for row in range(0,rows):
        for col in range(0,cols):
            if random.random() < 0.0:
                loc = [col,row]
                occupied.append(loc)
                tiletype = 144 # human closed wall
                value = 40
            else:
                tiletype = 80 # grassy
                value = 0
            file.write('SetTile({0}, {1}, {2}, {3})\n'.format(tiletype,row,col,value))


    # place player0's units
    unit_spec = Template('unit = CreateUnit("unit-footman", ${player}, {$col, $row})\n')
    col = 0
    for i in range(0,units0):
        # place units randomly in leftmost 4 columns
        col, row = nextloc(occupied, 4, 35)
        file.write(unit_spec.substitute(player=0, row=row, col=col))

    # place player1's units
    for i in range(0,units1):
        # place units randomly in rightmost 4 columns
        col, row = nextloc(occupied, 4, 35)
        col = 32 + col
        file.write(unit_spec.substitute(player=1, row=row, col=col))

    file.close()
    compress(filename)

def nextloc(occupied, maxX, maxY):
    """get next unoccupied cell within bounds"""
    done = False
    while not done:
        x = random.randint(0,maxX)
        y = random.randint(0,maxY)
        loc = [x,y]
        if not (loc in occupied):
            done = True
    occupied.append(loc)
    return x, y
    
if __name__ == '__main__':
    # each list in params is a set of map parameters.
    # [player0 type, player0 units, player1 type, player1 units]
    # "P" is "person", "C" is "computer"
    #
    params = []
    for i in range(5,21):
        params.append(["P",i,"C",i])
    for i in range(5,21):
        params.append(["P",i,"P",i])
    rows = 36
    cols = 36
    for p in params:
        gen_presentation_script(p[0],p[1],p[2],p[3],rows,cols)
        gen_map_script(p[0],p[1],p[2],p[3],rows,cols)

    print('done!')
    
