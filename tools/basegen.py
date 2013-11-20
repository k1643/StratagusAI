#!/bin/env pythonp
#
# generate a production base.
#

player_config = """
-- player configuration
SetStartView(0, 2, 7)
SetPlayerData(0, "Resources", "wood", 40000)
SetPlayerData(0, "Resources", "gold", 40000)
SetPlayerData(0, "Resources", "oil",  40000)
SetPlayerData(0, "RaceName", "human")
SetAiType(0, "wc2-passive")
SetStartView(1, 9, 51)
SetPlayerData(1, "Resources", "wood", 40000)
SetPlayerData(1, "Resources", "gold", 40000)
SetPlayerData(1, "Resources", "oil",  40000)
SetPlayerData(1, "RaceName", "human")
SetAiType(1, "{0}") -- opponent AI
SetStartView(15, 0, 0)
SetPlayerData(15, "Resources", "wood", 0)
SetPlayerData(15, "Resources", "gold", 0)
SetPlayerData(15, "Resources", "oil", 0)
SetPlayerData(15, "RaceName", "neutral")
SetAiType(15, "wc2-passive")
"""

player_config_switched = """
-- player configuration
SetStartView(0, 9, 51)
SetPlayerData(0, "Resources", "wood", 40000)
SetPlayerData(0, "Resources", "gold", 40000)
SetPlayerData(0, "Resources", "oil",  40000)
SetPlayerData(0, "RaceName", "human")
SetAiType(0, "wc2-passive")
SetStartView(1, 2, 7)
SetPlayerData(1, "Resources", "wood", 40000)
SetPlayerData(1, "Resources", "gold", 40000)
SetPlayerData(1, "Resources", "oil",  40000)
SetPlayerData(1, "RaceName", "human")
SetAiType(1, "{0}") -- opponent AI
SetStartView(15, 0, 0)
SetPlayerData(15, "Resources", "wood", 0)
SetPlayerData(15, "Resources", "gold", 0)
SetPlayerData(15, "Resources", "oil", 0)
SetPlayerData(15, "RaceName", "neutral")
SetAiType(15, "wc2-passive")
"""

def gen_base(playerId,pos,groupHorizonal=False):
    th = pos[0] # townhall
    ml = pos[1] # mill
    bk = pos[2] # barracks
    gp = pos[3]
    fm = pos[4]
    ps = pos[5] # peasant
    base = """
unit = CreateUnit("unit-town-hall", {0}, {{{1}, {2}}})
unit = CreateUnit("unit-elven-lumber-mill", {0}, {{{3}, {4}}})
unit = CreateUnit("unit-human-barracks", {0}, {{{5}, {6}}})
unit = CreateUnit("unit-farm", {0}, {{{7}, {8}}})
unit = CreateUnit("unit-peasant", {0}, {{{9}, {10}}})
""".format(playerId,th[0],th[1],ml[0],ml[1],bk[0],bk[1],fm[0],fm[1],ps[0],ps[1])
    if groupHorizonal:
        farmgrp = """
unit = CreateUnit("unit-farm", {0}, {{{3}, {1}}})
unit = CreateUnit("unit-farm", {0}, {{{4}, {1}}})
unit = CreateUnit("unit-farm", {0}, {{{5}, {1}}})
unit = CreateUnit("unit-farm", {0}, {{{3}, {2}}})
unit = CreateUnit("unit-farm", {0}, {{{4}, {2}}})
unit = CreateUnit("unit-farm", {0}, {{{5}, {2}}})
        """.format(playerId,gp[1],gp[1]+2,gp[0],gp[0]+2,gp[0]+4)
    else:
        farmgrp = """
unit = CreateUnit("unit-farm", {0}, {{{1}, {3}}})
unit = CreateUnit("unit-farm", {0}, {{{1}, {4}}})
unit = CreateUnit("unit-farm", {0}, {{{1}, {5}}})
unit = CreateUnit("unit-farm", {0}, {{{2}, {3}}})
unit = CreateUnit("unit-farm", {0}, {{{2}, {4}}})
unit = CreateUnit("unit-farm", {0}, {{{2}, {5}}})
        """.format(playerId,gp[0],gp[0]+2,gp[1],gp[1]+2,gp[1]+4)
    return base + farmgrp

def write_map(filename,switched,AI1):
    out = open(filename,'wb')
    tiles = open('one-way-game-tiles.txt','rb')
    if switched:
        out.write(player_config_switched.format(AI1))
    else:
        out.write(player_config.format(AI1))
    for line in tiles.readlines():
        out.write(line)
    tiles.close()
    top_player = 0
    bottom_player = 1
    if switched:
        top_player = 1
        bottom_player = 0
    # bases in top half of map
    out.write(gen_base(top_player,tl)) # playerId, positions, farmgroup horizontal
    out.write(gen_base(top_player,tr,True))
    # bases in bottom half of map
    out.write(gen_base(bottom_player,bl))
    out.write(gen_base(bottom_player,br))

    out.close()

# building positions. townhall,mill,barracks,farmgroup, farm, peasant
tl = [(1,6),(5,6),(5,9),(10,10),(16,16),(1,5)] # top left
tr = [(55,7),(52,4),(59,13),(43,8),(60,18),(59,10)] # top right  farmgroup is horizontal
bl = [(2,46),(6,47),(6,44),(10,45),(16,42),(5,51)] # bottom left
br = [(53,49),(50,52),(50,49),(44,50),(47,44),(57,50)] # bottom right


################################################################################
#
# main
#
################################################################################
if __name__ == '__main__':
    #
    # bases
    #
    write_map('../maps/2bases.sms',False,'wc2-passive')
    write_map('../maps/2bases_PvC.sms',False,'wc2-land-attack')
    #
    # switched bases
    #
    write_map('../maps/2bases_switched.sms',True,'wc2-passive')
    write_map('../maps/2bases_switched_PvC.sms',True,'wc2-land-attack')
    
