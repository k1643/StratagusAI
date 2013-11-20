import os
import glob
import sys
import yaml

# For string formatting see http://docs.python.org/library/string.html#formatstrings
# maps:
# player0: name of player0 for statistics
# player1: name of player0 for statistics
#
config = """
!!orst.stratagusai.config.Config
mapPaths: [{maps}]
maxCycles: 80000
episodes: {episodes}
agentConfigs:
- !!orst.stratagusai.config.ControllerConfig
  controllerClassName: orst.stratagusai.stratplan.mgr.StrategyController
  params: !!map
    stats: {player0}_vs_{player1}
    planner: !!map
      className: orst.stratagusai.stratsim.planner.{planner0}
      {strategy_set0}
      {choice_method0}
      {simReplan0}
      {strategy0}
      player: {player0}
      opponent: {player1}
    tactical: orst.stratagusai.taclp.TacticalManager
    production: orst.stratagusai.prodh.ProductionManager
- !!orst.stratagusai.config.ControllerConfig
  controllerClassName: orst.stratagusai.stratplan.mgr.StrategyController
  params: !!map
    planner: !!map
      className: orst.stratagusai.stratsim.planner.{planner1}
      {strategy_set1}
      {choice_method1}
      {simReplan1}
      {strategy1}
    tactical: orst.stratagusai.taclp.TacticalManager
    production: orst.stratagusai.prodh.ProductionManager
"""

episodes=7
maps = "../../maps/2bases.smp,../../maps/2bases_switched.smp,../../maps/the-right-strategy.smp,../../maps/the-right-strategy_switched.smp"
AIMaps = "../../maps/2bases_PvC.smp,../../maps/2bases_switched_PvC.smp,../../maps/the-right-strategy_PvC.smp,../../maps/the-right-strategy_switched_PvC.smp,"

def write_random_vs_strategy(stratName1):
    return config.format(
        maps= maps,
        episodes=episodes,
        player0= "rand",
        player1= stratName1,
        planner0= "RandomSwitchingPlanner",
        strategy_set0="",
        choice_method0="",
        simReplan0="",
        strategy0="",
        planner1= "GoalDrivenPlanner",
        strategy_set1="",
        choice_method1="",
        simReplan1="",
        strategy1= "strategy: " + stratName1)

def write_strategy_vs_strategy(strategy0, strategy1):
    # strategy vs. self will be played on the map and the map with switched
    # positions, so we need to play 25 episodes on each map to get 50 episodes.
    # s0 vs. s1 plays 50 times on 2bases,
    # s0 vs. s1 plays 50 times on 2bases switched, and the results fill the
    # game matrix for s1 vs. s0.
    # s0 vs. s0 plays 25 times on 2bases and 25 on 2bases switched.  The results
    # are combined to fill game matrix for s0 vs. s0, giving 50 games for the
    # diagonal entry.
    #
    if strategy0 == strategy1:
        e = 25
    else:
        e = 50
    return config.format(
        maps=  maps,
        episodes=e,
        player0= strategy0,
        player1= strategy1,
        planner0= "GoalDrivenPlanner",
        planner1= "GoalDrivenPlanner",
        strategy_set0="",
        choice_method0= "",
        simReplan0="",
        strategy0= "strategy: " + strategy0,
        strategy_set1="",
        choice_method1="",
        simReplan1="",
        strategy1= "strategy: " + strategy1)

def write_strategy_vs_params(strategy0, strategyParams):
    return config.format(
        maps=  maps,
        episodes=episodes,
        player0= strategy0,
        player1= str(strategyParams).replace(", ",'_'),
        planner0= "GoalDrivenPlanner",
        planner1= "GoalDrivenPlanner",
        choice_method0="",
        simReplan0="",
        strategy0= "strategy: " + strategy0,
        choice_method1="",
        simReplan1="",
        strategy1= "strategy_params: " + str(strategyParams))

def write_sw_vs_strategy(swChoice, stratName, strategy_set):
    return config.format(
        maps=  maps,
        episodes=50,
        player0= swChoice,
        player1= stratName,
        planner0= "SwitchingPlanner",
        strategy_set0='strategy_set: "' + strategy_set + '"',
        simReplan0 = "simReplan: true",
        choice_method0= "choice_method: " + swChoice,
        strategy0= "",
        planner1= "GoalDrivenPlanner",
        strategy_set1="",
        choice_method1="",
        simReplan1="",
        strategy1= "strategy: " + stratName)

def write_sw_vs_sw(sw0, sw1, strategy_set):
    return config.format(
        maps=  maps,
        episodes=30,
        player0= sw0,
        player1= sw1,
        planner0= "SwitchingPlanner",
        strategy_set0='strategy_set: "' + strategy_set + '"',
        choice_method0= "choice_method: " + sw0,
        simReplan0="simReplan: true",
        strategy0= "",
        planner1= "SwitchingPlanner",
        strategy_set1='strategy_set: "' + strategy_set + '"',
        simReplan1="simReplan: true",
        strategy1= "",
        choice_method1= "choice_method: " + sw1)

def write_sw_vs_random(sw0,strategy_set):
    return config.format(
        maps=  maps,
        episodes=episodes,
        player0= sw0,
        player1= "random",
        planner0= "SwitchingPlanner",
        strategy_set0='strategy_set: "' + strategy_set + '"',
        choice_method0= "choice_method: " + sw0,
        simReplan0="simReplan: true",
        strategy0= "",
        planner1= "RandomSwitchingPlanner",
        strategy_set1="",
        strategy1= "",
        choice_method1= "",
        simReplan1="")


def write_sw_vs_builtin(sw,strategy_set,simReplan=True):
    return """
!!orst.stratagusai.config.Config
mapPaths: [{maps}]
maxCycles: 80000
episodes: {episodes}
agentConfigs:
- !!orst.stratagusai.config.ControllerConfig
  controllerClassName: orst.stratagusai.stratplan.mgr.StrategyController
  params: !!map
    stats: {sw}_vs_builtin
    planner: !!map
      className: orst.stratagusai.stratsim.planner.SwitchingPlanner
      strategy_set: "{strategy_set}"
      choice_method: {sw}
      simReplan: {simReplan}
      player: {sw}   # for statistics
      opponent: built-in  # for statistics
    tactical: orst.stratagusai.taclp.TacticalManager
    production: orst.stratagusai.prodh.ProductionManager
""".format(maps=AIMaps, 
           episodes=30,
           strategy_set=strategy_set,
           sw=sw,
           simReplan= "true" if simReplan else "false")


def load_strategy_defs(d, strategy_set):
    """get strategy set used in SwithingPlanner."""

    filepath = os.path.join(d, 'sw_strategies_'+ strategy_set + '.yaml')
    f = open(filepath,'rb')
    strat_data = yaml.load(f)
    f.close()
    strs = strat_data[0]['matrix']
    strategies = []
    for s in strs:
        strategies.append(s[0])

    return strategies # return strategy template names

################################################################################
# main
#
################################################################################

for fn in glob.glob('*.yaml'):
    os.remove(fn)

# match strategies in SwitchingPlanner.java.
#
strategy_set = '2012-02-05'
strategies = load_strategy_defs('..',strategy_set)

#strategy_params = [[10,10,5,4,1,0,-0.10,-0.10,-0.10]]             
switching = ["Nash","maximin","monotone"]

# random
for j in range(0, len(strategies)):
    strategy = strategies[j]
    name = "random_vs_" + strategy + ".yaml"
    file = open(name, 'w')
    file.write(write_random_vs_strategy(strategy))
    file.close()

# switching planners vs. fixed strategies
for i in range(len(switching)):
    for j in range(len(strategies)):
        sw = switching[i]
        strategy = strategies[j]
        name = sw + "_vs_" + strategy + ".yaml"
        file = open(name, 'w')
        file.write(write_sw_vs_strategy(sw, strategy, strategy_set))
        file.close()

# fixed strategy vs. strategy
for i in range(len(strategies)):
    s0 = strategies[i]
    for j in range(i+1): # include stratagy vs. self
        s1 = strategies[j]
        name = s0 + "_vs_" + s1 + ".yaml"
        file = open(name, 'w')
        file.write(write_strategy_vs_strategy(s0,s1))
        file.close()

# vs. parameterized strategy
#for s0 in strategies:
#    for s1 in strategy_params:
#        if s0 != s1:
#            name = s0 + "_vs_" + str(s1).replace(', ','_') + ".yaml"
#            file = open(name, 'w')
#            file.write(write_strategy_vs_params(s0,s1))
#            file.close()

# switching vs. built-in
for sw in switching:
    name = sw + "_vs_builtin.yaml"
    file = open(name, 'w')
    file.write(write_sw_vs_builtin(sw, strategy_set)) # Nov 11th strategy set works pretty well.
    file.close()

# switching vs. switching
for i in range(len(switching)):
    sw0 = switching[i]
    for j in range(i):
        sw1 = switching[j]
        name = sw0 + "_vs_" + sw1 + ".yaml"
        file = open(name, 'w')
        file.write(write_sw_vs_sw(sw0, sw1, strategy_set))
        file.close()

# switching vs. random
for sw in switching:
    name = sw + "_vs_rand.yaml"
    file = open(name, 'w')
    file.write(write_sw_vs_random(sw, strategy_set))
    file.close()
