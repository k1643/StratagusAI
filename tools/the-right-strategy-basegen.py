#!/bin/env python
#
# generate a production base.
#

player_config = """
-- player configuration
SetStartView(0, 28, 3)
SetPlayerData(0, "Resources", "gold", 40000)
SetPlayerData(0, "Resources", "wood", 40000)
SetPlayerData(0, "Resources", "oil", 40000)
SetPlayerData(0, "RaceName", "human")
SetAiType(0, "wc2-passive")
SetStartView(1, 50, 53)
SetPlayerData(1, "Resources", "gold", 40000)
SetPlayerData(1, "Resources", "wood", 40000)
SetPlayerData(1, "Resources", "oil", 40000)
SetPlayerData(1, "RaceName", "human")
SetAiType(1, "{0}")
SetPlayerData(15, "RaceName", "neutral")
"""

player_config_switched = """
-- player configuration
SetStartView(0, 50, 53)
SetPlayerData(0, "Resources", "gold", 40000)
SetPlayerData(0, "Resources", "wood", 40000)
SetPlayerData(0, "Resources", "oil", 40000)
SetPlayerData(0, "RaceName", "human")
SetAiType(0, "wc2-passive")
SetStartView(1, 28, 3)
SetPlayerData(1, "Resources", "gold", 40000)
SetPlayerData(1, "Resources", "wood", 40000)
SetPlayerData(1, "Resources", "oil", 40000)
SetPlayerData(1, "RaceName", "human")
SetAiType(1, "{0}")
SetPlayerData(15, "RaceName", "neutral")
"""

def gen_base(playerId,pos):
    th = pos[0] # townhall
    ml = pos[1] # mill
    bk = pos[2] # barracks
    bk2 = pos[3] # barracks2
    gp = pos[4] # farm group
    gp2 = pos[5] # farm group 2
    ps = pos[6] # peasant
    base = """
unit = CreateUnit("unit-town-hall", {0}, {{{1}, {2}}})
unit = CreateUnit("unit-elven-lumber-mill", {0}, {{{3}, {4}}})
unit = CreateUnit("unit-human-barracks", {0}, {{{5}, {6}}})
unit = CreateUnit("unit-human-barracks", {0}, {{{7}, {8}}})
unit = CreateUnit("unit-peasant", {0}, {{{7}, {8}}})
""".format(playerId,th[0],th[1],ml[0],ml[1],bk[0],bk[1],bk2[0],bk2[1],ps[0],ps[1])

    farmgrp = """
unit = CreateUnit("unit-farm", {0}, {{{3}, {1}}})
unit = CreateUnit("unit-farm", {0}, {{{4}, {1}}})
unit = CreateUnit("unit-farm", {0}, {{{5}, {1}}})
unit = CreateUnit("unit-farm", {0}, {{{3}, {2}}})
unit = CreateUnit("unit-farm", {0}, {{{4}, {2}}})
unit = CreateUnit("unit-farm", {0}, {{{5}, {2}}})
""".format(playerId,gp[1],gp[1]+2,gp[0],gp[0]+2,gp[0]+4)
    farmgrp2 = """
unit = CreateUnit("unit-farm", {0}, {{{1}, {2}}})
unit = CreateUnit("unit-farm", {0}, {{{1}, {3}}})
unit = CreateUnit("unit-farm", {0}, {{{4}, {2}}})
unit = CreateUnit("unit-farm", {0}, {{{4}, {3}}})
""".format(playerId,gp2[0],gp2[1],gp2[1]+2,gp2[0]+2)
    return base + farmgrp + farmgrp2

def write_map(filename,switched,AI1):
    out = open(filename,'wb')
    tiles = open('the-right-strategy-game-tiles.txt','rb')
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
    out.write(gen_base(top_player,tp)) # playerId, positions, farmgroup horizontal
    # bases in bottom half of map
    out.write(gen_base(bottom_player,bt))

    out.close()

# building positions. townhall,mill,barracks,barracks2,farmgroup, farmgroup2,peasant
tp = [(32,11),(36,3),(31,7),(23,6),(23,0),(37,8),(30,11)] # top
bt = [(35,54),(40,55),(29,57),(34,50),(27,51),(44,53),(38,52)] # bottom

################################################################################
#
# main
#
################################################################################
if __name__ == '__main__':
    #
    # bases
    #
    write_map('../maps/the-right-strategy.sms',False,'wc2-passive')
    write_map('../maps/the-right-strategy_PvC.sms',False,'wc2-land-attack')
    #
    # switched bases
    #
    write_map('../maps/the-right-strategy_switched.sms',True,'wc2-passive')
    write_map('../maps/the-right-strategy_switched_PvC.sms',True,'wc2-land-attack')
    
