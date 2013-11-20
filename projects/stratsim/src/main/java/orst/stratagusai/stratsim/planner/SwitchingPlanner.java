package orst.stratagusai.stratsim.planner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.stat.StatUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.mgr.StrategyController;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;
import orst.stratagusai.stratsim.analysis.GameMatrixSolver;
import orst.stratagusai.stratsim.analysis.GameSolution;
import orst.stratagusai.stratsim.analysis.GameStateEvaluator;
import orst.stratagusai.stratsim.io.UnitsGameStateWriter;
import orst.stratagusai.stratsim.io.YamlTable;
import orst.stratagusai.stratsim.model.SimController;
import orst.stratagusai.stratsim.model.SimState;
import orst.stratagusai.util.Randomizer;

/**
 * Select plans by Nash equilibrium.
 * 
 * @author Brian
 */
public class SwitchingPlanner extends BasePlanner {
    private static final Logger log = Logger.getLogger(SwitchingPlanner.class);
    private static Logger simlog = Logger.getLogger("game_sim_event");
    
    // how to choose strategy from game matrix.
    public static final String CHOOSE_NASH = "Nash";
    public static final String CHOOSE_MAXIMIN = "maximin";
    public static final String CHOOSE_MONOTONE = "monotone";
    public static final String CHOOSE_MINIMAX = "minimax";
    public static final String CHOOSE_MAX = "max";
    
    protected int playerId = -1;

    /** name of strategy set to use. */
    protected String strategy_set;

    // strategy evaluation about .25 sec.
    // 5 strategies gives 25 matrix values.  Evaluation ~ 6 seconds.
    //
    protected StrategyTemplate[] strategies;

    private int nStrats;

    protected boolean simReplan = true;

    /** the game matrix */
    protected double[][] values;

    /** planners to use for each player. It's necessary that GoalDrivenPlanners
      * not hold state between planning calls.
      */
    protected GoalDrivenPlanner [] planners;

    protected GameGoal goal = new GameGoal();

    /** player name for logging statistics */
    protected String playerName;
    
    /** opponent planner or strategy name for statistics */
    protected String opponentName;

    /** choose strategy by Nash equilibrium, maximin, or max. */
    protected String choiceMethod = CHOOSE_NASH;

    /** monotone value is the maximum of the current maximin + accumlated reward
      * and the previous monotone value. */
    protected double monotone_v = Double.NEGATIVE_INFINITY;

    /** index of previously choosen strategy */
    protected int prevStrategy = -1;

    protected GameStateEvaluator evaluator = new GameStateEvaluator();

    protected int[] log_pair;

    /** name of logfile if planner is logging matrix values. */
    protected String logMatrix;

    public SwitchingPlanner() {

    }

    /** return the named strategy set. */
    public static StrategyTemplate[] getStrategies(String name) {
        if ("atk-dfnd".equals(name)) {
            // reduced set of strategies that do not secure chokepoints.
            return new StrategyTemplate[] {
                StrategyTemplate.getDefendAttack(5, false),
                StrategyTemplate.getDefendAttack(5, true),
                StrategyTemplate.getDefendAttack(7, false),
                StrategyTemplate.getDefendAttack(7, true),
                StrategyTemplate.getAttackDefend(5, false),
                StrategyTemplate.getAttackDefend(5, true),
                StrategyTemplate.getAttackDefend(7, false),
                StrategyTemplate.getAttackDefend(7, true),
                StrategyTemplate.getOffensiveOnly(5, true),
                StrategyTemplate.getOffensiveOnly(7, true),
            };
        } else if ("2011-11-01".equals(name)) {
            // stategy set used for results on 2011-11-01.
            return new StrategyTemplate[] {
                StrategyTemplate.getDefensive(7, false),
                StrategyTemplate.getBalanced(7, false),
                StrategyTemplate.getBalanced(7, true),
                StrategyTemplate.getBalanced(9, false),
                StrategyTemplate.getBalanced(9, true),
                StrategyTemplate.getRush(7, false),
                StrategyTemplate.getRush(7, true),
                StrategyTemplate.getRush(9, false),
                StrategyTemplate.getOffensiveOnly(5, true),
                StrategyTemplate.getOffensiveOnly(7, true),
                StrategyTemplate.getSecureChokepoint(6)
            };
        } else if ("2012-02-05".equals(name)) {
            // stategy 2011-11-01 with defensive_7 removed.  defensive_7 wins
            // in simulation, but loses when played in the Stratagus Engine,
            // so it may be favored too much when it is in the strategy set.
            return new StrategyTemplate[] {
                StrategyTemplate.getBalanced(7, false),
                StrategyTemplate.getBalanced(7, true),
                StrategyTemplate.getBalanced(9, false),
                StrategyTemplate.getBalanced(9, true),
                StrategyTemplate.getRush(7, false),
                StrategyTemplate.getRush(7, true),
                StrategyTemplate.getRush(9, false),
                StrategyTemplate.getOffensiveOnly(5, true),
                StrategyTemplate.getOffensiveOnly(7, true),
                StrategyTemplate.getSecureChokepoint(6)
            };
        } else if ("synth".equals(name)) {
            // strategy set created by StrategyTrainer on 2012-01-27
            // doesn't perform as well as strategy set 2011-11-01 against
            // builtin AI script.
            //
            return new StrategyTemplate[] {
StrategyTemplate.getStrategy("synth0",1,6,0,1,true,0.70F,0.00F,0.90F,0.00F),
StrategyTemplate.getStrategy("synth1",6,5,5,0,false,0.50F,0.00F,0.80F,0.00F),
StrategyTemplate.getStrategy("synth2",4,6,5,0,false,0.00F,0.00F,-0.10F,0.00F),
StrategyTemplate.getStrategy("synth3",2,7,4,4,false,0.80F,0.00F,0.80F,0.00F),
StrategyTemplate.getStrategy("synth4",3,6,0,1,true,0.10F,0.00F,-0.20F,0.00F),
StrategyTemplate.getStrategy("synth5",9,9,0,2,true,0.30F,0.00F,-1.00F,0.00F),
StrategyTemplate.getStrategy("synth6",7,4,0,2,true,0.00F,0.00F,-0.90F,0.00F),
StrategyTemplate.getStrategy("synth7",6,8,0,1,false,0.80F,0.00F,-0.70F,0.00F),
StrategyTemplate.getStrategy("synth8",0,8,0,5,true,0.90F,0.00F,-0.20F,0.00F),
StrategyTemplate.getStrategy("synth9",0,6,0,5,true,0.00F,0.00F,0.00F,0.00F),
            };
        } else {
            throw new RuntimeException("unknown strategy set '" + name + "'");
        }
    }

    @Override
    public void configure(Map params) {
        // configure strategies
        strategy_set = (String) params.get("strategy_set");
        strategies = getStrategies(strategy_set);
        nStrats = strategies.length;
        values = new double[nStrats][nStrats];
        planners = new GoalDrivenPlanner[nStrats];
        for (int i = 0; i < strategies.length; i++) {
            planners[i] = new GoalDrivenPlanner();
            planners[i].setTemplate(strategies[i]);
        }

        // configure logging info.
        playerName = (String) params.get("player");
        opponentName = (String) params.get("opponent");
        logMatrix = (String) params.get("logMatrix");

        // configure switching method
        Boolean b = (Boolean) params.get("simReplan");
        assert b != null : "simReplan must be set in configuration.";
        simReplan = b.booleanValue();
        log.debug("simReplan=" + simReplan);
        String c = (String) params.get("choice_method");
        if (!CHOOSE_NASH.equals(c) &&
            !CHOOSE_MAXIMIN.equals(c) &&
            !CHOOSE_MONOTONE.equals(c) &&
            !CHOOSE_MINIMAX.equals(c) &&
            !CHOOSE_MAX.equals(c)) {
            throw new RuntimeException("Unknown strategy choice method " + c);
        }
        choiceMethod = c;
        log.debug("choose stratagy by " + choiceMethod);
    }

    public Map getConfiguration() {
        Map params = new LinkedHashMap();
        params.put("strategy_set", strategy_set);
        params.put("player", playerName);
        params.put("opponent", opponentName);
        params.put("simReplan", simReplan);
        params.put("choice_method", choiceMethod);
        return params;
    }

    public void setSimulationReplanning(boolean replan) {
        simReplan = replan;      
    }

    /** get the configured strategy set. */
    public StrategyTemplate[] getStrategies() {
        return strategies;
    }

    /** get game matrix values */
    public double[][] getValues() {
        return values;
    }

    public String getChoiceMethod() {
        return choiceMethod;
    }

    /**
     * log the simulated game events for strategy pair i,j.
     */
    public void setLogEventsPair(int[] pair) {
        log_pair = pair;
    }

    public StrategicPlan makePlan(int playerId, StrategicState state) {
        // initialize monotone variables.
        this.monotone_v = Double.NEGATIVE_INFINITY;
        this.prevStrategy = -1;
        return simSelect(playerId, null, state);
    }

    public StrategicPlan replan(int playerId, StrategicPlan plan, StrategicState state) {
        return simSelect(playerId, plan, state);
    }

    protected StrategicPlan simSelect(int playerId, StrategicPlan prevPlan,  StrategicState state) {
        assert playerId == 0 || playerId == 1;
        this.playerId = playerId;

        // simulate each pair of plans generated by planners.
        long start = System.currentTimeMillis();
        for (int i = 0; i < nStrats; i++) {
            for (int j = 0; j < nStrats; j++) {
                simulate(i, j, prevPlan, state);
            }
        }

        // select strategy according to equilibrium distribution.
        StrategyTemplate strategy = selectStrategy(state.getGameState());
        System.out.println(String.format("Player %d selects %s at cycle %d.",playerId, strategy.getName(), state.getCycle()));
        System.out.println(String.format("Game matrix evaluation in %.2f sec.",
                    (System.currentTimeMillis() - start)/1000.0));

        if (logMatrix != null) {
            logMatrix(state.getGameState());
        }

        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.setTemplate(strategy);
        if (prevPlan == null) {
            return planner.makePlan(playerId, state);
        } else {
            return planner.replan(playerId, prevPlan, state);
        }
    }

    /**
     * select strategy using the game matrix
     *
     * @return
     */
    protected StrategyTemplate selectStrategy(GameState game) {
        int cycle = game.getCycle();
        if (CHOOSE_NASH.equals(choiceMethod)) {
            return chooseNash(cycle);
        } else if (CHOOSE_MAXIMIN.equals(choiceMethod)) {
            return chooseMaximin(cycle);
        } else if (CHOOSE_MONOTONE.equals(choiceMethod)) {
            return chooseMonotone(game);
        } else if (CHOOSE_MINIMAX.equals(choiceMethod)) {
            return chooseMinimax(cycle);
        } else if (CHOOSE_MAX.equals(choiceMethod)) {
            return chooseMax(cycle);
        }
        throw new RuntimeException("Unknown strategy choice method " + choiceMethod);
    }

    protected StrategyTemplate chooseNash(int cycle) {

        RealMatrix A = new Array2DRowRealMatrix(values);
        // solve for row player as maximizer.
        GameSolution soln = GameMatrixSolver.solveGame(A);
        RealPointValuePair solution = soln.getRowSolution();

        double [] x = solution.getPoint();
        double r = Randomizer.nextFloat();
        double cumulative = 0;
        int i = 0;
        for (; i < x.length-1; i++) {
            cumulative += x[i];
            if (cumulative >= r) {
                break;
            }
        }
        
        StrategyTemplate selected = planners[i].getTemplate();
        logPredictionStats(selected, solution.getValue());
        return selected;
    }

    /**
     *  Choose strategy that maximizes the player's minimum payoff.
     *  This is the strategy the player chooses assuming that
     *  once the player has choosen a strategy, the opponent can
     *  choose a strategy to minimize the result.
     *  For player i, opponent j
     *
     *  argmax min V(i,j) (x)
     *     i    j
     *
     *  See "Essentials of Game Theory", Leyton-Brown, Shoham, pg. 15.
     */
    protected StrategyTemplate chooseMaximin(int cycle) {
        // find the minumum of each row (the minimum across the column players
        // choices), then choose a maximum of these minimums.
        double[] mins = new double[values.length];
        for (int row = 0; row < values.length; row++) {
            mins[row] = StatUtils.min(values[row]);
        }
        // find a maxmin.  There may be several rows with the same maximin
        // value, so we select one randomly.
        List<Integer> indexes = new ArrayList<Integer>();
        double maximin = StatUtils.max(mins);
        for (int row = 0; row < values.length; row++) {
            // if the min of this row equals the maximin,
            // we can choose the strategy of this row.
            if (StatUtils.min(values[row]) == maximin) {
                indexes.add(row);
            }
        }
        int i = (Integer) Randomizer.select(indexes);
        StrategyTemplate selected = planners[i].getTemplate();
        logPredictionStats(selected, maximin);
        return selected;
    }

    /**
     *  Choose strategy that maximizes the player's minimum payoff,
     *  but don't switch unless maximin value exceeds previous maximin value.
     *
     *  argmax min V(i,j) (x)
     *     i    j
     */
    protected StrategyTemplate chooseMonotone(GameState game) {
        int cycle = game.getCycle();
        int reward = game.getScores()[playerId]; // reward so far.
        // find the minumum of each row (the minimum across the column players
        // choices), then choose a maximum of these minimums.
        double[] mins = new double[values.length];
        for (int row = 0; row < values.length; row++) {
            mins[row] = StatUtils.min(values[row]);
        }
        // find a maxmin.  There may be several rows with the same maximin
        // value, so we select one randomly.
        List<Integer> indexes = new ArrayList<Integer>();
        double maximin = StatUtils.max(mins);
        for (int row = 0; row < values.length; row++) {
            // if the min of this row equals the maximin,
            // we can choose the strategy of this row.
            if (StatUtils.min(values[row]) == maximin) {
                indexes.add(row);
            }
        }
        int i = (Integer) Randomizer.select(indexes);
        StrategyTemplate selected;
        if (maximin + reward > monotone_v) {
            selected  = planners[i].getTemplate();
            prevStrategy = i;
            System.out.println("monotone switches to strategy " + selected.getName() + " at " + cycle + ". maximin: " + maximin + ", previous maximin:" + monotone_v);
            monotone_v = maximin + reward;
        } else {
            selected = planners[prevStrategy].getTemplate();
            System.out.println("monotone stays with strategy " + selected.getName() + " at " + cycle + ". maximin: " + maximin);
        }
        logPredictionStats(selected, maximin);
        return selected;
    }

    /**
     *  Choose a strategy that minimizes the opponents's maximum payoff.
     *  This assumes opponent chooses first and player can maximize its own score
     *  given opponent's choice.
     * 
     *  min max -V(i,j) (x)
     *   i   j
     *
     *  opponent first commits to strategy j, then player chooses strategy i to
     *  minimize opponents maximum.
     *
     *  See "Essentials of Game Theory", Leyton-Brown, Shoham, pg. 15.
     */
    protected StrategyTemplate chooseMinimax(int cycle) {
        // find the maximum of each column, then choose the
        // min of these maxima.
        double[] maxs = new double[values[0].length];
        for (int col = 0; col < values[0].length; col++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int row = 0; row < values.length; row++) {
                if (values[row][col] > max) {
                    max = values[row][col];
                }
            }
            maxs[col] = max;
        }
        int i = -1;
        double min = Double.POSITIVE_INFINITY;
        for (int col = 0; col < values.length; col++) {
            if (maxs[col] < min) {
                i = col;
                min = maxs[col];
            }
        }
        StrategyTemplate selected = planners[i].getTemplate();
        logPredictionStats(selected, min);
        return selected;
    }

    protected StrategyTemplate chooseMax(int cycle) {
        double max = Double.NEGATIVE_INFINITY;
        int i = -1;
        for (int row = 0; row < values.length; row++) {
            for (int col = 0; col < values[row].length; col++) {
                if (values[row][col] > max) {
                    i = row;
                    max = values[row][col];
                }
            }
        }
        StrategyTemplate selected = planners[i].getTemplate();
        logPredictionStats(selected, max);
        return selected;
    }

    public void simulate(int i, int j, StrategicPlan prevPlan, StrategicState s) {
        int opponentId = playerId == 0 ? 1 : 0;
        StrategicPlan allyPlan;
        StrategicPlan opponentPlan;

        SimState simstate = new SimState(s); // copy groups from StrategicState
        if (prevPlan == null) {
            allyPlan = planners[i].makePlan(playerId, simstate);
            opponentPlan = planners[j].makePlan(opponentId, simstate);
            if (logPair(i,j)) {
                logSimState(i,j,simstate);
            }
        } else {
            allyPlan = planners[i].replan(playerId, prevPlan, simstate);
            // currently the previous plan is not used in the
            // GoalDrivenPlanner.
            opponentPlan = planners[j].replan(opponentId, null, simstate);
        }
        if (logPair(i,j)) {
            logPlan(playerId,allyPlan);
            logPlan(opponentId,opponentPlan);
        }
        allyPlan.intialize();
        opponentPlan.intialize();

        SimController sim = new SimController(simstate);
        sim.setPlan(playerId, allyPlan);
        sim.setPlan(opponentId, opponentPlan);

        logEvents(i,j); // start simulation game event log.
        sim.simulate(StrategyController.REPLAN_CYCLES);
        simlog.removeAllAppenders();  // end simulation game event log.
        if (logPair(i,j)) {
            logSimState(i,j,sim.getState());
            logScores(sim.getState());
        }
        while (!sim.isTerminal()) {
            if (simReplan) {                
                sim.update(); // prepare for planning: remove dead units, remove commands, update groups.
                allyPlan = planners[i].replan(playerId, allyPlan, simstate);
                opponentPlan = planners[j].replan(opponentId, opponentPlan, simstate);
                sim.setPlan(playerId, allyPlan);
                sim.setPlan(opponentId, opponentPlan);
            }
            sim.simulate(StrategyController.REPLAN_CYCLES);
        }
        values[i][j] = evaluator.evaluate(simstate.getGameState(), playerId);
    }

    private void logPredictionStats(StrategyTemplate selected, double gameValue) {
        if (stats == null) {
            return;
        }
        stats.setValue("player ID", playerId);
        stats.setValue("player", choiceMethod);
        stats.setValue("strategy", selected.getName());
        stats.setValue("simReplan", simReplan ? 1 : 0);
        stats.setValue("opponent", opponentName);
        double v = gameValue;
        stats.setValue("predicted", v > 0 ? "0" : "1"); // assuming row player is player 0.
        stats.setValue("predicted diff", String.format("%.2f", v));
    }

    /**
     * log available strategies for documentation.
     * @throws IOException
     */
    private static void logStrategies(String name, String dir) throws IOException {
        StrategyTemplate[] strategies = SwitchingPlanner.getStrategies(name);
        String[] rowhdr = new String[strategies.length];
        String[] colhdr = {"Strategy","base","enemy","chkpt.","base","enemy", "chkpt.","Units"};
        String[][] table = new String[strategies.length][colhdr.length];
        for (int i = 0; i < strategies.length; i++) {
            StrategyTemplate s = strategies[i];
            rowhdr[i] = i + ". ";
            table[i][0] = s.getName();
            table[i][1] = String.valueOf(s.getPriority(GoalType.SECURE_ALLIED_BASE));
            table[i][2] = String.valueOf(s.getPriority(GoalType.SECURE_ENEMY_BASE));
            table[i][3] = String.valueOf(s.getPriority(GoalType.SECURE_CHOKEPOINT));
            int allyForce = s.getForce(GoalType.SECURE_ALLIED_BASE);
            int enemyBaseForce = s.getForce(GoalType.SECURE_ENEMY_BASE);
            int chokepointForce = s.getForce(GoalType.SECURE_CHOKEPOINT);
            table[i][4] = String.valueOf(allyForce);
            table[i][5] = String.valueOf(enemyBaseForce);
            table[i][6] = String.valueOf(chokepointForce);
            table[i][7] = String.valueOf(allyForce+enemyBaseForce+chokepointForce);
        }

        String caption = "Strategy Set "+ name + ".";
        String label = "strategy_defs_" + name;
        String filepath = "sw_strategies_" + name + ".yaml";
        String comment = "table written by " + SwitchingPlanner.class.getSimpleName();
        System.out.println("write strategy table to " + filepath);
        YamlTable.write_YAML_table(table, colhdr, rowhdr, label, caption, filepath, comment);
    }

    private void logSimState(int i, int j, StrategicState s) {
        final String filename = "game_state_sim.txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            UnitsGameStateWriter wr = new UnitsGameStateWriter();
            // wr.setObjectWriter(UnitGroupSim.class, new UnitGroupSimWriter());
            out.write(String.format("# simulated game state for strategy pair %d, %d.%s", i, j, NL));
            wr.write(out, s.getGameState());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void logPlan(int playerId, StrategicPlan plan) {
        final String filename = "plans" + playerId + "_sim.txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            StrategicPlanWriter.write(plan, out);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException("unable to write " + filename, ex);
        }
    }

    /**
     * log matrix values produced by SwitchingPlanner's simulation.
     */
    private void logMatrix(GameState state) {
        assert logMatrix != null;
        double[][] v = getValues(); // get matrix produced by SwitchingPlanner simulation.
        // transpose for presentation
        double[][] vt = new double[v[0].length][v.length];
        for (int i = 0; i < v.length; i++) {
            for (int j = 0; j < v[0].length; j++) {
                vt[j][i] = v[i][j];
            }
        }
        v = vt;
        String gameName = state.getMap().getName();
        if (gameName == null) {
            gameName = "";
        } else if (playerId == 1) {
            gameName += "_switched";
        } 

        String[] colhdr = new String[strategies.length];
        String[] rowhdr = new String[strategies.length];
        for (int i = 0; i < rowhdr.length; i++) {
            rowhdr[i] = i + ". " + strategies[i].getName().replace("_", " ");
        }
        for (int j = 0; j < colhdr.length; j++) {
            colhdr[j] = j + ". ";
        }
        String caption = String.format("%s vs. %s Simulation Matrix on %s at %d.",
                                      choiceMethod,
                                      opponentName,
                                      gameName.replace("_"," "),
                                      state.getCycle());
        Map<String,Integer> properties = new LinkedHashMap<String,Integer>();
        properties.put("cycle", state.getCycle());
        String label = "sim_planner_matrix_" + gameName;
        File f = new File(".",logMatrix);
        try {
            BufferedWriter doc = new BufferedWriter(new FileWriter(f,true)); // append
            String comment = "table written by " + SwitchingPlanner.class.getSimpleName();
            YamlTable.write_YAML_table(properties,v, colhdr, rowhdr, label, caption, doc, comment);
            doc.close();
        } catch (IOException e) {
            log.error("Unable to write file " + logMatrix);
        }
    }

    /**
     * Write strategy definitions to YAML file.
     */
    public static void main(String[] args) throws Exception {
        String dir = ".";
        if (args.length > 0) {
            dir = args[0];
        }
        logStrategies("atk-dfnd",dir);
        logStrategies("synth",dir);
        logStrategies("2011-11-01",dir);
        logStrategies("2012-02-05",dir);
    }

    private boolean logPair(int i, int j) {
        return log_pair != null && log_pair[0] == i && log_pair[1] == j;
    }

    private void logEvents(int i, int j) {
        if (logPair(i,j)) {
                simlog.removeAllAppenders();
                String filename = "game_events_sim.txt";
                Layout layout = new SimpleLayout();
                try {
                    Appender append = new FileAppender(layout, filename, true);
                    simlog.addAppender(append);
                    simlog.setLevel(Level.INFO);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                simlog.info("SwitchingPlanner simulation of " +
                            planners[i].getTemplate().getName() + " vs. " +
                            planners[j].getTemplate().getName());
        }
    }

    private void logScores(StrategicState state) {
        final String filename = "game_scores_sim.txt";
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

}
