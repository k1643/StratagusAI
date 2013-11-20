package orst.stratagusai.stratsim.analysis;

import orst.stratagusai.stratsim.planner.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import orst.stratagusai.Goal;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.mgr.StrategyController;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameStates;
import orst.stratagusai.stratsim.model.SimController;
import orst.stratagusai.stratsim.model.SimState;
import orst.stratagusai.util.Randomizer;

/**
 * Train a new StrategyTemplate.
 * 
 * @author Brian
 */
public class StrategyTrainer {
    private static final Logger log = Logger.getLogger(StrategyTrainer.class);
    protected final static String NL = System.getProperty("line.separator");

    protected static StrategyTemplate bestStrategy;
    
    protected static double bestMaxmin;

    protected static Goal goal = new Goal();

    protected static File resultLog;

    /** current strategies used in SwitchingPlanner */
    protected static StrategyTemplate[] templates;
    private static int nStrats = -1;

    /** game values.  Row is for trained strategies.  Columns are for current
        strategies of SwitchingPlanner.
     */
   // protected static double [][] game_matrix;

    public static void main(String [] args) throws Exception {
        Options options = new Options();
        options.addOption("m", true, "message level (0|1|2|3|off|debug|info|warn)");
        options.addOption("h", false, "help");

        Parser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(StrategyTrainer.class.getName(), options);
            System.exit(-1);
        }

       // if (cmd.hasOption("m")) {
            // loggers defined in log4j.properties resource file.
            // these levels replace the levels set in thed property file.
            //String level = cmd.getOptionValue("m");
            String level = "off";
            Logger orstlog = Logger.getLogger("orst");
            Logger msglog = Logger.getLogger("msglog");
            if ("off".equals(level) || "0".equals(level)) {
                orstlog.setLevel(Level.OFF);
                msglog.setLevel(Level.OFF);
            } else if ("debug".equals(level) || "1".equals(level)) {
                orstlog.setLevel(Level.DEBUG);
                msglog.setLevel(Level.DEBUG);
            } else if ("info".equals(level) || "2".equals(level)) {
                orstlog.setLevel(Level.INFO);
                msglog.setLevel(Level.INFO);
            } else if ("warn".equals(level) || "3".equals(level)) {
                orstlog.setLevel(Level.WARN);
                msglog.setLevel(Level.WARN);
            }
       // }

        templates = SwitchingPlanner.getStrategies("2011-11-01");
        nStrats = templates.length;

        //benchmark(); // write strategy results to benchmark_matrix.tex
        
        long start = System.currentTimeMillis();
        train();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("training takes " + elapsed/(float) (1000) + " secs.");
    }

    static public void benchmark() throws IOException {

        final int allyId = 0;
        final int enemyId = 1;

        GoalDrivenPlanner planner0 = new GoalDrivenPlanner();
        GoalDrivenPlanner planner1 = new GoalDrivenPlanner();

        double[][] game_matrix = new double[nStrats][nStrats];

        for (int i = 0; i < nStrats; i++) {       // rows
            planner0.setTemplate(templates[i]);
            for (int j = 0; j < nStrats; j++) {   // columns
                GameState state = getGameState(); // get new game state.
                planner1.setTemplate(templates[j]);

                double v = simulate(allyId, enemyId, planner0, planner1, state);
                game_matrix[i][j] = v;
            }
        }
        PrintWriter out = new PrintWriter(
                new FileWriter("benchmark_matrix.tex"));
        logMatrix(game_matrix, out);
        out.close();
        System.out.println("benchmark done.");
    }

    /**
     * 
     * @throws IOException
     */
    static public void train() throws IOException {

        final int allyId = 0;
        final int enemyId = 1;

        GoalDrivenPlanner planner0 = new GoalDrivenPlanner();       
        GoalDrivenPlanner planner1 = new GoalDrivenPlanner();

        final int N_STRATS = getNStrategies();
        log.debug("number of potential strategies is " + N_STRATS);

        String[] games = {
            "2bases-game.txt",
            "2bases_switched.txt",
            "the-right-strategy-game.txt",
            "the-right-strategy-game_switched.txt"
        };

        System.out.println("generate strategies...");      
        for (int s = 0; s < 10; s++) { // generate 10 new strategies.
            double[][] game_matrix = new double[N_STRATS][nStrats];
            bestStrategy = null;
            bestMaxmin = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < N_STRATS; i++) {       // rows
                StrategyTemplate curr = nextStrategy();
                planner0.setTemplate(curr);
                for (int k = 0; k < games.length; k++) {
                    String name = games[k];
                    GameState state = getGameState(name); // get new game state.
                    for (int j = 0; j < nStrats; j++) {   // columns
                        planner1.setTemplate(templates[j]);
                        double v = simulate(allyId, enemyId, planner0, planner1, state);
                        // cumulative moving average
                        game_matrix[i][j] = (v - game_matrix[i][j])/(float)(k+1);
                    }
                }
                // set bestStrategy if row minimum exceeds bestMaximin
                selectStrategy(game_matrix[i], curr);
            }
            System.out.println(bestStrategy.asConstructor("synth"+s));
        }
        /*
        PrintWriter out = new PrintWriter(
                new FileWriter("training_matrix.tex"));
        logMatrix(game_matrix, out);
        out.close(); */
    }

    public static double simulate(int allyId, int opponentId, GoalDrivenPlanner planner0, GoalDrivenPlanner planner1, GameState state) {

        PlayerGroups[] gs = GroupAnalysis.defineGroups(state);
        SimState s = new SimState(gs, state);

        StrategicPlan allyPlan = planner0.makePlan(allyId, s);
        StrategicPlan enemyPlan = planner1.makePlan(opponentId, s);
        allyPlan.intialize();
        enemyPlan.intialize();
        
        SimController sim = new SimController(s);
        sim.setPlan(allyId, allyPlan);
        sim.setPlan(opponentId, enemyPlan);
        sim.simulate(StrategyController.REPLAN_CYCLES);
        while (!sim.isTerminal()) {
            sim.update(); // prepare for planning: remove dead units, remove commands, update groups.
            allyPlan = planner0.replan(allyId, allyPlan, s);
            enemyPlan = planner1.replan(opponentId, enemyPlan, s);
            sim.setPlan(allyId, allyPlan);
            sim.setPlan(opponentId, enemyPlan);
            sim.simulate(StrategyController.REPLAN_CYCLES);
        }
        GameStateEvaluator evaluator = new GameStateEvaluator();
        return evaluator.evaluate(state, allyId);
    }


    protected static int getTotalHP(Collection<Unit> units) {
        int total = 0;
        for (Unit u : units) {
            total += u.getHitPoints();
        }
        return total;
    }

    private static GameState getGameState(String name) {
        GameState state = GameStates.getGameState(name);
        return state;
    }

    private static GameState getGameState() {
        GameState state;
        String[] games = {
            "2bases-game.txt",
            "2bases_switched.txt",
            "the-right-strategy-game.txt",
            "the-right-strategy-game_switched.txt"
        };
        String name = (String) Randomizer.select(games);
        state = GameStates.getGameState(name);
        return state;
    }

    /**
     * @return
     */
    private static int getNStrategies() {
        return 50;
    }

    public static StrategyTemplate nextStrategy() {
        Parameter[] params = StrategyTemplate.getParameterSpecs();
        StrategyTemplate strategy  = new StrategyTemplate();

        // first select a goal order.  The force parameters depend on the
        // goal order.
        assert "goal order".equals(params[3].getName());
        int goalOrder = (int) selectParamValue(params[3]);
        strategy.setParameter(params[3], goalOrder);
        for (int i = 0; i < params.length; i++) {
            if (i == 3) {
                // already set "goal order"
                continue;
            }
            Parameter param = params[i];
            // if force has priority zero, don't set a force number
            if ("base force".equals(param.getName()) &&
                strategy.getPriority(GoalType.SECURE_ALLIED_BASE) == 0) {
                continue;
            } else if ("enemy base force".equals(param.getName()) &&
                strategy.getPriority(GoalType.SECURE_ENEMY_BASE) == 0) {
                continue;
            } else if ("chokepoint force".equals(param.getName()) &&
                strategy.getPriority(GoalType.SECURE_CHOKEPOINT) == 0) {
                continue;
            }
            // OK! Set a paramter.
            float v = selectParamValue(param);
            strategy.setParameter(param, v);
        }
        return strategy;
    }

    private static float selectParamValue(Parameter param) {
        float v;
        float range = param.getMax()-param.getMin();
        if (param.isIntegral()) {
            // Random.nextInt()
            // int value between 0 (inclusive) and the specified value (exclusive),
            range += 1;
            v = Randomizer.nextInt((int) Math.floor(range)) + param.getMin();
        } else {
            if (range > 0) {
                range = (int) range * 10;  // change in increments of .1
                v = Randomizer.nextInt((int) range)*.1F + param.getMin();
            } else {
                v = 0;
            }
        }
        return v;
    }

    private static void selectStrategy(double[] game_row, StrategyTemplate curr) {
        double min = Float.POSITIVE_INFINITY;
        for (int j = 0; j < game_row.length; j++) {
            if (game_row[j] < min) {
                min = game_row[j];
            }
        }

        if (min > bestMaxmin) {
            bestMaxmin = min;
            bestStrategy = (StrategyTemplate) curr.clone();
        }
    }

    static void logMatrix(double[][] values, Writer out) {
        try {
            out.write("\\begin{table}\n");
            out.write("\\begin{tabular}{ r | l}\n");
            out.write("\\hline\n");
            for (int i = 0; i < templates.length; i++) {
                out.write(String.format("%d. & ", i));
                out.write(templates[i].getName().replace('_',' '));
                out.write("\\\\" + NL);
            }
            out.write("\\end{tabular}\n");
            out.write("\\caption{Strategies}\n");
            out.write("\\end{table}\n");
            out.write(NL);
            
            for (int row = 0; row < values.length; row++) {
                if (row > 0 && row % 25 == 0) {
                    out.write("\\end{tabular}\n");
                    out.write("\\caption{Strategy Values}\n");
                    out.write("\\end{table}\n");
                    out.write(NL);
                }
                if (row % 25 == 0) {
                    out.write("\\begin{table}\n");
                    out.write("\\begin{tabular}{ l | ");
                    for (int col = 0; col < values[0].length; col++) {
                        out.write("r ");
                    }
                    out.write("}\n");
                    out.write("\\hline\n");
                    out.write(NL);
                }
                //out.write(templates[row].getName().replace('_',' '));
                out.write(String.valueOf(row));

                for (int col = 0; col < values[row].length; col++) {
                    out.write(" & ");
                    out.write(String.format("%.1f",values[row][col]));
                }
                out.write("\\\\" + NL);     
            }
            out.write("\\end{tabular}\n");
            out.write("\\caption{Strategy Values}\n");
            out.write("\\end{table}\n");
            out.write(NL);
        } catch (IOException ex) {
            log.error("unable to write values table.");
        }
    }
}
