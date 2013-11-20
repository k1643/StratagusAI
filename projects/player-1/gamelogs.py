import os.path
import csv
import datetime
import glob
import math
import matplotlib.pyplot as plt
import numpy as np
import os
import pickle
import random
import scipy
import scipy.stats  # confidence intervals
import sqlite3
import sys
import yaml

# statistics CSV column names and indexes.
cols = {
    "event":0,            # plan or end (of game)
    "player ID":1,        # the number of player being evaluated
    "player":2,           # type of player being evaluated
    "strategy":3,         # strategy of player being evaluated
    "simreplan":4,        # is matrix switching player using simulation re-planning?
    "opponent":5,         # type of opponent
    "predicted":6,        # predicted winner
    "predicted diff":7,   #
    "actual":8,           # actual winner
    "diff":9,
    "cycle":10,            # cycle of this event
    "map":11
}

sim_maps = ['2bases-game',
            '2bases_switched',
            'the-right-strategy-game',
            'the-right-strategy-game_switched'
]

# for games against built-in script
script_maps = [
    ['../../maps/2bases_PvC.smp','../../maps/2bases_switched_PvC.smp'],
    ['../../maps/the-right-strategy_PvC.smp','../../maps/the-right-strategy_switched_PvC.smp']
]

# planner vs. planner maps
# same order as sim_maps.
planner_maps = [
        '../../maps/2bases.smp',
        '../../maps/2bases_switched.smp',
        '../../maps/the-right-strategy.smp',
        '../../maps/the-right-strategy_switched.smp',
]

mapnames = ['2bases','the-right-strategy']

# planner vs. planner maps
engine_maps = [
    ['../../maps/2bases.smp','../../maps/2bases_switched.smp',],
    ['../../maps/the-right-strategy.smp','../../maps/the-right-strategy_switched.smp']
]

switching = ['Nash','maximin', 'monotone']

#epochs = [10000,20000,40000,80000] # divide game in 4 epochs
#epochs = [6030,12060,18090,24120,30150,36180,42210,48240,54270,60300,66330,72360,78390
epochs = [6030,12060,18090,24120,80000]

def write_table(data,fmt,rowhdr,colhdr,label,caption,filepath,hline=None,bolddiag=False,colspec=None):
    """write data matrix as LaTeX table"""
    today = datetime.date.today()
    tex = open(filepath,'w')
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("\\begin{table}[!ht]\n")
    tex.write("\\centering\n")
    tex.write("\\begin{tabular}")
    tex.write("{")
    if colspec:
        tex.write(colspec)
    else:
        tex.write("l |")
        for j in range(len(colhdr)):
            tex.write(" r ") # assume numbers in cells
    tex.write("}\n")
    # column header
    for c in colhdr:
        tex.write(" & " + c)
    tex.write("\\cr\n")
    tex.write("\\hline\n")
    for i in range(len(rowhdr)):
        tex.write(rowhdr[i])
        for j in range(len(colhdr)):
            x = data[i][j]
            tex.write(" & ")
            if bolddiag and i==j:
                tex.write("\\textbf{")
            if x:
                tex.write(fmt(x))
            elif x == 0:
                tex.write("0")
            if bolddiag and i==j:
                tex.write("}")
        tex.write("\\cr\n")
        if hline == i:
            tex.write("\\hline\n")
    tex.write("\\end{tabular}\n")
    tex.write("\\caption{" + caption + "}\n")
    tex.write("\\label{" + label + "}\n")
    tex.write("\\end{table}\n")
    tex.close()

def print_table(data,fmt,rowhdr,colhdr,caption):
    """print data matrix to console"""
    colwidth = max([len(c) for c in rowhdr])
    colfmt = "{0:"+str(colwidth)+"}"
    for c in colhdr:
        print colfmt.format(c),
    print
    for i in range(len(rowhdr)):
        print colfmt.format(rowhdr[i]),
        for j in range(len(colhdr)):
            x = data[i][j]
            if x:
                print fmt(x),
            elif x == 0:
                print "0",
        print
    print caption


def max_index(data):
    """get indexes of max values in data"""
    m = max(data)
    return [i for i,v in enumerate(data) if v==m]

def count_wins(v):
    """count wins in score sequence"""
    return reduce(lambda v1,v2: v1+1 if v2 > 0 else v1,v,0)

def max_star(data):
    """get list with '*' where max value is in data"""
    m = max(data)
    return ['*' if v == m else ' ' for v in data]

def normal_approx_interval(p,n,bound):
    """get 95% confidence interval around sample success rate sp assuming n bernoulli trials, normal distribution"""
    # for a 95% confidence level the error (\alpha) is 5%,
    # so 1- \alpha /2=0.975 and z_{1- \alpha /2}=1.96.
    z = 1.96 # z=1.0 for 85%, z=1.96 for 95%
    n = float(n)
    if bound == 'upper':
        return p + z*math.sqrt(p*(1-p)/n)
    elif bound == 'lower':
        return p - z*math.sqrt(p*(1-p)/n)
    else:
        raise Exception("unknown bound " + bound)

def wilson_score_interval(p,n,bound):
    """get 95% confidence interval around sample success rate sp assuming n bernoulli trials"""
    # for a 95% confidence level the error (\alpha) is 5%,
    # so 1- \alpha /2=0.975 and z_{1- \alpha /2}=1.96.
    z = 1.96 # z=1.0 for 85%, z=1.96 for 95%
    n = float(n)
    #return z*math.sqrt(sp*(1-sp)/float(n))
    #
    # Wilson score interval:
    #
    #       z^2                 p(1-p)     z^2
    # p +  ----  (+-) z * sqrt( ------ + ------ )
    #       2n                       n       4n^2
    # ----------------------------------------------
    #                   z^2
    #              1 + ----
    #                    n
    if bound == 'upper':
        return ((p + z*z/(2*n) + z * math.sqrt((p*(1-p)+z*z/(4*n))/n))/(1+z*z/n))
    elif bound == 'lower':
        return ((p + z*z/(2*n) - z * math.sqrt((p*(1-p)+z*z/(4*n))/n))/(1+z*z/n))
    else:
        raise Exception("unknown bound " + bound)


def bernoulli_confidence(v,formula='normal'):
    """turn score sequence into bernoulli trials.  return win rate and confidence interval"""
    nWins = count_wins(v)
    n = len(v)
    rate = nWins/float(len(v))
    if formula == 'normal':
        f = normal_approx_interval
    elif formula == 'wilson':
        f = wilson_score_interval
    else:
        raise Exception,"unknown interval formula"+formula
    return [rate, [f(rate,n,'lower'),f(rate,n,'upper')]]

def validate_games(curs,scores_dict,strategies):
    # calculate expected number of games
    stratlist = "(" + reduce(lambda x, y: x+','+y,["'"+s+"'" for s in strategies]) + ")"
    cmd = "select count(*) from event where event='end' and player=? and opponent in " + stratlist
    # fixed vs. fixed strategy
    for player in strategies:
        curs.execute(cmd,(player,))
        c = curs.fetchone()[0]
        print c,"games for",player, "vs. fixed strategy"
    # switching vs. fixed strategy
    for player in switching:
        curs.execute(cmd,(player,))
        c = curs.fetchone()[0]
        print c,"games for",player, "vs. fixed strategy"
    # switching vs. switching
    swlist = "(" + reduce(lambda x, y: x+','+y,["'"+s+"'" for s in switching]) + ")"
    curs.execute("select count(*) from event where event='end' and player in " + swlist + " and opponent in " + swlist)
    print curs.fetchone()[0],"switching vs. switching episodes"
    # switching vs. built-in
    curs.execute("select count(*) from event where event='end' and player in " + swlist + " and opponent = 'built-in'")
    print curs.fetchone()[0],"switching vs. built-in episodes"
    # total
    curs.execute("select count(*) from event where event='end'")
    print curs.fetchone()[0],"total episodes"

    # validate scores dict.
    total = 0
    counts = [0,0,0,0]
    for k,v in scores_dict.iteritems():
        c = len(v)
        if k[0] in strategies and k[1] in strategies:
            counts[0] += c
        elif (k[0] in switching and k[1] in strategies) or (k[1] in switching and k[0] in strategies):
            counts[1] += c
        elif k[0] in switching and k[1] in switching:
            counts[2] += c
        elif k[0] == 'built-in' or k[1] == 'built-in':
            counts[3] += c
        else:
            print "no category for", k
        total += c
    print "scores dictionary"
    print total,"episodes"
    print counts[0], "strategy vs. strategy episodes"
    print counts[1], "switching vs. strategy episodes"
    print counts[2], "switching vs. switching episodes"
    print counts[3], "switching vs. built-in"

def load_strategy_defs(d, strategy_set):
    print "load_strategy_defs()"

    filepath = os.path.join(d, 'sw_strategies_'+ strategy_set + '.yaml')
    f = open(filepath,'rb')
    strat_data = yaml.load(f)
    f.close()
    strs = strat_data[0]['matrix']
    names = []
    for s in strs:
        names.append(s[0])
    return names

def write_strategy_defs(d, strategy_set):
    filepath = os.path.join(d, 'sw_strategies_'+ strategy_set + '.yaml')
    f = open(filepath,'rb')
    strat_data = yaml.load(f)
    f.close()
    strs = strat_data[0]['matrix']
    for i in range(len(strs)):
        strs[i] = strs[i][1:]

    # write TEX strategy definitions
    fmt = lambda x: str(x)
    rowhdr = [str(j) + ". " + strategies[j].replace('_',' ') for j in range(len(strategies))]
    colhdr = strat_data[0]['colhdr'][1:]
    caption = strat_data[0]['caption']
    label = strat_data[0]['label']
    outfile = os.path.join(d, 'sw_strategies_'+strategy_set+'.tex')
    write_table(strs,fmt,rowhdr,colhdr,label,caption,outfile)

    return strategies # return strategy template names

class Medians:
    """calculate medians and confidence intervals"""
    def __init__(self,scores,strategies,threshold=.95):
        # strategy vs. strategy table of ConfidenceIntervals indexed by mapname
        self.s_v_s_intervals = {}
        # confidence interval for maximin of strategy vs. strategy table
        # indexed by mappath
        self.s_v_s_maximin_interval = {}
        #
        self.sw_v_s_intervals = {}
        # switching planner vs. fixed strategy tables.
        self.sw_v_s_min_intervals = {} # compare min of switching vs. strategy to maximin

        # build tables.
        #
        self.strategies = strategies
        # load median data
        #
        # load fixed strategy vs. fixed strategy games
        for mapname in mapnames: # ['2bases','the-right-strategy']
            table = [[None for player in strategies] for opponent in strategies]
            interval_table = [[None for player in strategies] for opponent in strategies]
            for i in range(len(strategies)):
                opponent = strategies[i]
                for j in range(len(strategies)):
                    player = strategies[j]
                    v = get_scores(player,opponent,mapname,scores)
                    if len(v) > 0:
                        interval = get_confidence_interval(v,threshold)
                        interval.player = player
                        interval.opponent = opponent
                        table[i][j] = interval.median
                        interval_table[i][j] = interval
            self.s_v_s_intervals[mapname] = interval_table
            # get confidence interval around maximin
            mins = np.min(table,axis=0) # column mins
            mins_indexes = np.argmin(table,axis=0) # row indexes
            maximin_col = np.argmax(mins)
            for i in mins_indexes:
                if table[i][maximin_col] == mins[maximin_col]:
                    self.s_v_s_maximin_interval[mapname] = interval_table[i][maximin_col]
            assert self.s_v_s_maximin_interval[mapname] and self.s_v_s_maximin_interval[mapname].median == mins[maximin_col]

        # load switching planner vs. fixed strategy games
        for mapname in mapnames:
            self.sw_v_s_min_intervals[mapname] = {}
            interval_table = [[None for player in switching] for opponent in strategies]
            for j in range(len(switching)):
                player = switching[j]
                min_interval = None
                for i in range(len(strategies)):
                    opponent = strategies[i]
                    v = get_scores(player,opponent,mapname,scores)
                    if len(v) > 0:
                        interval = get_confidence_interval(v,threshold)
                        interval.player = player
                        interval.opponent = opponent
                        interval_table[i][j] = interval
                        if (not min_interval) or min_interval.median > interval.median:
                            min_interval = interval

                # get confidence interval around min
                assert min_interval, "no minimum found for " + player
                self.sw_v_s_min_intervals[mapname][player] = min_interval
            self.sw_v_s_intervals[mapname] = interval_table
            
class Means:
    """calculate means"""
    def __init__(self,scores,strategies):
        # strategy vs. strategy table of ConfidenceIntervals indexed by mappath
        self.s_v_s_means = {}
        # maximin value and strategy pair for maximin of strategy vs. strategy table
        # indexed by mappath
        self.s_v_s_maximin_pair = {} #
        # switching planner vs. fixed strategy tables.
        self.sw_v_s_min = {} # compare min of switching vs. strategy to maximin

        # build tables.
        #
        self.strategies = strategies
        # load mean data
        #
        # load fixed strategy vs. fixed strategy games
        for mapname in mapnames:
            table = [[None for player in strategies] for opponent in strategies]
            for i in range(len(strategies)):
                opponent = strategies[i]
                for j in range(len(strategies)):
                    player = strategies[j]
                    table[i][j] = get_mean(player,opponent,mapname,scores)
            self.s_v_s_means[mapname] = table
            # get maximin
            mins = np.min(table,axis=0) # column mins
            mins_indexes = np.argmin(table,axis=0) # row indexes
            maximin_col = np.argmax(mins)
            for i in mins_indexes:
                # if row i has maximin value in column maximin_col
                if table[i][maximin_col] == mins[maximin_col]:
                    maximin_row = i
                    self.s_v_s_maximin_pair[mapname] = (table[i][maximin_col],strategies[maximin_col],strategies[maximin_row])
            assert self.s_v_s_maximin_pair[mapname] and self.s_v_s_maximin_pair[mapname][0] == mins[maximin_col]

        # load switching planner vs. fixed strategy games
        for mapname in mapnames:
            self.sw_v_s_min[mapname] = {}
            for j in range(len(switching)):
                player = switching[j]
                min_pair = None
                for i in range(len(strategies)):
                    opponent = strategies[i]
                    v = get_mean(player,opponent,mapname,scores)
                    if (not min_pair) or min_pair[0] > v:
                        min_pair = (v,player,opponent)

                # get confidence interval around min
                assert min_pair, "no minimum found for " + player
                self.sw_v_s_min[mapname][player] = min_pair

def show_maximin_compare_errorbars(d,medians):
    print "show_maximin_compare_errorbars()"
    x = [j+1 for j in range(len(switching)+1)]
    xticklabels = ["fixed"]
    xticklabels.extend(switching)
    for mapname in mapnames:
        mins = []       # minimum of medians
        upper_conf = [] # difference between upper_confidence and median
        lower_conf = []
        # get fix strategy maximin of medians
        conf = medians.s_v_s_maximin_interval[mapname]
        upper_conf.append(conf.interval[1] - conf.median) # upper range
        mins.append(conf.median)
        lower_conf.append(conf.median - conf.interval[0]) # lower range
        # get switching planner mins of medians
        for j in range(len(switching)):
            player = switching[j]
            conf = medians.sw_v_s_min_intervals[mapname][player]
            upper_conf.append(conf.interval[1] - conf.median) # upper range
            mins.append(conf.median)
            lower_conf.append(conf.median - conf.interval[0]) # lower range
        y = mins

        plt.figure()
        plt.xticks(x, xticklabels)
        plt.xlabel('Switching Planners')
        plt.ylabel('Score')
        plt.axvline(x=1.5, color='gray',linestyle='--') #axvline(x=0, ymin=0, ymax=1, **kwargs)
        plt.xlim( (.5, len(x)+.5) ) # show results at 1,...,len(switching)
        plt.errorbar(x, y, yerr=[lower_conf,upper_conf], fmt='bs')
        #plt.show()
        fname = os.path.join(d,"maxmin_compare_"+mapname+".png")
        plt.savefig(fname, format='png') # png, pdf, ps, eps and svg.

def write_maximin_compare(d,medians):
    print "write_maximin_compare()"
    rowhdr = []
    for mapname in mapnames:
        rowhdr.append(mapname)
    colhdr = ["Fixed"]
    for sw in switching:
        colhdr.append("\\texttt{"+sw+"}")
    colsubhdr = ["Upper","Median","Lower"]
    #colsubhdr = ["NA","Mean","NA"]

    data = [[None for j in range(len(switching)+1)] for i in range(len(mapnames)*3)]
    #stratgy vs. strategy maximin value
    for i in range(len(mapnames)):
        mapname = mapnames[i]
        row = i*3
        conf = medians.s_v_s_maximin_interval[mapname]
        #print "  maximin at",conf.player,"vs.",conf.opponent,conf
        data[row][0]   = conf.interval[1]
        data[row+1][0] = conf.median
        #data[row+1][0] = means.s_v_s_maximin_pair[mapname][0]
        data[row+2][0] = conf.interval[0]
    # switching player vs. strategy minimum value
        for j in range(len(switching)):
            player = switching[j]
            conf = medians.sw_v_s_min_intervals[mapname][player]
            #print "  min median at",conf.player,"vs.",conf.opponent,conf
            data[row][j+1] = conf.interval[1] # upper range
            data[row+1][j+1] = conf.median
            #data[row+1][j+1] = means.sw_v_s_min[mapname][player][0]
            data[row+2][j+1] = conf.interval[0] # lower range

    today = datetime.date.today()
    filepath = os.path.join(d,"maximin_compare.tex")
    tex = open(filepath,'w')
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("\\begin{table}[!ht]\n")
    tex.write("\\centering\n")
    tex.write("\\begin{tabular}{l l | ")
    for j in range(len(colhdr)):
        tex.write(" r ") # assume numbers in cells
    tex.write("}\n")
    # column header
    tex.write(" & ")
    for c in colhdr:
        tex.write(" & " + c)
    tex.write("\\cr\n")
    tex.write("\\hline\n")
    # write Upper,Median,Lower on first map
    for i in range(len(colsubhdr)):
        if i == 0:
            tex.write("\\texttt{{{0}}} & {1}".format(mapnames[0],colsubhdr[0]))
        else:
            tex.write(" & {0}".format(colsubhdr[i]))
        for j in range(len(colhdr)):
            x = data[i][j]
            tex.write(" & {0:.0f}".format(x))
            #tex.write(" & " + str(x))
        tex.write("\\cr\n")
    tex.write("\\hline\n")
    for i in range(len(colsubhdr)):
        if i == 0:
            tex.write("\\texttt{{{0}}} & {1}".format(mapnames[1],colsubhdr[0]))
        else:
            tex.write(" & {0}".format(colsubhdr[i]))
        for j in range(len(colhdr)):
            x = data[3+i][j]
            tex.write(" & {0:.0f}".format(x))
            #tex.write(" & " + str(x))
        tex.write("\\cr\n")
    tex.write("\\hline\n")
    tex.write("\end{tabular}\n")
    tex.write("\\caption{Fixed Strategy Maximin and Switching Planner Minimum Intervals}\n")
    tex.write("\\label{table:maximin_and_minimums}\n")
    tex.write("\\end{table}\n")
    tex.close()

def plot_rate_v_mean(d,scores):
    """show relationship between win rate and mean score"""
    # scores = get_strat_v_strat_scores(curs,strategies)
    means = {}
    rates = {}
    for k,v in scores.iteritems():
        if len(v) == 0:
            continue
        means[k] = np.mean(v)
        nWins = 0
        nGames = len(v)
        for score in v:
            if score > 0:
                nWins += 1
        rates[k] = nWins/float(nGames)

    for mapname in mapnames:
        keys = rates.keys() # get [player,opponent,mappath]
        x = [rates[k] for k in filter(lambda t: t[2] == mapname, keys)]
        y = [means[k] for k in filter(lambda t: t[2] == mapname, keys)]
        plt.figure() # new graph
        plt.xlim( (0, 1) ) # 0-100%
        plt.xlabel('win rate')
        plt.ylabel('mean score')
        plt.scatter(x,y)
        plt.show()
        fname = os.path.join(d,"rate_v_mean_"+mapname+".png")
        #plt.savefig(fname, format='png') # png, pdf, ps, eps and svg.

def strat_vs_strat_rate(d,scores_dict,strategies):
    """write strategy vs. strategy win rate table."""
    print "strat_vs_strat_rate()"

    # setup Latex table
    fmt = lambda x: x if x.__class__ == str else "{0:.0f}\%".format(x*100) # formatter.
    rowhdr = [str(j) + "." for j in range(len(strategies))]
    hline = None
    colhdr = [str(i) + "." for i in range(len(strategies))]

    for mapname in mapnames:
        table = [[None for p in strategies] for o in strategies]
        for i in range(len(strategies)):
            o = strategies[i] # opponent
            for j in range(len(strategies)):
                p = strategies[j] # player
                v = get_scores(p,o,mapname,scores_dict)
                nWins = count_wins(v)
                table[i][j] = nWins/float(len(v))
        caption = 'Strategy Win Rate on \\texttt{' + mapname.replace('_',' ') + '}'
        label = 'engine_rate_' + mapname
        outfile = os.path.join(d, 'engine_rate_'+mapname+'.tex')
        write_table(table,fmt,rowhdr,colhdr,label,caption,outfile,hline,bolddiag=True)

def strat_vs_strat_score_db(d,curs,strategies,summary='median'):
    """Debugging.  write strategy vs. strategy score table from database"""
    print "strat_vs_strat_score_db()"

    def fmt(x):
        if not x:
            return "      "
        elif x.__class__ == str:
            return "{0:6}".format(x)
        else:
            return "{0:6.0f}".format(x)
    rowhdr = [str(i) + "." for i in range(len(strategies))]
    rowhdr.append("min")
    rowhdr.append("mxmn")
    cmd = "select diff from event where event='end' and player=? and opponent=? and map=?"
    for mappaths in engine_maps:
        path,mapname = os.path.split(mappaths[0])
        mapname = mapname.replace('.smp','')
        table = [[0 for p in strategies] for o in strategies]
        for i in range(len(strategies)):
            p = strategies[i]
            for j in range(i+1):
                o = strategies[j]
                curs.execute(cmd,(p,o,mappaths[0],)) # from north position
                n_scores = [row[0] for row in curs.fetchall()]
                scores = n_scores
                curs.execute(cmd,(p,o,mappaths[1],)) # from south position
                s_scores = [row[0] for row in curs.fetchall()]
                scores.extend(s_scores)
                if summary == 'median':
                    stats = np.median
                elif summary == 'mean':
                    stats = np.mean
                else:
                    raise Exception, "unknown summary function", summary
                if i == j:                                    
                    table[j][i] = stats(scores) # transpose for point of view of column player
                else:
                    table[j][i] = stats(scores)
                    table[i][j] = -stats(scores)
        mins = np.min(table,axis=0)
        table.append(mins)
        table.append(max_star(mins)) # mark the maximin columns)
        print mapname
        for i in range(len(table)):
            print "{0:4}".format(rowhdr[i]),
            row = table[i]
            for cell in row:
                print fmt(cell),
            print

def strat_vs_strat_score(d,scores_dict,strategies):
    """write strategy vs. strategy mean score table."""
    print "strat_vs_strat_score()"

    # setup Latex table
    fmt = lambda x: x if x.__class__ == str else "{0:.0f}".format(x) # formatter.
    rowhdr = [str(j) + "." for j in range(len(strategies))]
    rowhdr.append("min.")
    rowhdr.append("maxmin")
    hline = len(strategies) - 1 # add horizontal line to table
    colhdr = [str(i) + "." for i in range(len(strategies))]

    for mapname in mapnames:
        table = [[None for p in strategies] for o in strategies]
        for i in range(len(strategies)):
            o = strategies[i] # opponent
            for j in range(len(strategies)):
                p = strategies[j] # player
                #v = get_scores(p,o,mapname,scores_dict)
                table[i][j] = get_mean(p,o,mapname,scores_dict)
        mins = np.min(table,axis=0)
        table.append(mins)
        table.append(max_star(mins)) # mark the maximin columns)
        caption = 'Strategy Mean Scores on \\texttt{' + mapname.replace('_',' ') + '}'
        label = 'engine_scores_' + mapname
        outfile = os.path.join(d, 'engine_scores_'+mapname+'.tex')
        write_table(table,fmt,rowhdr,colhdr,label,caption,outfile,hline,bolddiag=True)

def strat_vs_strat_median_score(d,medians,strategies):
    """write strategy vs. strategy median score table."""
    print "strat_vs_strat_median_score()"

    # setup Latex table
    fmt = lambda x: x if x.__class__ == str else "{0:.0f}".format(x) # formatter.
    rowhdr = [str(j) + "." for j in range(len(strategies))]
    rowhdr.append("minimum")
    rowhdr.append("maximin")
    hline = len(strategies) - 1 # add horizontal line to table
    colhdr = [str(i) + "." for i in range(len(strategies))]

    for mapname in mapnames:
        table = [[None for p in strategies] for o in strategies]
        confidence_table = medians.s_v_s_intervals[mapname]
        for i in range(len(strategies)): # opponent i
            for j in range(len(strategies)): # player j
                confidence = confidence_table[i][j]
                table[i][j] = confidence.median
        mins = np.min(table,axis=0)
        table.append(mins)
        table.append(max_star(mins)) # mark the maximin columns)
        caption = 'Strategy Median Scores on \\texttt{' + mapname.replace('_',' ') + '}'
        label = 'engine_median_scores_' + mapname
        outfile = os.path.join(d, 'engine_median_scores_'+mapname+'.tex')
        write_table(table,fmt,rowhdr,colhdr,label,caption,outfile,hline,bolddiag=True)

def sw_vs_strat_scores(d,scores_dict,strategies):
    """write switcher vs. strategy score table."""
    print "sw_vs_strat_scores()"
    for mapname in mapnames:
        sw_vs_strat_map_scores(d,scores_dict,strategies,mapname)

def sw_vs_strat_map_scores(d,scores_dict,strategies,mapname):
    """write switcher vs. strategy score table."""
    print "sw_vs_strat_map_scores(" + mapname + ")"
    means_table = [[None for p in switching] for o in strategies]
    rates_table = [[None for p in switching] for o in strategies]
    for i in range(len(strategies)):
        o = strategies[i] # opponent
        for j in range(len(switching)):
            p = switching[j] # player
            # get averge for games from both positions on map
            means_table[i][j] = get_mean(p,o,mapname,scores_dict)
            rates_table[i][j] = get_rate(p,o,mapname,scores_dict)
    # add row for mean results
    means = np.mean(means_table,axis=0)
    mins = np.min(means_table,axis=0)
    means_table.append(means)
    means_table.append(mins)
    rates_table.append([None for p in switching])
    rates_table.append([None for p in switching])
    
    write_sw_vs_strat_map_table(d,means_table,rates_table,strategies,mapname)

def write_sw_vs_strat_map_table(d,data,rates,strategies,mapname):
    fmt = lambda x: "{0:.0f}".format(x) # formatter.
    rowhdr = [str(i)+'. \\texttt{'+strategies[i].replace('_',' ')+'}' for i in range(len(strategies))]
    rowhdr.append("mean")
    rowhdr.append("minimum")
    hline = len(strategies) - 1
    colhdr = ['\\texttt{'+s+'}' for s in switching]
    label = 'sw_scores_' + mapname
    caption = 'Switching Planner Mean Scores on \\texttt{' + mapname + '}'
    fn = 'sw_scores_'+mapname+'.tex'
    filepath = os.path.join(d, fn)
    #write_table(table,fmt,rowhdr,colhdr,label,caption,outfile,hline)
    """write data matrix as LaTeX table"""
    today = datetime.date.today()
    tex = open(filepath,'w')
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("\\begin{table}[!ht]\n")
    tex.write("\\centering\n")
    tex.write("\\begin{tabular}")
    tex.write("{")
    tex.write("l |")
    for j in range(len(colhdr)):
        tex.write(" r ") # assume numbers in cells
    tex.write("|")
    for j in range(len(colhdr)):
        tex.write(" r ") # assume numbers in cells
    tex.write("}\n")
    # column header
    tex.write(" & \multicolumn{3}{l}{Mean Scores} & \multicolumn{3}{l}{Win Rates}\\cr\n")
    for c in colhdr:
        tex.write(" & " + c)
    for c in colhdr:
        tex.write(" & " + c)
    tex.write("\\cr\n")
    tex.write("\\hline\n")
    for i in range(len(rowhdr)):
        tex.write(rowhdr[i])
        # score table
        for j in range(len(colhdr)):
            x = data[i][j]
            tex.write(" & ")
            if x:
                tex.write(fmt(x))
            elif x == 0:
                tex.write("0")
        # rate table
        for j in range(len(colhdr)):
            x = rates[i][j]
            tex.write(" & ")
            if x:
                tex.write(fmt(x*100) + "\%")
            elif x == 0:
                tex.write("0")

        tex.write("\\cr\n")
        if hline == i:
            tex.write("\\hline\n")
    tex.write("\\end{tabular}\n")
    tex.write("\\caption{" + caption + "}\n")
    tex.write("\\label{" + label + "}\n")
    tex.write("\\end{table}\n")
    tex.close()

def sw_vs_strat_median_scores(d,medians):
    """write switcher vs. strategy median score table."""
    print "sw_vs_strat_median_scores()"
    for mapname in mapnames:
        sw_vs_strat_median_map_scores(d,medians,mapname)

def sw_vs_strat_median_map_scores(d,medians,mapname):
    """write switcher vs. strategy score table."""
    print "sw_vs_strat_map_scores(" + mapname + ")"
    table = [[None for p in switching] for o in medians.strategies]
    interval_table = medians.sw_v_s_intervals[mapname]
    for i in range(len(medians.strategies)):
        for j in range(len(switching)):
            table[i][j] = interval_table[i][j].median
            
    # add row for min results
    mins = np.min(table,axis=0)
    table.append(mins)
    fmt = lambda x: "{0:.0f}".format(x)
    rowhdr = ['\\texttt{'+s.replace('_',' ')+'}' for s in medians.strategies]
    rowhdr.append("minimum")
    hline = len(medians.strategies) - 1
    colhdr = ['\\texttt{'+s+'}' for s in switching]
    label = 'sw_median_scores_' + mapname
    caption = 'Switching Planner Median Scores on \\texttt{' + mapname + '}'
    fn = 'sw_median_scores_'+mapname+".tex"
    outfile = os.path.join(d, fn)
    write_table(table,fmt,rowhdr,colhdr,label,caption,outfile,hline)

def sw_vs_strat_rates(d,scores_dict,strategies):
    """write switcher vs. strategy win rate table."""
    print "sw_vs_strat_rates()"
    for mapname in mapnames:
        sw_vs_strat_map_rates(d,scores_dict,strategies,mapname)

def sw_vs_strat_map_rates(d,scores_dict,strategies,mapname):
    """write switcher vs. strategy win rate table."""
    print "sw_vs_strat_map_rates(" + mapname + ")"
    table = [[None for p in switching] for o in strategies]
    for i in range(len(strategies)):
        o = strategies[i] # opponent
        for j in range(len(switching)):
            p = switching[j] # player
            v = get_scores(p,o,mapname,scores_dict)
            nWins = count_wins(v)
            table[i][j] = 100*nWins/float(len(v))

    fmt = lambda x: "{0:.0f}\%".format(x) # formatter.
    rowhdr = [str(i)+'. \\texttt{'+strategies[i].replace('_',' ')+'}' for i in range(len(strategies))]
    hline = None
    colhdr = ['\\texttt{'+s+'}' for s in switching]
    label = 'sw_rates_' + mapname
    caption = 'Switching Planner Win Rates on \\texttt{' + mapname + '}'
    fn = 'sw_rates_'+mapname+'.tex'
    outfile = os.path.join(d, fn)
    write_table(table,fmt,rowhdr,colhdr,label,caption,outfile,hline)

def game_duration(d,curs):
    """how many games last thru each period"""
    outfile = os.path.join(d, 'game_duration_barchart.tex')
    tex = open(outfile,'w')
    tex.write("\\begin{figure}[!ht]\n")
    tex.write("\\begin{tikzpicture}\n")
    tex.write("""\\begin{axis}[ybar stacked,
        area legend,
        cycle list={
            % see pgfplots.pdf barcharts and 
            % see pgfmanual.pdf 41 Pattern Library
            % patterns: crosshatch, north east lines, north west lines,...
            {fill=blue},{fill=red},{fill=teal},{fill=gray},{fill=white},{fill=orange},{fill=black},{fill=violet},{pattern color=red,pattern=north east lines},{pattern color=blue,pattern=north west lines},{fill=brown}
        },
        legend style={at={(2,.95)}}
]
    """)
    replans = range(0,80000,6000)
    tex.write("    \\addplot coordinates\n")
    tex.write("        {")
    for t in replans:        
        nGames = curs.execute("select count(*) from event where event='end' and cycle > ?",(t,)).fetchone()[0]
        tex.write(" ({0},{1})".format(t,nGames))
    tex.write("};\n")
    tex.write("    \\legend{")
    for i in range(len(replans)):
        if i > 0:
            tex.write(", ")
        tex.write(str(replans[i]))
    tex.write("}\n")

    tex.write("\\end{axis}\n")
    tex.write("\\end{tikzpicture}\n")
    tex.write("\\caption{Game Durations}\n")
    tex.write("\\label{game_duration}\n")
    tex.write("\\end{figure}\n")
        
def sw_vs_sw(d,scores_dict):
    """write switcher vs. switcher win rate table."""
    print "switcher_vs_switcher()"
    for mapname in mapnames:
        sw_vs_sw_by_map(d,scores_dict,mapname)

def sw_vs_sw_by_map(d,scores_dict,mapname):
    players = switching
    opponents = switching[:] # copy
    opponents.append('built-in')
    counts = [[None for p in players] for o in opponents]
    for i in range(len(opponents)):
        o = opponents[i] # opponent
        for j in range(len(players)):
            p = players[j] # player
            if p != o:
                scores = get_scores(p,o,mapname,scores_dict) # combine scores from N. and S. maps
                nWins = 0
                for score in scores:
                    if score > 0:
                        nWins += 1
                counts[i][j] = nWins/float(len(scores))
    fmt = lambda x: "{0:.0f}\\%".format(100 * x) # formatter.  show as percent.
    rowhdr = [s for s in opponents]
    colhdr = [s for s in players]
    outfile = os.path.join(d, 'sw_vs_sw_win_rate_' + mapname + '.tex')
    label = 'sw_vs_sw_win_rate_' + mapname
    caption = 'Switching vs.~Switching Win Rates on \\texttt{' + mapname + '}'
    write_table(counts,fmt,rowhdr,colhdr,label,caption,outfile)

def switcher_choices(d,curs,strategies):
    print "switcher_choices()"
    counts = [[0 for p in switching] for s in strategies]
    nEvents = [0 for p in switching] # number of planning events for switcher
    inclause = "("
    for i in range(len(strategies)):
        if i > 0:
            inclause += ","
        inclause += "'" + strategies[i] + "'"
    inclause += ")"
    #print inclause
    for j in range(len(switching)):
        p = switching[j]
        cmd = "select count(*) from event where event='plan' and simreplan=1 and player=? and opponent in " + inclause
        c = curs.execute(cmd,(p,)).fetchone()[0]
        nEvents[j] = c

    # for each fixed strategy, for each switching planner
    for i in range(len(strategies)):
        s = strategies[i]
        for j in range(len(switching)):
            if nEvents[j]:
                p = switching[j]
                cmd = "select count(*) from event where event='plan' and simreplan=1 and player=? and strategy=? and opponent in " + inclause
                nUse = curs.execute(cmd,(p,s,)).fetchone()[0]
                counts[i][j] = nUse / float(nEvents[j])

    fmt = lambda x: "{0:.0f}\%".format(100 * x) # formatter.  show as percent.
    colhdr = ['\\texttt{'+s.replace('_',' ')+'}' for s in switching]
    rowhdr = [str(i)+'. \\texttt{'+strategies[i].replace('_',' ')+'}' for i in range(len(strategies))]
    outfile = os.path.join(d, 'switcher_choices.tex')
    write_table(counts,fmt,rowhdr,colhdr,'switcher_choices','Strategy Choices of Switching Planners',outfile)

def switcher_choices_by_epoch(d,curs,strategies):
    print "switcher_choices_by_epoch()"
    table = [[0 for epoch in epochs] for s in strategies]
    inclause = "("
    for i in range(len(strategies)):
        if i > 0:
            inclause += ","
        inclause += "'" + strategies[i] + "'"
    inclause += ")"
    #print inclause
    player = 'maximin'
    for epoch in range(len(epochs)):
        if epoch == 0:
            start = 0
        else:
            start = epochs[epoch-1]
        end = epochs[epoch]
        # total planning events of epoch
        cmd = "select count(*) from event where player=? and simreplan=1 and cycle > ? and cycle <= ? " + \
                " and opponent in " + inclause
        nEvents = curs.execute(cmd,(player,start,end,)).fetchone()[0]

        # for each fixed strategy
        for i in range(len(strategies)):
            s = strategies[i]
            if nEvents:
                cmd = "select count(*) from event where player=? and simreplan=1  and cycle >= ? and cycle < ? " + \
                   " and strategy=? and opponent in " + inclause
                nUsed = curs.execute(cmd,(player,start,end,s,)).fetchone()[0]
                table[i][epoch] = nUsed / float(nEvents)

    fmt = lambda x: "{0:.0f}\%".format(100 * x) # formatter.  show as percent.
    rowhdr = [str(i)+'. \\texttt{'+strategies[i].replace('_',' ')+'}' for i in range(len(strategies))]
    colhdr = ['{:,}'.format(e) for e in epochs]
    caption = '\\texttt{maximin} Choices by Epoch'
    outfile = os.path.join(d, 'maximin_choices_by_epoch.tex')
    write_table(table,fmt,rowhdr,colhdr,'maximin_choices_by_epoch',caption,outfile)

def switcher_choices_by_opponent_map_epoch(d,curs,strategies,player,opponent,mapname):
    print "switcher_choices_by_opponent_map_epoch()"
    i = mapnames.index(mapname)
    mappaths = engine_maps[i]
    table = [[0 for epoch in epochs] for s in strategies]
    for epoch in range(len(epochs)):
        if epoch == 0:
            start = 0
        else:
            start = epochs[epoch-1]
        end = epochs[epoch]

        # for each fixed strategy
        nEvents = 0
        for i in range(len(strategies)):
            s = strategies[i]
            cmd = "select count(*) from event where player=? and simreplan=1 " + \
              " and opponent=? " + \
              " and cycle >= ? and cycle < ? " + \
              " and strategy=? " + \
              " and (map=? or map=?)"
            nUsed = curs.execute(cmd,(player,opponent,start,end,s,mappaths[0],mappaths[1],)).fetchone()[0]
            table[i][epoch] = nUsed
            nEvents += nUsed
        if nEvents:
            for i in range(len(strategies)):
                 table[i][epoch] = table[i][epoch] / float(nEvents)

    fmt = lambda x: "{0:.0f}\%".format(100 * x) # formatter.  show as percent.
    rowhdr = [str(i)+'. \\texttt{'+strategies[i].replace('_',' ')+'}' for i in range(len(strategies))]
    colhdr = ['{:,}'.format(e) for e in epochs]
    caption = '\\texttt{{{0}}} vs.~\\texttt{{{1}}} Choices'.format(player,opponent.replace('_',' '))
    outfile = os.path.join(d, '{0}_v_{1}_{2}_choices_by_epoch.tex'.format(player,opponent,mapname))
    label = '{0}_v_{1}_{2}_choices_by_epoch'.format(player,opponent,mapname)
    write_table(table,fmt,rowhdr,colhdr,label,caption,outfile)

def switcher_choice_sequence(d,curs,sw,opponent):
    """print sequence of strategy choices"""
    cmd = "select event,strategy,predicted_diff,diff,cycle,map from event where player=? and simreplan=1 " + \
                " and opponent=? order by map,game,cycle"
    curs.execute(cmd,(sw,opponent,))
    m = None
    nGames = 0
    for row in curs.fetchall():
        if m != row[5]:
            m = row[5]
            print m
        if row[0] == 'plan':
            print "{0} prediction: {1} at: {2}".format(row[1],row[2],row[4])
        else:
            print row[0],"score",row[3]
            nGames += 1
    print nGames,"games"

def switcher_choices_sim(d,strategies,mapname,filename):
    print "switcher_choices_sim()"
    table = [[0 for epoch in epochs] for s in strategies]
    sim_epochs = [6000,12000,18000,24000,80000]
    player = None
    opponent = None

    file = open(os.path.join(d,filename), 'rb')
    rd = csv.reader(file)
    for row in rd:
        event = row[0]
        if not player:
            player = row[cols["player"]]
            opponent = row[cols["opponent"]]
        else:
            assert player == row[cols["player"]]
            assert opponent == row[cols["opponent"]]
        assert mapname in row[cols["map"]]
        if event == "plan":
            # count number of strategy choices in epoch
            i = strategies.index(row[cols["strategy"]])
            cycle = int(row[cols["cycle"]])
            for epoch in range(len(sim_epochs)):
                if epoch == 0:
                    start = 0
                else:
                    start = sim_epochs[epoch-1]
                end = sim_epochs[epoch]
                if cycle >= start and cycle < end:
                    break
            #print player, "choose strategy",strategies[i],"at cycle",row[cols["cycle"]],"epoch", epoch
            table[i][epoch] += 1
    # normalize
    sums = np.sum(table,axis=0)
    for j in range(len(sums)):
        if sums[j] != 0:
            for i in range(len(table)):
                table[i][j] = table[i][j]/float(sums[j])

    #for i in range(len(table)):
    #    for j in range(len(table[i])):
    #        print "{0:2.0f}\% ".format(100*table[i][j]),
    #    print

    fmt = lambda x: "{0:.0f}\%".format(100 * x) # formatter.  show as percent.
    rowhdr = [str(i)+'. \\texttt{'+strategies[i].replace('_',' ')+'}' for i in range(len(strategies))]
    colhdr = ['{:,}'.format(e) for e in epochs]
    caption = '\\texttt{{{0}}} vs.~\\texttt{{{1}}} Choices on \\texttt{{{2}}} in Simulation'.format(player,opponent.replace('_',' '),mapname)
    outfile = os.path.join(d, '{0}_v_{1}_{2}_sim_choices_by_epoch.tex'.format(player,opponent,mapname))
    label = '{0}_v_{1}_{2}_sim_choices_by_epoch'.format(player,opponent,mapname)
    write_table(table,fmt,rowhdr,colhdr,label,caption,outfile)

def switcher_win_loss_choices(d,curs):
    players = switching
    data = [[0 for p in players]*2 for s in strategies] # two columns (win,lose) for each player
    for j in range(len(players)):
        p = players[j]
        nEvents = curs.execute("select count(*) from event where player=? and event='plan'",(p,)).fetchone()[0]
        if nEvents == 0:
            print "No planning events for player", p
            continue
        # get game IDs of won games.
        for i in range(len(strategies)):
            s = strategies[i]
            n = curs.execute("select count(*) from event where strategy=? and game in (select game from event where player=? and simreplan=1 and actual=0)",(s,p)).fetchone()[0]
            data[i][j*2] = n/float(nEvents)
            # get game IDs of lost games
            n = curs.execute("select count(*) from event where strategy=? and game in (select game from event where player=? and simreplan=1 and actual=1)",(s,p)).fetchone()[0]
            data[i][j*2+1] = n/float(nEvents)

    fmt = lambda x: "{0:.1f}".format(100 * x) # formatter.  show as percent.
    colhdr = players
    colhdr2 = ['Win','Lose','Win','Lose','Win','Lose']
    rowhdr = [s.replace('_',' ') for s in strategies]
    filepath = os.path.join(d, 'switcher_win_choices.tex')
    caption = 'Strategy Choices of Switching Planners in Winning Games (with Re-Planning)'
    label = 'table:switcher_win_choices'
    # copied from write_table.  We need a different version of table header.
    today = datetime.date.today()
    tex = open(filepath,'w')
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("\\begin{table}[!ht]\n")
    tex.write("\\begin{tabular}{l | ")
    for j in range(len(colhdr2)):
        tex.write(" r ") # assume numbers in cells
    tex.write("}\n")
    # column header
    for c in colhdr:
        tex.write(" & \multicolumn{2}{c}{" + c + "}")
    tex.write("\\cr\n")
    for c in colhdr2:
        tex.write(" & " + c)
    tex.write("\\cr\n")
    tex.write("\\hline\n")
    for i in range(len(rowhdr)):
        tex.write(rowhdr[i])
        for j in range(len(colhdr2)):
            x = data[i][j]
            if x:
                tex.write(" & " + fmt(x))
            elif x == 0:
                tex.write(" & 0 ")
            else: # None
                tex.write(" & ")
        tex.write("\\cr\n")
    tex.write("\end{tabular}\n")
    tex.write("\\caption{" + caption + "}\n")
    tex.write("\\label{" + label + "}\n")
    tex.write("\\end{table}\n")
    tex.close()

def switcher_choices_barchart(d,curs,strategies):
    print "switcher_choices_barchart()"
    players = switching
    for p in players:
        for s in ['balanced_7_mass','balanced_9','balanced_9_mass','rush_9']: # strongest strategies.
            tex_choices_barchart(d,curs,p,s,strategies)
        tex_choices_barchart(d,curs,p,'built-in',strategies)
            
def tex_choices_barchart(d,curs, player, opponent,strategies):
    """show choices at each planning event"""
    print "tex_choices_barchart(" + player + "," + opponent + ")"
    label = '{0}_choices_vs_{1}_barchart'.format(player,opponent)
    filepath =  os.path.join(d, label+'.tex')
    tex = open(filepath,'w')
    tex.write("\\begin{figure}[!ht]\n")
    tex.write("\\begin{tikzpicture}\n")
    # need at least 11 bar styles for 11 strategies
    tex.write("""\\begin{axis}[ybar stacked,
        area legend,
        cycle list={
            % see pgfplots.pdf barcharts and 
            % see pgfmanual.pdf 41 Pattern Library
            % patterns: crosshatch, north east lines, north west lines,...
            {fill=blue},{fill=red},{fill=teal},{fill=gray},{fill=white},{fill=orange},{fill=black},{fill=violet},{pattern color=red,pattern=north east lines},{pattern color=blue,pattern=north west lines},{fill=brown}
        },
        legend style={at={(2,.95)}}
]
    """)
    for s in strategies:
        tex.write("    \\addplot coordinates\n")
        tex.write("        {")
        for epoch in range(len(epochs)):
            if epoch == 0:
                start = 0
            else:
                start = epochs[epoch-1]
            end = epochs[epoch]
            c = curs.execute("select count(*) from event where player=? and opponent=? and strategy=? and simreplan=1 and cycle >= ? and cycle < ?",
                             (player,opponent,s,start,end,)).fetchone()[0]
            tex.write(" ({0},{1})".format(start,c))
        tex.write("};\n")
    tex.write("    \\legend{")
    for i in range(len(strategies)):
        if i > 0:
            tex.write(", ")
        tex.write(strategies[i].replace('_',' '))
    tex.write("}\n")

    tex.write("\\end{axis}\n")
    tex.write("\\end{tikzpicture}\n")
    caption = player + " Choices vs. " + opponent.replace("_",' ')
    tex.write("\\caption{" + caption + "}\n")
    tex.write("\\label{"+ label + "}\n")
    tex.write("\\end{figure}\n")

    tex.close()

def get_bias(d, curs, strategies):
    """get avg. scores for fixed strategy vs. self games."""
    print "get_bias()"
    colhdr = []
    rowhdr = [str(i)+". "+strategies[i].replace('_',' ') for i in range(len(strategies))]
    table = []
    for mappaths in engine_maps:
        for mappath in mappaths:
            path,mapname = os.path.split(mappath)
            mapname = mapname.replace('_',' ')
            mapname = mapname.replace('.smp','')
            if 'switched' in mapname:
                mapname = mapname.replace('switched','S.')
            else:
                mapname = mapname + " N."
            colhdr.append(mapname)
            bias = get_bias_by_map(curs, strategies, mappath)
            table.append(bias)
            #print "avg. ", np.mean(bias)

    table = np.transpose(table)
    fmt = lambda x: "{0:.0f}".format(x) # formatter.
    hline = None
    label = 'map_bias'
    caption = 'Bias by Map and Position'
    filename = os.path.join(d, 'map_bias.tex')
    write_table(table,fmt,rowhdr,colhdr,label,caption,filename,hline)

def get_bias_by_map(curs,strategies,map):
    """get avg. scores for fixed strategy vs. self games."""
    cmd = "select diff from event where event='end' and player=? and opponent=player and map=?"
    bias = [None for s in strategies]
    for i in range(len(strategies)):
        curs.execute(cmd,(strategies[i],map,))
        scores = [row[0] for row in curs.fetchall()]
        bias[i] = np.median(scores)
    return bias

class ConfidenceInterval:
    def __init__(self,median,confidence,interval):
        self.player = None
        self.opponent = None
        self.median = median
        self.confidence = confidence
        self.interval = interval

    def __str__(self):
        return "{0} {1:.4f} [{2},{3}]".format(self.median,self.confidence,self.interval[0],self.interval[1])

def get_confidence_interval(x,threshold=.95):
    """get tightest interval arount median that exceeds .95 confidence."""
    x = x[:] # get a copy and sort it.
    x.sort()
    n = len(x)
    median = np.median(x)
    cs = []
    for k in range(int(math.floor(n/2.0))):
        c = 1 - (2 * scipy.stats.binom.cdf(k,n,0.5)) # binomial CDF of k successes in n samples
        if c < .999 and c > threshold:
            cs.append(ConfidenceInterval(median,c,[x[k],x[-k-1]]))
    if len(cs) > 0:
        return cs[-1]
    else:
        raise Exception("no confidence interval meets requirements")

def get_bernoulli_confidence_intervals(scores,episodes):
    intervals = []
    for n in episodes:
        player_scores = random.sample(scores, n)
        intervals.append(bernoulli_confidence(player_scores))
    return intervals

def compare_sim_engine(d, scores_dict, strategy_set,strategies):
    """compare strategy performace in simulation to performance in engine"""
    print "compare_sim_engine()"
    for mapname in mapnames:
        compare_sim_engine_by_map(d,scores_dict,strategy_set,strategies,mapname)

def compare_sim_engine_by_map(d, scores_dict, strategy_set,strategies,mapname):
    # get simulation scores
    fn = "sim_scores_{0}_{1}-game.yaml".format(strategy_set, mapname)
    filepath = os.path.join(d, fn)
    f = open(filepath,'rb')
    simdata = yaml.load(f)
    f.close()
    sv = simdata[0]['matrix']
    s = np.mean(sv,axis=0) # mean of columns
    sim_coords = ""
    for j in range(len(s)):
        sim_coords += "({0},{1}) ".format(j, s[j])

    # get mean engine scores
    coords = ""
    for j in range(len(strategies)):
        player = strategies[j]
        v = []
        for opponent in strategies:
            v.extend(get_scores(player,opponent,mapname,scores_dict))
        assert len(v) > 0, "no scores for " + strategies[j] + " on " + mapname
        coords += "  ({0},{1:.2f})\n".format(j, np.mean(v))

    # write LaTeX graph
    label = "compare_sim_engine_" + mapname
    caption = "Scores in Simulation and Engine on \\texttt{" + mapname.replace("_"," ") + "}"
    filepath = os.path.join(d, 'compare_sim_engine_'+mapname+'.tex')
    tex = open(filepath,'w')
    # "sharp plot" or "const plot"
    # xticklabel={<command>} or xticklabels={<label list>}
    #
    # error bars/.cd,y explicit.  Need "explicit" to put +- range in coordinates.
    #
    xtick = "{" + reduce(lambda x, y: str(x)+','+str(y), range(len(strategies))) + "}"
    xticklabels= "{" + reduce(lambda x, y: str(x)+'.,'+str(y), range(len(strategies))) + ".}"
    txt = """
\\begin{figure}[!ht]
\\centering
\\begin{tikzpicture}
\\begin{axis}[
    scaled ticks=false, % disallow scaling tick labels in powers of 10
    legend entries={Simulation Mean,Engine Mean},
    legend style={at={(1.5,.95)}},
    ymajorgrids=true,
    xlabel=Strategy,
    ylabel=Score,
    xtick=""" + xtick + "," + """
    xticklabels=""" +xticklabels + """
  ]
  \\addplot+[const plot mark mid] coordinates
    {""" + sim_coords + """};
  \\addplot+[const plot mark mid] coordinates
    {""" + coords + """};
\\end{axis}
\\end{tikzpicture}
\\caption{"""+caption+"""}
\\label{"""+label+"""}
\\end{figure}
"""
    tex.write(txt)
    tex.close()

#  \\addplot+[const plot mark mid,mark=none,style=dashed,draw=brown] coordinates
#    {""" + coords_plus + """};
#  \\addplot+[const plot mark mid,mark=none,style=dashdotted,draw=black] coordinates
#    {""" + coords_minus + """};
#  \\addplot+[const plot mark mid,mark=none,style=loosely dotted,draw=green] coordinates
#    {""" + median + """};

class MatrixTK:
    """game matrix parser states"""
    START=0
    CYCLE=1
    VALUES_KEYWORD=2
    ROWS_KEYWORD=3
    ROWS=4
    COLS_KEYWORD=5
    COLS=6
    VALUES=7
    SOLN_KEYWORD=8
    LENGTH_KEYWORD=9
    LENGTH=10
    SOLN=11

class MatrixHistory:
    def __init__(self,maxCycle):
        self.maxCycle = maxCycle
        self.nEvents = 0
        self.values = None
        
def games_matrices():
    """show average game matrix values over time."""
    epoch_matrices = [MatrixHistory(epoch) for epoch in epochs]
    gmfiles = glob.glob("*game_matrix0.txt")
    for fn in gmfiles:
        f = open(fn,'rb')
        for line in f.readlines():
            update_game_matrices(line,epoch_matrices)
        f.close()

    # write game matrix TEX files
    fmt = lambda x: "{0:.0f}".format(x) # formatter.
    rowhdr = [str(i) + "." for i in range(len(strategies))]
    colhdr = [str(i) + "." for i in range(len(strategies))]
    for i in range(len(epoch_matrices)):
        mh = epoch_matrices[i]
        if mh.nEvents > 0:
            caption = 'Avg. Game Matrix to Cycle ' + str(epochs[i])
            filepath = os.path.join(d, 'matrix_history_' + str(i) + '.tex')
            write_table(mh.values,fmt,rowhdr,colhdr,'label',caption,filepath)

def update_game_matrices(line,epoch_matrices):
        # parse
        # cycle 0 values:  rows 11 columns 11 -1.00... solution: length 12 0.00 ...
        fields = line.split()
        state = MatrixTK.START
        row = 0
        col = 0
        solni = 0
        matrixHistory = None
        for tk in fields:
            if state == MatrixTK.START:
                assert tk == "cycle"
                state = MatrixTK.CYCLE
            elif state == MatrixTK.CYCLE:
                cycle = int(tk)
                state = MatrixTK.VALUES_KEYWORD
            elif state == MatrixTK.VALUES_KEYWORD:
                assert tk == "values:"
                state = MatrixTK.ROWS_KEYWORD
            elif state == MatrixTK.ROWS_KEYWORD:
                assert tk == "rows"
                state = MatrixTK.ROWS
            elif state == MatrixTK.ROWS:
                rows = int(tk)
                state = MatrixTK.COLS_KEYWORD
            elif state == MatrixTK.COLS_KEYWORD:
                assert tk == 'columns'
                state = MatrixTK.COLS
            elif state == MatrixTK.COLS:
                cols = int(tk)
                for i in range(len(epochs)):
                    if cycle < epochs[i]:
                        matrixHistory = epoch_matrices[0]
                        break
                if matrixHistory.values:
                    assert len(matrixHistory.values) == rows
                    assert len(matrixHistory.values[0]) == cols
                else:
                    matrixHistory.values = [[0 for j in range(cols)] for i in range(rows)]
                state = MatrixTK.VALUES
            elif state == MatrixTK.VALUES:
                matrixHistory.values[row][col] = float(tk)
                col += 1
                if col >= cols:
                    col = 0
                    row += 1
                if row >= rows:
                    state = MatrixTK.SOLN_KEYWORD
            elif state == MatrixTK.SOLN_KEYWORD:
                assert tk == "solution:"
                state = MatrixTK.LENGTH_KEYWORD
            elif state == MatrixTK.LENGTH_KEYWORD:
                assert tk == "length"
                state = MatrixTK.LENGTH
            elif state == MatrixTK.LENGTH:
                soln_len = int(tk)
                soln = [0 for i in range(soln_len)]
                state = MatrixTK.SOLN
            elif state == MatrixTK.SOLN:
                soln[solni] = float(tk)
                solni += 1

        matrixHistory.nEvents += 1
        print "values", matrixHistory.values

def strat_vs_strat_sim_scores(d, strategy_set, strategies):
    """simulated strategy final scores"""
    print "strat_vs_strat_sim_scores()"
    for mapname in mapnames:
        fn = 'sim_scores_'+strategy_set + '_' + mapname+'-game.yaml'
        filepath = os.path.join(d, fn)
        f = open(filepath,'rb')
        simdata = yaml.load(f)
        f.close()
        strat_vs_strat_sim_scores_map(d,simdata, strategy_set, strategies, mapname)

def strat_vs_strat_sim_scores_map(d,simdata,strategy_set,strategies,mapname):
    """simulated strategy final scores"""
    # get YAML source files by running sim-matrix.bat. The BAT file runs
    # stratsim WriteGameMatrix.java.
    #
    # get simulated strategy value matrix
    #

    sv = simdata[0]['matrix']
    mins = np.min(sv,axis=0)
    sv.append(mins)
    sv.append(max_star(mins)) # mark the maximin columns

    fmt = lambda x: "{0:.0f}".format(x) if x.__class__ == float else str(x) # formatter.
    rowhdr = [str(j) + "." for j in range(len(strategies))]
    #rowhdr.append("average")
    rowhdr.append("min.")
    rowhdr.append("maxmin")
    hline = len(strategies) - 1
    colhdr = [str(j) + "." for j in range(len(strategies))]
    label = simdata[0]['label']
    #caption = simdata[0]['caption']
    caption = "Strategy Simulation Scores on \\texttt{" + mapname + "}"
    filename = os.path.join(d, 'sim_scores_' + strategy_set + '_' + mapname+'.tex')
    write_table(sv,fmt,rowhdr,colhdr,label,caption,filename,hline,bolddiag=True)

def get_sw_vs_strat_sim_scores(d,mapname,position='both'):
    """get sw_vs_strat scores averaged for map and switched position map"""
    # the file names aren't systematic, so just map them here.
    sim_maps = {
     '2bases' :
     ['sw_vs_strat_sim_2bases-game.yaml',
      'sw_vs_strat_sim_2bases_switched.yaml'],
     'the-right-strategy' :
    ['sw_vs_strat_sim_the-right-strategy-game.yaml',
     'sw_vs_strat_sim_the-right-strategy-game_switched.yaml']
    }
    # get map
    if position == 'both' or position == 'top':
        fn = sim_maps[mapname][0]
    else:
        fn = sim_maps[mapname][1]
    filepath = os.path.join(d, fn)
    f = open(filepath,'rb')
    simdata = yaml.load(f)
    f.close()
    sv = simdata[0]['matrix'] # sv: sim values
    if position == 'both':
        # get switched position map and take average
        fn = sim_maps[mapname][1]
        filepath = os.path.join(d, fn)
        f = open(filepath,'rb')
        simdata_switched = yaml.load(f)
        f.close()
        sv_switched = simdata_switched[0]['matrix']
        assert simdata[0]['colhdr'] == simdata_switched[0]['colhdr']
        assert len(sv) == len(sv_switched)
        for i in range(len(sv)):
            for j in range(len(sv[i])):
                sv[i][j] = (sv[i][j] + sv_switched[i][j])/2.0
    return simdata[0]

def sim_maximin(d, strategy_set):
    """get maximin values for simulated fixed strategies and switching planners"""
    print "sim_maximin()"
    # table of strategy maximin and switching planner minimums for each map
    table = [[None for j in range(len(switching)+1)] for i in range(len(mapnames))]
    for i in range(len(mapnames)):
        mapname = mapnames[i]
        # get strat vs. strat maximin
        filepath = os.path.join(d, 'sim_scores_' + strategy_set + '_' +  mapname + '-game.yaml')
        f = open(filepath,'rb')
        simdata = yaml.load(f)
        f.close()
        sv = simdata[0]['matrix']
        table[i][0] = get_maximin(sv)
        # get switcher vs. strat mins
        simdata = get_sw_vs_strat_sim_scores(d,mapname)
        mins = np.min(simdata['matrix'],axis=0)
        for j in range(len(switching)):
            table[i][j+1] = mins[j]

    fmt = lambda x: "{0:.0f}".format(x) # formatter.
    rowhdr = ['\\texttt{'+m+'}' for m in mapnames]
    hline = None
    colhdr = ['Fixed']
    colhdr.extend(['\\texttt{'+sw+'}' for sw in switching])
    label = 'sim_maximin'
    caption = 'Fixed Strategy Maximin and Switching Planner Minimums in Simulation'
    filename = os.path.join(d, 'sim_maximin_' + strategy_set + '.tex')
    write_table(table,fmt,rowhdr,colhdr,label,caption,filename,hline)

def get_maximin(table):
    """get column-wise maximin value"""
    mins = np.min(table,axis=0)
    return max(mins)

def engine_maximin(d,means):
    """get maximin values for fixed strategies and switching planners on games played in engine"""
    print "engine_maximin()"
    #                       fixed  Nash maximin monotone
    #  2bases                   x     x       x        x
    #  the-right-strategy       x     x       x        x
    table = [[None for j in range(len(switching)+1)] for i in range(len(mapnames))]

    for i in range(len(mapnames)):
        mapname = mapnames[i]
        table[i][0] = means.s_v_s_maximin_pair[mapname][0]
        for j in range(len(switching)):
            player = switching[j]
            table[i][j+1] = means.sw_v_s_min[mapname][player][0]

    fmt = lambda x: "{0:.0f}".format(x) if x else "" # formatter.
    rowhdr = ['\\texttt{'+m+'}' for m in mapnames]
    hline = None
    colhdr = ['Fixed']
    colhdr.extend(['\\texttt{'+sw+'}' for sw in switching])
    label = 'engine_maximin_means'
    caption = 'Switching Planner Minimum Means in Engine'
    filename = os.path.join(d, 'engine_maximin_means.tex')
    if True:
        write_table(table,fmt,rowhdr,colhdr,label,caption,filename,hline)
    else:
        print_table(table,fmt,rowhdr,colhdr,caption)

def engine_maximin_medians(d,medians):
    """get maximin values for fixed strategies and min values for switching planners on games played in engine"""
    print "engine_maximin_medians()"
    #                       Fixed  Nash maximin monotone
    #  2bases                   x     x       x        x
    #  the-right-strategy       x     x       x        x
    table = [[None for j in range(len(switching)+1)] for i in range(len(mapnames))]

    for i in range(len(mapnames)):
        mapname = mapnames[i]
        interval = medians.s_v_s_maximin_interval[mapname]
        table[i][0] = interval.median
        for j in range(len(switching)):
            player = switching[j]
            interval = medians.sw_v_s_min_intervals[mapname][player]
            table[i][j+1] = interval.median

    fmt = lambda x: "{0:.0f}".format(x) if x else "" # formatter.
    rowhdr = ['\\texttt{'+m+'}' for m in mapnames]
    hline = None
    colhdr = ['Fixed']
    colhdr.extend(['\\texttt{'+sw+'}' for sw in switching])
    label = 'engine_maximin_medians'
    caption = 'Fixed Strategy Maximin and Switching Planner Minimum Medians in Engine'
    filename = os.path.join(d, 'engine_maximin_medians.tex')
    write_table(table,fmt,rowhdr,colhdr,label,caption,filename,hline)

def engine_maximin_pairs(d,means,score_dict):
    """get maximin values for fixed strategies and min values for switching planners on games played in engine"""
    print "engine_maximin_pairs()"
    #
    #
    #             player opponent value confidence
    #   ------------------------------------------
    #   maximin     x        x       x
    #   ------------------------------------------
    #   minimums   Nash      x       x
    #              maximin   x       x
    #              monotone  x       x
    #
    fmt = lambda x: "{0}".format(x) if x.__class__ == str else "{0:.0f}".format(x) # formatter.
    rowhdr = ['maximin','minimums','','']
    hline = 0
    colspec = " l | l l r r"
    colhdr = ['Player','Opponent','Score','Rate Confidence']

    for i in range(len(mapnames)):
        table = [[""]*4 for j in range(len(switching)+1)]
        mapname = mapnames[i]
        c = means.s_v_s_maximin_pair[mapname]
        table[0][0] = c[1].replace('_',' ') # player
        table[0][1] = c[2].replace('_',' ') # opponent
        table[0][2] = c[0] # mean
        # calculate confidence interval
        v = get_scores(c[1],c[2],mapname,score_dict)
        nWins = count_wins(v)
        print "mean of scores",np.mean(v)
        print nWins,"wins in",len(v)
        interval = bernoulli_confidence(v,'wilson')
        table[0][3] = "{0:.0f}\% ({1:.0f}\%,{2:.0f}\%)".format(interval[0]*100,
                                                         interval[1][0]*100
                                                         ,interval[1][1]*100)
        for j in range(len(switching)):
            player = switching[j]
            c = means.sw_v_s_min[mapname][player]
            table[j+1][0] = c[1] #player
            table[j+1][1] = c[2].replace('_',' ') #opponent
            table[j+1][2] = c[0] # mean
            # calculate confidence interval
            v = get_scores(c[1],c[2],mapname,score_dict)
            interval = bernoulli_confidence(v,'wilson')
            table[j+1][3] = "{0:.0f}\% ({1:.0f}\%,{2:.0f}\%)".format(interval[0]*100,
                                                         interval[1][0]*100
                                                         ,interval[1][1]*100)

        filepath = os.path.join(d, 'engine_maximin_pairs_'+mapname+'.tex')
        label = 'engine_maximin_pairs_'+mapname
        caption = 'Strategy Pairs on \\texttt{'+mapname+'}'
        write_table(table,fmt,rowhdr,colhdr,label,caption,filepath,hline,colspec=colspec)

def sw_vs_strat_sim_scores(d):
    """translate game points YAML tables into LaTeX tables."""
    print "sw_vs_strat_sim_score()"
    # get YAML source files by running orst.stratagusai.stratsim.analysis.SwitchingPlannerSimulation
    #
    for m in range(len(mapnames)):
        mapname = mapnames[m]
        # get score averaged for playing from top and bottom of map
        simdata = get_sw_vs_strat_sim_scores(d,mapname,position='both')
        sw_vs_strat_sim_scores_by_map(d,simdata,mapname,position='both')
        # get score for playing from top of map
        simdata = get_sw_vs_strat_sim_scores(d,mapname,position='top')
        sw_vs_strat_sim_scores_by_map(d,simdata,mapname,position='top')
        # get scores for playing from bottom of map
        simdata = get_sw_vs_strat_sim_scores(d,mapname,position='bottom')
        sw_vs_strat_sim_scores_by_map(d,simdata,mapname,position='bottom')

def sw_vs_strat_sim_scores_by_map(d, simdata, mapname, position):
    rowhdr = [str(i)+'. \\texttt{'+simdata['rowhdr'][i]+'}' for i in range(len(simdata['rowhdr']))]
    colhdr = ['\\texttt{'+s+'}' for s in simdata['colhdr']]
    sv = simdata['matrix']
    means = np.mean(sv,axis=0)
    mins = np.min(sv,axis=0)
    sv.append(means)
    sv.append(mins)
    fmt = lambda x: "{0:.0f}".format(x) # formatter.  show as percent.    
    hline = len(rowhdr) - 1
    rowhdr.append("mean")
    rowhdr.append("minimum")    
    caption = 'Switching Planner Scores in Simulation on \\texttt{' + mapname + "}"
    if position == 'top':
        caption += ' from North'
    elif position == 'bottom':
        caption += ' from South'
    label = 'sw_vs_strat_sim_score_' + mapname
    fn = 'sw_vs_strat_sim_score_' + mapname
    if position == 'top' or position == 'bottom':
        label += '_' + position
        fn += '_' + position
    fn += '.tex'    
    filepath = os.path.join(d, fn)
    write_table(sv,fmt,rowhdr,colhdr,label,caption,filepath,hline)

def sw_vs_strat_scores_by_epoch(d,curs,player,opponent,mapname):
    i = mapnames.index(mapname)
    mappaths = engine_maps[i]
    table = [[0 for epoch in epochs] for i in range(len(mappaths))]
    rowhdr = []
    for i in range(len(mappaths)):
        mappath = mappaths[i]
        p,m = os.path.split(mappath)
        m = m.replace('_',' ')
        rowhdr.append("\\texttt{"+player+"} on " + m)
        for epoch in range(len(epochs)):
            if epoch == 0:
                start = 0
            else:
                start = epochs[epoch-1]
            end = epochs[epoch]

            cmd = "select avg(diff) from event where player=? and simreplan=1 " + \
              " and opponent=? " + \
              " and cycle >= ? and cycle < ? " + \
              " and map=? "
            mean = curs.execute(cmd,(player,opponent,start,end,mappath,)).fetchone()[0]
            table[i][epoch] = mean
    fmt = lambda x: "{0:.0f}".format(x) # formatter.
    colhdr = ["{0:,}".format(s) for s in epochs]
    caption = '\\texttt{{{0}}} vs.~\\texttt{{{1}}} Score by Epoch on \\texttt{{{2}}}'.format(player,opponent.replace('_',' '),mapname)
    label = '{0}_v_{1}_score_by_epoch_on_{2}'.format(player,opponent,mapname)
    filepath = os.path.join(d, label + '.tex')
    write_table(table,fmt,rowhdr,colhdr,label,caption,filepath,None)

def sim_minus_engine_scores(d,curs,strategy_set,strategies):
    """sim score matrix - engine score matrix"""
    sim_minus_engine_scores_map(d,curs,strategy_set,strategies,None,None)
    for i in range(len(planner_maps)):
        simmap = sim_maps[i]
        mappath = planner_maps[i]
        sim_minus_engine_scores_map(d,curs,strategy_set,strategies,simmap,mappath)

def sim_minus_engine_scores_map(d,curs,strategy_set,strategies,simmap, mappath):
    # simulation data
    if simmap:
        fn = 'sim_scores_'+strategy_set+'_'+simmap+'.yaml'
    else:
        fn = 'sim_scores_'+strategy_set + '.yaml'
    filepath = os.path.join(d, fn)
    f = open(filepath,'rb')
    simdata = yaml.load(f)
    f.close()
    sv = simdata[0]['matrix']
    # engine data
    hp = strat_vs_strat_avg_score_data(curs,strategies,mappath)
    data = [row[:] for row in sv] # copy sim matrix
    for i in range(len(hp)):
        for j in range(len(hp[i])):
            data[i][j] = data[i][j] - hp[i][j] # minus engine data
    fmt = lambda x: "{0:.0f}".format(x) # formatter.  show as percent.
    rowhdr = [s.replace('_',' ') for s in strategies]
    hline = None
    colhdr = [str(i) + '.' for i in range(len(strategies))]    
    if mappath:
        path, mapname = os.path.split(mappath)
        mapname = mapname.replace('.smp','')
        caption = 'Simulation Minus Engine Scores on ' + mapname.replace('_',' ')
        label = 'sim_minus_engine_'+mapname
        outpath = os.path.join(d,'sim_minus_engine_scores_'+mapname+'.tex')
    else:
        caption = 'Simulation Minus Engine Scores'
        label = 'sim_minus_engine'
        outpath = os.path.join(d,'sim_minus_engine_scores.tex')
    write_table(data,fmt,rowhdr,colhdr,label,caption,outpath,hline)

def write_game_matrices(d,filename):
    f = open(filename,'rb')
    matrices = yaml.load(f)
    f.close()
    for m in matrices:
        write_game_matrix(d,m,filename)

def write_game_matrix(d,data,filename):

    cycle = data['cycle']
    caption = data['caption'].replace("_"," ")
    label = data['label']
    matrix = data['matrix']
    mins = np.min(matrix,axis=0)
    matrix.append(mins)
    matrix.append(max_star(mins)) # mark the maximin columns

    fmt = lambda x: str(x) # formatter.
    rowhdr = data['rowhdr']
    colhdr = data['colhdr']
    hline = len(rowhdr)
    rowhdr.append('mins')
    rowhdr.append('maximin')
    filepath = os.path.join(d, filename.replace(".yaml",'') + "_" + str(cycle) + ".tex")
    print filepath
    write_table(matrix,fmt,rowhdr,colhdr,label,caption,filepath,hline)

def write_game_choices(d, curs, player, opponent, map):
    print "write_game_choices({0},{1},{2})".format(player,opponent,map)
    cmd = """select cycle,strategy from event
              where player=? and opponent=? and map=? and event='plan' order by cycle"""
    curs.execute(cmd,(player,opponent,map+".txt",))

    label = "{0}_{1}_choices_{2}".format(player,opponent,map)
    filepath = os.path.join(d,label + ".tex")
    tex = open(filepath,'w')
    today = datetime.date.today()
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("""\\begin{table}[!ht]
\\centering
\\begin{tabular}{l | l}
cycle & strategy\\cr
\\hline
""")
    for row in curs.fetchall():
        tex.write("{0} & {1}\\cr\n".format(row[0],row[1].replace('_',' ')))
    tex.write("""
\\end{tabular}
\\caption{""" + "{0} Choices against {1} on {2}".format(player,opponent.replace('_',' '),map.replace('_',' ')) + """}
\\label{""" + label + """}
\\end{table}
""")
    tex.close()

def write_confidence_tables(d, medians):
    print "write_confidence_tables()"
    for mapname in mapnames:
        write_confidence_table(d,medians,mapname)
        write_sw_confidence_table(d,medians,mapname)
        
def write_confidence_table(d, medians, mapname):
    """for each fixed strategy vs. fixed strategy write confidence around mean"""
    # using multirows, so can't use write_table()
    rowhdr = [str(j) + "." for j in range(len(medians.strategies))]
    colhdr = rowhdr
    filepath = os.path.join(d, 's_v_s_confidence_' + mapname + '.tex')
    today = datetime.date.today()
    tex = open(filepath,'w')
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("\\begin{table}[!ht]\n")
    tex.write("\\centering\n")
    tex.write("\\begin{tabular}{l | ")
    for j in range(len(colhdr)):
        tex.write(" r ") # assume numbers in cells
    tex.write("}\n")
    # column header
    for c in colhdr:
        tex.write(" & " + c)
    tex.write("\\cr\n")
    tex.write("\\hline\n")

    interval_table = medians.s_v_s_intervals[mapname]
    median_table = [[None for o in medians.strategies] for p in medians.strategies]
    for i in range(len(medians.strategies)):
        tex.write("\\multirow{3}{*}{"+ rowhdr[i] + "}")
        # write high of confidence interval
        for j in range(len(medians.strategies)):
            confidence = interval_table[i][j]
            tex.write("& {0:.0f}".format(confidence.interval[1]))
        tex.write("\\\\")
        # write median of confidence interval
        for j in range(len(medians.strategies)):
            confidence = interval_table[i][j]
            median_table[i][j] = confidence.median
            tex.write(" & {0:.0f}".format(confidence.median))
        tex.write("\\\\")
        # write low of confidence interval
        for j in range(len(medians.strategies)):
            confidence = interval_table[i][j]
            tex.write(" & {0:.0f}".format(confidence.interval[0]))
        tex.write("\\\\")
        tex.write("\n")
        tex.write("\\hline\n")

    # add minimum
    mins = np.min(median_table,axis=0) # column mins
    tex.write("\\hline\n")
    tex.write("minimums")
    for m in mins:
        tex.write(" & {0:.0f}".format(m))
    tex.write("\\cr\n")
    tex.write("maximin")
    for m in max_star(mins):
        tex.write(" & {0}".format(m))
    tex.write("\\cr\n")
    
    label = 's_v_s_confidence_' + mapname
    caption = 'Fixed Strategy Confidence on ' + mapname
    tex.write("\end{tabular}\n")
    tex.write("\\caption{" + caption + "}\n")
    tex.write("\\label{" + label + "}\n")
    tex.write("\\end{table}\n")
    tex.close()
    print '\\input{' + filepath.replace('.tex','') + '}'

def write_sw_confidence_table(d, medians, mapname):
    """for each switching vs. fixed strategy write confidence around mean"""
    # using multirows, so can't use write_table()
    rowhdr = [str(j) + ". "  + medians.strategies[j].replace('_',' ') for j in range(len(medians.strategies))]
    colhdr = ["\\texttt{"+sw+"}" for sw in switching]
    filepath = os.path.join(d, 'sw_v_s_confidence_' + mapname + '.tex')
    today = datetime.date.today()
    tex = open(filepath,'w')
    tex.write("% table written on {0} by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
    tex.write("\\begin{table}[!ht]\n")
    tex.write("\\centering\n")
    tex.write("\\begin{tabular}{l | ")
    for j in range(len(colhdr)):
        tex.write(" r ") # assume numbers in cells
    tex.write("}\n")
    # column header
    for c in colhdr:
        tex.write(" & " + c)
    tex.write("\\cr\n")
    tex.write("\\hline\n")

    interval_table = medians.sw_v_s_intervals[mapname]
    median_table = [[None for sw in switching] for s in medians.strategies]
    for i in range(len(medians.strategies)):
        tex.write("\\multirow{3}{*}{"+ rowhdr[i] + "}")
        # write high of confidence interval
        for j in range(len(switching)):
            confidence = interval_table[i][j]
            tex.write("& {0:.0f}".format(confidence.interval[1]))
        tex.write("\\\\")
        # write median of confidence interval
        for j in range(len(switching)):
            confidence = interval_table[i][j]
            median_table[i][j] = confidence.median
            tex.write(" & {0:.0f}".format(confidence.median))
        tex.write("\\\\")
        # write low of confidence interval
        for j in range(len(switching)):
            confidence = interval_table[i][j]
            tex.write(" & {0:.0f}".format(confidence.interval[0]))
        tex.write("\\\\")
        tex.write("\n")
        tex.write("\\hline\n")

    # add minimum
    mins = np.min(median_table,axis=0) # column mins
    tex.write("\\hline\n")
    tex.write("minimums")
    for m in mins:
        tex.write(" & {0:.0f}".format(m))
    tex.write("\\cr\n")

    label = 'sw_v_s_confidence_' + mapname
    caption = 'Switching Planner Confidence on ' + mapname
    tex.write("\end{tabular}\n")
    tex.write("\\caption{" + caption + "}\n")
    tex.write("\\label{" + label + "}\n")
    tex.write("\\end{table}\n")
    tex.close()
    print '\\input{' + filepath.replace('.tex','') + '}'

def get_classification_rate(scores_dict,strategies):
    """what percentage of confidence intervals fall fully positive or fully negative?"""
    n = 0 # number of confidence intervals fall fully positive or fully negative
    nIntervals = 0
    for player in strategies:
        for opponent in strategies:
            for mapname in mapnames:
                scores = scores_dict[(player,opponent,mapname)] # get_strat_v_strat_scores2(curs,player,opponent,mappath)
                assert len(scores) == 50, str(len(scores))+" scores for "+player+" vs. "+opponent+" on " + mapname
                #intervals = get_confidence_intervals(player,scores,[50])
                intervals = [] # fix
                assert len(intervals) == 1
                i = intervals[0]
                nIntervals += 1
                if np.sign(i.interval[0]) == np.sign(i.interval[1]):
                    n += 1
    print "percent of confidence intervals fall fully positive or fully negative is {0:.2f}.".format(n/float(nIntervals))

def get_scores(player,opponent,mapname,scores_dict,combine=True):
    """get scores on forward and switched maps"""
    v = scores_dict[(player,opponent,mapname)][:] # make copy
    assert v, "No games for {0} vs. {1} on {2}".format(player,opponent,mapname)
    if combine and player != opponent:
        v_switched = scores_dict[(opponent,player,mapname)]
        assert v_switched
        v.extend([-x for x in v_switched])
    return v

def get_mean(player,opponent,mapname,scores_dict,combine=True):
    v = get_scores(player,opponent,mapname,scores_dict,combine)
    return np.mean(v)

def get_median(player,opponent,mapname,scores_dict,combine=True):
    v = get_scores(player,opponent,mapname,scores_dict,combine)
    return np.median(v)

def get_rate(player,opponent,mapname,scores_dict,combine=True):
    v = get_scores(player,opponent,mapname,scores_dict,combine)
    return count_wins(v)/float(len(v))

def get_score_dict(curs,strategies):
    """get dictionary of scores for player vs. opponent on map """
    scores = {}
    cmd = "select diff from event where event='end' and player=? and opponent=? and map=?"
    for mappaths in engine_maps:
        path,mapname = os.path.split(mappaths[0])
        mapname = mapname.replace('.smp','')
        # fixed strat vs. fixed strat
        # match pairs defined in configs.py
        for i in range(len(strategies)):
            player = strategies[i]
            for j in range(i+1):
                opponent = strategies[j]                
                # get player vs. opponent on map scores                
                curs.execute(cmd,(player,opponent,mappaths[0],))
                pair_scores = [row[0] for row in curs.fetchall()]
                scores[(player,opponent,mapname)] = pair_scores
                # get player vs. opponent on switched map scores
                curs.execute(cmd,(player,opponent,mappaths[1],))                
                if player == opponent:
                    pair_scores = [row[0] for row in curs.fetchall()]
                    scores[(opponent,player,mapname)].extend(pair_scores)
                else:
                    pair_scores = [-row[0] for row in curs.fetchall()]
                    scores[(opponent,player,mapname)] = pair_scores
        # switching vs. fixed strat games
        for player in switching:
            for opponent in strategies:
                # get player vs. opponent on map scores
                curs.execute(cmd,(player,opponent,mappaths[0],))
                pair_scores = [row[0] for row in curs.fetchall()]
                scores[(player,opponent,mapname)] = pair_scores
                # get player vs. opponent on switched map scores
                curs.execute(cmd,(player,opponent,mappaths[1],))
                pair_scores = [-row[0] for row in curs.fetchall()]
                scores[(opponent,player,mapname)] = pair_scores
        # switching vs. switching
        for i in range(len(switching)):
            player = switching[i]
            for j in range(i): # [0,...,i-1]
                opponent = switching[j]
                # get player vs. opponent on map scores
                curs.execute(cmd,(player,opponent,mappaths[0],))
                pair_scores = [row[0] for row in curs.fetchall()]
                key = (player,opponent,mapname)
                scores[key] = pair_scores
                # get player vs. opponent on switched map scores
                curs.execute(cmd,(player,opponent,mappaths[1],))
                pair_scores = [-row[0] for row in curs.fetchall()]
                key = (opponent,player,mapname)
                scores[key] = pair_scores
    # switching vs. builtin
    for mappaths in script_maps:
        path,mapname = os.path.split(mappaths[0])
        mapname = mapname.replace('_PvC.smp','')
        for player in switching:
            opponent = 'built-in'
            # get player vs. opponent on map scores
            curs.execute(cmd,(player,opponent,mappaths[0],))
            pair_scores = [row[0] for row in curs.fetchall()]
            scores[(player,opponent,mapname)] = pair_scores
            # get player vs. opponent on switched map scores
            curs.execute(cmd,(player,opponent,mappaths[1],))
            pair_scores = [-row[0] for row in curs.fetchall()]
            scores[(opponent,player,mapname)] = pair_scores

    return scores

def build_db(d):
    """open event database and return connection."""
    dbpath = os.path.join(d, 'events.db')
    
    # connect to database and create table.
    if os.path.exists(dbpath):
        os.remove(dbpath)
    conn = sqlite3.connect(dbpath)
    curs = conn.cursor()
    curs.execute('''create table event
    (game int,
     event text,
     playerId text,
     player text,
     strategy text,
     simreplan int,
     opponent text,
     predicted text,
     predicted_diff int,
     actual text,
     diff int,
     cycle int,
     map text)''')

    csvfiles = glob.glob(d + '/*_0.csv') # non-simulation files.  sim files end in *_sim.csv

    if len(csvfiles) == 0:
        msg = "No input files found."
        raise Exception(msg)

    game = 0
    for filename in csvfiles:
        file = open(filename, 'rb')
        rd = csv.reader(file)
        for row in rd:
            event = row[0]
            row.insert(0,game) # add game ID
            curs.execute("""insert into event
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)""", row)
            if event == 'end':
                game += 1
        file.close()
    conn.commit()
    return conn

def open_db(d):
    """open event database and return connection."""
    dbpath = os.path.join(d, 'events.db')
    if not os.path.exists(dbpath):
        msg = "Error: database file", dbpath, "does not exist."
        raise Error(msg)
    conn = sqlite3.connect(dbpath)
    return conn

def build_score_dictionary(d,curs,strategies):
    # get dictionary of score arrays indexed by (player,opponent,mappath) tuples
    scores = get_score_dict(curs,strategies)
    mfile = open(os.path.join(d,'score_dict.pkl'),'wb')
    pickle.dump(scores,mfile)
    mfile.close()
    return scores

def open_score_dictionary(d,curs,strategies):
    fn = os.path.join(d,'score_dict.pkl')
    if not os.path.exists(fn):
        return build_score_dictionary(d,curs,strategies)
    else:
        mfile = open(fn,'rb')
        scores = pickle.load(mfile)
        mfile.close()
        return scores

