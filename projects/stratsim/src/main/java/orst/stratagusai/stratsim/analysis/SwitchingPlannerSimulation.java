package orst.stratagusai.stratsim.analysis;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.mgr.Statistics;
import orst.stratagusai.stratplan.mgr.StrategyController;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameStates;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.persist.GameStateWriter;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;
import orst.stratagusai.stratsim.io.UnitGroupSimWriter;
import orst.stratagusai.stratsim.io.UnitsGameStateWriter;
import orst.stratagusai.stratsim.io.YamlTable;
import orst.stratagusai.stratsim.model.SimController;
import orst.stratagusai.stratsim.model.SimState;
import orst.stratagusai.stratsim.model.GroupSim;
import orst.stratagusai.stratsim.planner.GoalDrivenPlanner;
import orst.stratagusai.stratsim.planner.SwitchingPlanner;
import orst.stratagusai.stratsim.planner.StrategyTemplate;
import orst.stratagusai.util.Randomizer;

/**
 *
 * Evaluate performance of SwitchingPlanner in simulated games.
 * <p>
 * Takes 12 hours to run?
 * <p>
 * Run of one SwitchingPlanner on 4 maps takes 1 hour.
 * Run of 2 SwitchingPlanners with 14 strategies on 1 map takes 2 hours.
 *
 * write switcher_opponent_sim_|mapname|.yaml files.
 * sw_vs_strat_sim.yaml
 */
public class SwitchingPlannerSimulation {

    private static Logger simlog = Logger.getLogger("game_sim_event");
    
    protected static String dir = ".";

    /** planners to use for each fixed strategy. */
    protected static GoalDrivenPlanner [] planners;
    
    protected static final String strategy_set = "2012-02-05";

    //protected static GameEvaluator evaluator = new CombatEvaluator();

    private static GameStateWriter stateWr;

    /** tell SwitchingPlanner which strategy pair simulations to log */
    private static int[][] sim_pair_log;

    public static void main(String[] args) throws Exception {
        Randomizer.init(14L); // initialize common random seed.

        Options options = new Options();
        options.addOption("s", true, "switching planner");
        options.addOption("f", true, "fixed strategy opponent");
        options.addOption("m", true, "map");
        options.addOption("h", false, "help");

        Parser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption("h")) {
            HelpFormatter fmt = new HelpFormatter();
            fmt.printHelp(SwitchingPlannerSimulation.class.getName(), options);
            System.exit(-1);
        }

        // turn off debug logs
        Logger orstlog = Logger.getLogger("orst");
        Logger msglog = Logger.getLogger("msglog");
        
        orstlog.setLevel(Level.OFF);
        msglog.setLevel(Level.OFF);
        simlog.setLevel(Level.OFF);

        // switching planners to run.
        String[] switchers;
        if (cmd.hasOption("s")) {
            String sw = cmd.getOptionValue("s");
            System.out.println("switching planner " + sw + ".");
            switchers = new String[]{sw};
        } else {
            switchers = new String[]{
                "Nash",
                "maximin",
                "monotone",
            };
        }
        // fixed strategy opponents to run.
        StrategyTemplate[] strategies;
        if (cmd.hasOption("f")) {
            String f = cmd.getOptionValue("f");
            System.out.println("fixed strategy opponent " + f + ".");
            strategies = new StrategyTemplate[] {
                StrategyTemplate.getNamedTemplate(f)
            };
        } else {
            strategies = SwitchingPlanner.getStrategies(strategy_set);
        }
        planners = new GoalDrivenPlanner[strategies.length];
        for (int i = 0; i < strategies.length; i++) {
            planners[i] = new GoalDrivenPlanner();
            planners[i].setTemplate(strategies[i]);
        }
        
        // erase previous log files.
        String[] logFiles = {
            "game_events.txt", "game_events_sim.txt",
            "plans0.txt", "plans0_sim.txt",
            "plans1.txt", "plans1_sim.txt",
            "game_state.txt", "game_state_sim.txt",
            "game_scores.txt", "game_scores_sim.txt"
        };
        for (String fn : logFiles) {
            File f = new File(fn);
            if (f.exists()) {
                f.delete();
            }
        }
        
        String[] gameFiles;
        if (cmd.hasOption("m")) {
            String m = cmd.getOptionValue("m");
            System.out.println("map " + m + ".");
            if ("2bases".equals(m)) {
                gameFiles = new String[]{
                    "2bases-game.txt",                      //
                    "2bases_switched.txt"};
            } else if ("the-right-strategy".equals(m)) {
                gameFiles = new String[]{
                    "the-right-strategy-game.txt",          // in stratplan module.
                    "the-right-strategy-game_switched.txt"};
            } else {
                System.err.println("unknown map " + m);
                return;
            }
        } else {
            gameFiles = new String[]{
                "the-right-strategy-game.txt",          // in stratplan module.
                "the-right-strategy-game_switched.txt",  //
                "2bases-game.txt",                      //
                "2bases_switched.txt",                  // in stratplan module.
            };
        }

        // erase simulation matrix files
        // maximin_dfnd-atk_7_mass_sim_2bases_switched_
        for (String switcher :switchers) {
            for (GoalDrivenPlanner p : planners) {
                String opponent = p.getTemplate().getName().replace(" ", "_");
                for (String gameFile : gameFiles) {
                    String mn = gameFile.replace(".txt", "");
                    String fn = String.format("%s_%s_sim_%s.yaml",
                                   switcher, opponent,mn);
                    File f = new File(fn);
                    if (f.exists()) {
                        f.delete();
                    }
                }
            }
        }

        String[] colhdr = switchers;
        String[] rowhdr = new String[planners.length];
        for (int i = 0; i < rowhdr.length; i++) {
            rowhdr[i] = planners[i].getTemplate().getName().replace("_", " ");
        }
        
        SwitchingPlanner p = new SwitchingPlanner();
        Map params = new LinkedHashMap();
        params.put("simReplan", true);

        final double nMaps = gameFiles.length;
        double [][][] values = new double[gameFiles.length][planners.length][switchers.length];
        long start = System.currentTimeMillis();
        for (int m = 0; m < nMaps; m++) {
            System.out.println(String.format("map file %d (%s)",m, gameFiles[m]));
            for (int j = 0; j < switchers.length; j++) {
                params.put("choice_method", switchers[j]);
                params.put("player", switchers[j]);
                params.put("strategy_set", strategy_set);
                for (int i = 0; i < planners.length; i++) {
                    String opponent = planners[i].getTemplate().getName();
                    params.put("opponent", opponent);
                    p.configure(params);
                    // simulate()
                    // for map m data row is for fixed planner, and column j
                    // is for switching planner.
                    values[m][i][j] = simulate(p, i, gameFiles[m]);
                    System.out.println(String.format(
                            "Finished %s vs. %s (strategy %d of %d)  on map %s. value=%.2f",
                            switchers[j],planners[i].getTemplate().getName(),
                            i,planners.length,m,values[m][i][j]));
                }
            }
            String caption = "Switching Planner vs. Strategies in Simulation on " + gameFiles[m].replace("_"," ").replace(".txt", "") + ".";
            String label = "sim_scores_" + gameFiles[m].replace(".txt", "");
            File f = new File(dir,"sw_vs_strat_sim_" + gameFiles[m].replace(".txt","") + ".yaml");
            String comment = "table written by " + SwitchingPlannerSimulation.class.getSimpleName();
            YamlTable.write_YAML_table(values[m], colhdr, rowhdr, label, caption, f.getPath(), comment);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(String.format("Games over! Elapsed time %.2f minutes", elapsed/(1000*60F)));

        // get average of values over maps.
        //
        double[][] avgs = new double[planners.length][switchers.length];
        for (int m = 0; m < nMaps; m++) {
            for (int i = 0; i < avgs.length; i++) {
                for (int j = 0; j < avgs[0].length; j++) {
                    avgs[i][j] += values[m][i][j];
                }
            }
        }
        for (int i = 0; i < avgs.length; i++) {
            for (int j = 0; j < avgs[0].length; j++) {
                avgs[i][j] = avgs[i][j] / nMaps;
            }
        }
        String caption = "Switching Planner vs. Strategies in Simulation.";
        File f = new File(dir,"sw_vs_strat_sim.yaml");
        String comment = "table written by " + SwitchingPlannerSimulation.class.getSimpleName();
        YamlTable.write_YAML_table(avgs, colhdr, rowhdr, "sim_scores", caption, f.getPath(), comment);

        diff();
    }

    public static double simulate(SwitchingPlanner p, int opponentPlanner, String gameFile) throws IOException {
        final int playerId = 0;
        final int opponentId = 1;
        GameState game = GameStates.getGameState(gameFile);
        // create strategic state
        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        SimState state = new SimState(gs, game);
        // make plans

        // set matrix planner to log simulated events for first pair of strategies.
        int pair_index = 0;
        sim_pair_log = getPairLogSequence(p, opponentPlanner, gameFile);
        if (sim_pair_log != null) {
            p.setLogEventsPair(sim_pair_log[pair_index]);
            pair_index++;
        }
        // set SwitchingPlanner statisics for logging.
        setStats(p, gameFile.replace("_"," ").replace(".txt", ""));

        // planners add UnitGroups to StrategicState.
        StrategicPlan allyPlan = p.makePlan(playerId, state);
        StrategicPlan opponentPlan = planners[opponentPlanner].makePlan(opponentId, state);
        simlog.removeAllAppenders();  // end simulation game event log.
        logStats(p.getStatistics(), state.getGameState(), "plan", gameFile); // log to CSV file.
        allyPlan.intialize();
        opponentPlan.intialize();
        
        SimController sim = new SimController(state);
        sim.setPlan(playerId, allyPlan);
        sim.setPlan(opponentId, opponentPlan);

        String comment = p.getChoiceMethod() + " vs. " + planners[opponentPlanner].getTemplate().getName() + " on " + gameFile + " at " + state.getCycle();
        logState(state.getGameState(), comment); // game_state.txt
        logPlannerValues(p, opponentPlanner, gameFile, state.getGameState());
        logPlan(0, allyPlan, comment);
        logPlan(1, opponentPlan, comment);
        logEvents(p,opponentPlanner); // start simulation game event log.
        sim.simulate(StrategyController.REPLAN_CYCLES);
        simlog.removeAllAppenders();  // end simulation game event log.
        comment = p.getChoiceMethod() + " vs. " + planners[opponentPlanner].getTemplate().getName() + " on " + gameFile + " at " + state.getCycle();
        logState(state.getGameState(), comment); // game_state.txt
        logScores(state);
        
        while (!sim.isTerminal()) {
            sim.update(); // remove dead units, remove commands, update groups
            if (sim_pair_log!=null) {
                // tell matrix planner which strategy pair to log.
                p.setLogEventsPair(sim_pair_log[pair_index++]);
            }
            allyPlan = p.replan(playerId, allyPlan, state);
            // currently the previous plan is not used in the GoalDrivenPlanner.
            opponentPlan = planners[opponentPlanner].replan(opponentId, null, state);
            simlog.removeAllAppenders();  // end simulation game event log.
            logStats(p.getStatistics(), state.getGameState(), "plan", gameFile); // log to CSV file.
            sim.setPlan(playerId, allyPlan);
            sim.setPlan(opponentId, opponentPlan);
            comment = p.getChoiceMethod() + " vs. " + planners[opponentPlanner].getTemplate().getName() + " on " + gameFile + " at " + state.getCycle();
            logPlannerValues(p, opponentPlanner, gameFile, state.getGameState());
            logPlan(0, allyPlan, comment);
            logPlan(1, opponentPlan, comment);
            logEvents(p,opponentPlanner); // start simulation game event log.
            sim.simulate(StrategyController.REPLAN_CYCLES);
            simlog.removeAllAppenders();  // end simulation game event log.
            comment = p.getChoiceMethod() + " vs. " + planners[opponentPlanner].getTemplate().getName() + " on " + gameFile + " at " + state.getCycle();
            logState(state.getGameState(), comment); // game_state.txt
            logScores(state); // game_scores.txt
        }
        endEpisode(p, sim.getState().getGameState(), gameFile); // log results.

        int[] scores = sim.getScores();
        return scores[0] - scores[1];
    }

    private static void setStats(SwitchingPlanner p, String map) {
        // code from StrategyController.configure()
        //
        Map params = p.getConfiguration();
        String player = (String) params.get("player");
        String opponent = (String) params.get("opponent");
        Statistics stats = new Statistics(
                String.format("%s_vs_%s_0_sim.csv", player, opponent));
        stats.setColumns(new String[] {
            "event",            // plan or end of game
            "player ID",        // the number of player being evaluated
            "player",           // planner type of the player
            "strategy",         // strategy of player being evaluated
            "simReplan",        // is SwitchingPlanner using replanning in game simulation?
            "opponent",         // planner type of the opponent
            "predicted",        // predicted winner
            "predicted diff",   //
            "actual",           // actual winner
            "diff",             // actual
            "cycle",             // cycle of this event
            "map"
        });
        p.setStatistics(stats);
    }

    protected final static String NL = System.getProperty("line.separator");
    protected final static SimpleDateFormat formatter = new SimpleDateFormat("MM-DD-HH-mm-ss");
    protected final static UnitsGameStateWriter unitsWr = new UnitsGameStateWriter();
    
    private static void logState(GameState state, String comment) {
        if (stateWr == null) {
            stateWr = new GameStateWriter();
            stateWr.setObjectWriter(GroupSim.class, new UnitGroupSimWriter());
        }
        try {
            // FileWriter out = new FileWriter("game_state_" + (game_state_log_index++) + ".txt");
            FileWriter out = new FileWriter("game_state.txt", true);
            out.write(String.format("# %s (%s)%s", comment, formatter.format(new Date()),NL));
            unitsWr.write(out, state);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write game state.");
        }
    }

    /**
     * log matrix values produced by SwitchingPlanner's simulation.
     */
    private static void logPlannerValues(SwitchingPlanner p, int opponent, String gameFile, GameState state) throws IOException {
        double[][] planner_values = p.getValues(); // get matrix produced by SwitchingPlanner simulation.
        planner_values = transpose(planner_values);

        StrategyTemplate[] strategies = p.getStrategies(); // get configured strategy set.
        String[] colhdr = new String[strategies.length];
        String[] rowhdr = new String[strategies.length];
        for (int i = 0; i < rowhdr.length; i++) {
            rowhdr[i] = i + ". " + strategies[i].getName().replace("_", " ");
        }
        for (int j = 0; j < colhdr.length; j++) {
            colhdr[j] = j + ". ";
        }
        String caption = String.format("%s vs. %s Simulation Matrix on %s at %d.",
                                      p.getChoiceMethod(),
                                      planners[opponent].getTemplate().getName(),
                                      gameFile.replace("_"," ").replace(".txt", ""),
                                      state.getCycle());
        Map<String,Integer> properties = new LinkedHashMap<String,Integer>();
        properties.put("cycle", state.getCycle());
        String label = "sim_planner_matrix_" + gameFile.replace(".txt", "");
        String fn = String.format("%s_%s_sim_%s.yaml", 
                                      p.getChoiceMethod(),
                                      planners[opponent].getTemplate().getName(),
                                      gameFile.replace(".txt", ""));
        File f = new File(dir,fn);
        BufferedWriter doc = new BufferedWriter(new FileWriter(f,true)); // append
        String comment = "table written by " + SwitchingPlannerSimulation.class.getSimpleName();
        YamlTable.write_YAML_table(properties,planner_values, colhdr, rowhdr, label, caption, doc, comment);
        doc.close();
    }

    private static double[][] transpose(double[][] v) {
        double[][] vt = new double[v[0].length][v.length];
        for (int i = 0; i < v.length; i++) {
            for (int j = 0; j < v[0].length; j++) {
                vt[j][i] = v[i][j];
            }
        }
        return vt;
    }

    private static void logPlan(int playerId, StrategicPlan plan, String comment) {
        final String filename = "plans" + playerId + ".txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            out.write("# " + comment + "\n");
            StrategicPlanWriter.write(plan, out);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException("unable to write " + filename, ex);
        }
    }

    /**
     * start game events logging to game_events.txt
     */
    private static void logEvents(SwitchingPlanner p, int opponentPlanner) {
        simlog.removeAllAppenders();
        String filename = "game_events.txt";
        Layout layout = new SimpleLayout();
        try {
            Appender append = new FileAppender(layout, filename, true);
            simlog.addAppender(append);
            simlog.setLevel(Level.INFO);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        simlog.info("SwitchingPlannerSimulation simulation of " +
                     p.getChoiceMethod() + " vs. " +
                    planners[opponentPlanner].getTemplate().getName());
    }

    public static void endEpisode(SwitchingPlanner p, GameState state, String mapPath) {
        // code from StrategyController.
        Statistics stats = p.getStatistics();
        Map params = p.getConfiguration();
        if (stats != null) {
            // log the results.
            stats.setValue("player", (String) params.get("player"));
            stats.setValue("opponent", (String) params.get("opponent"));
            stats.setValue("simReplan", (Boolean) params.get("simReplan") ? 1 : 0);
            int[] scores = state.getScores();
            if (scores[0] > scores[1]) {
                stats.setValue("actual", 0);
            } else if (scores[0] < scores[1]) {
                stats.setValue("actual", 1);
            } else {
                stats.setValue("actual", -1);
            }
            logStats(stats, state, "end", mapPath);
        }
    }

    private static void logStats(Statistics stats, GameState game, String event, String mapPath) {
        if (stats == null) {
            return;
        }
        stats.setValue("event", event);
        stats.setValue("cycle", game.getCycle());
        // game value from perspective of player 0.
        int[] scores = game.getScores();
        stats.setValue("diff", scores[0]-scores[1]);
        stats.setValue("map", mapPath);
        stats.nextRow();
    }

    private static void logScores(StrategicState state) {
        final String filename = "game_scores.txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            int[] scores = state.getGameState().getScores();
            out.write(
                String.format("cycle:%d\tscore[0]:%d\tscore[1]%d\tdiff:%d%s",
                              state.getCycle(),scores[0],scores[1],scores[0]-scores[1],NL));
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException("unable to write " + filename, ex);
        }
    }

    /** if we want the game events logged, return the
     * sequence of expected strategy choices.
     */
    private static int[][] getPairLogSequence(SwitchingPlanner p, int opponentPlanner, String gameFile) {
        // current stategies:
        // 0. dfnd-atk_7
        // 2. dfnd-atk_9
        // 6. atk-dfnd_9
        // 8.   offensive_7_mass
        String choiceMethod = p.getChoiceMethod();
        // log maximin-mod vs. dfnd-atk_7 strategy choices recorded in
        // maximin-mod_dfnd-atk_7_2bases-game.csv
        //
        if ("maximin-mod".equals(choiceMethod) && opponentPlanner == 0 &&
            "2bases-game.txt".equals(gameFile)) {
            int[][] sequence = {
                {6,0},// cycle      0 "atk-dfnd_9","dfnd-atk_7
                {6,0}, // cycle  6000 "atk-dfnd_9","dfnd-atk_7"
                {6,0}, // cycle 12000 "atk-dfnd_9","dfnd-atk_7"
            };
            return sequence;
        } else {
            return null;
        }
    }

    /**
     * find differences between simulation run in simulate() and simulation
     * run by SwitchingPlanner.  Read the output files game_events.txt and
     * game_events_sim.txt.
     */
    private static void diff() throws IOException {
        diff("game event differences:",
             "game_events.txt","game_events_sim.txt");
        diff("plans0 differences:",
             "plans0.txt","plans0_sim.txt");
        diff("plans1 differences:",
             "plans1.txt","plans1_sim.txt");
        diff("state differences:",
             "game_state.txt","game_state_sim.txt");
    }

    private static void diff(String comment, String outerFile, String innerFile)
        throws IOException {
        File f = new File(outerFile);
        if (!f.exists()) {
            System.out.println("simulation file " + outerFile + " not found.");
            return;
        }
        f = new File(innerFile);
        if (!f.exists()) {
            System.out.println("simulation file " + innerFile + " not found.");
            return;
        }
        List<String> outer_sim = fileToLines(outerFile);
        List<String> inner_sim = fileToLines(innerFile);
        Patch patch = DiffUtils.diff(outer_sim, inner_sim);
        List<Delta> dels = patch.getDeltas();
        System.out.println(comment);
        for (Delta del : dels) {
            System.out.println(del);
        }
    }

    private static List<String> fileToLines(String filename) throws IOException {
        List<String> lines = new ArrayList<String>();
        String line;
        BufferedReader in = new BufferedReader(new FileReader(filename));
        while ((line = in.readLine()) != null) {
            lines.add(line);
        }

        return lines;
    }
}
