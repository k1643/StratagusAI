import os.path
import argparse
from gamelogs import *
import numpy as np
import os


################################################################################
# main
#
parser = argparse.ArgumentParser(description='Analyze player statistics.')
parser.add_argument('-d', default='.', help='source data directory')
parser.add_argument('-b', action='store_true', default=False, help='build database from CSV files')
parser.add_argument('-t', action='store_true', default=False, help='run test functions only')
parser.add_argument('-l', action='store_true', default=False, help='include long-running functions')
parser.add_argument('-v', action='store_true', default=False, help='verify games only')
args = parser.parse_args()

np.seterr(all='raise') # Numpy raise error on floating point error.
random.seed(14)        # reproducible sampling

strategies = load_strategy_defs(args.d, "2012-02-05")
dbpath = os.path.join(args.d, 'events.db')

if args.b:
    # connect to database and create table.
    conn = build_db(args.d)
    curs = conn.cursor()
    scores = build_score_dictionary(args.d,curs,strategies)
else:
    conn = open_db(args.d)
    curs = conn.cursor()
    scores = open_score_dictionary(args.d,curs,strategies)
#medians = Medians(scores,strategies)
means = Means(scores,strategies)

if args.v:
    validate_games(curs,scores,strategies)
    curs.close()
    conn.close()
    exit()
    
strategy_sets = ["2012-02-05"] # "atk-dfnd","synth"]

if args.t:
    # test functions here
    #
    switcher_choices_sim(args.d,strategies, 'the-right-strategy', 'monotone_vs_balanced_9_0_sim.csv')
    curs.close()
    conn.close()
    exit()

compare_sim_engine(args.d, scores, strategy_sets[0],strategies) # means plot
strat_vs_strat_sim_scores(args.d, strategy_sets[0], strategies)
strat_vs_strat_score(args.d, scores, strategies)
strat_vs_strat_rate(args.d,scores,strategies)
sw_vs_strat_sim_scores(args.d)     # translate game points YAML tables into LaTeX tables
sw_vs_strat_scores(args.d,scores,strategies) # engine scores -> sw_scores_<mapname>.tex
sw_vs_strat_rates(args.d,scores,strategies)
sim_maximin(args.d, strategy_sets[0]) #
engine_maximin(args.d,means) # table engine_maximin_<strategy_set>.tex
engine_maximin_pairs(args.d,means,scores)
sw_vs_sw(args.d,scores)
if args.l: # long running functions here
    switcher_choices(args.d,curs,strategies) # one summary table, then one table for each map
    switcher_choices_by_epoch(args.d,curs,strategies)
    switcher_choices_sim(args.d,strategies, 'the-right-strategy', 'monotone_vs_balanced_9_0_sim.csv')
    switcher_choices_by_opponent_map_epoch(args.d,curs,strategies,'maximin','balanced_9','the-right-strategy')
    switcher_choices_by_opponent_map_epoch(args.d,curs,strategies,'monotone','balanced_9','the-right-strategy')
    sw_vs_strat_scores_by_epoch(args.d,curs,'maximin','balanced_9','the-right-strategy')
    sw_vs_strat_scores_by_epoch(args.d,curs,'monotone','balanced_9','the-right-strategy')
    ## analyzing switcher choices
    #write_game_matrices(args.d,'maximin_rush_9_2bases_matrices.yaml')
    #write_game_matrices(args.d,'maximin_rush_9_2bases_switched_matrices.yaml')

#show_maximin_compare_errorbars(args.d,medians)
#get_classification_rate(scores,strategies)
#plot_rate_v_mean(args.d,scores)
#game_duration(args.d,curs)
#switcher_win_loss_choices(curs)
#games_matrices() # """show average game matrix values over time.""" *game_matrix0.txt -> matrix_history_<i>.tex


curs.close() # cursor close.
conn.close()