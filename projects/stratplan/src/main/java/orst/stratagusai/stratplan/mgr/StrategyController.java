package orst.stratagusai.stratplan.mgr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameMap;
import org.apache.log4j.Logger;
import orst.stratagusai.Controller;
import orst.stratagusai.GameProxy;
import orst.stratagusai.config.ControllerConfig;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlanner;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.command.CommandTranslator;
import orst.stratagusai.stratplan.persist.GameMapReader;
import orst.stratagusai.stratplan.persist.StrategicPlanDOT;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;

/**
 *
 * @author sean
 */
public class StrategyController implements Controller {
    private static final Logger log = Logger.getLogger(StrategyController.class);
    private static final String NL = System.getProperty("line.separator");
    
    /** cycles between re-planning. */
    public static final int REPLAN_CYCLES = 6000;

    protected int playerId = -1;
    protected StrategyManager stratMgr;
    /** strategy planner */
    protected StrategicPlanner planner;
    
    /** game map file path for statistics */
    protected String mapPath;
    /** StrategicState has GameState and groups defined for the planners. */
    protected StrategicState state;
    private int lastPlanningCycle;
    private boolean replan = true;

    // information for statistics
    private Statistics stats;
    private String playerName;
    private String opponentName;
    private boolean simReplan;

    public void setPlayerId(int id) {
        // setPlayerId() is called in stratagusai-client Main
        // before configure().
        this.playerId = id;
    }

    public void configure(ControllerConfig conf) {
        assert playerId != -1 : "player id not set.";
        stratMgr = new StrategyManager();
        stratMgr.setPlayerId(playerId);
        // add managers for each task type.
        Manager stratman = createManager((String) conf.getParam("tactical"));
        stratMgr.addManager(stratman, "attack");
        stratMgr.addManager(stratman, "secure");
        Manager prodman = createManager((String) conf.getParam("production"));
        stratMgr.addManager(prodman, "produce");

        Map plannerParams = (Map) conf.getParam("planner");
        planner = createPlanner(plannerParams);

        Boolean b = (Boolean) conf.getParam("re-plan");
        if (b != null) {
            replan = b;
        }
        String statsfile = (String) conf.getParam("stats");
        if (statsfile != null) {
            playerName = (String) plannerParams.get("player");
            assert playerName != null : "null player name. Needed for statistics.";
            opponentName = (String) plannerParams.get("opponent");
            b = (Boolean) plannerParams.get("simReplan");
            if (b != null) {
                simReplan = b.booleanValue();
            }
            stats = new Statistics(statsfile + "_" + playerId + ".csv");
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
                "diff",
                "cycle",             // cycle of this event
                "map"
            });
            planner.setStatistics(stats);
        }
    }

    public StrategicPlanner getPlanner() {
        return planner;
    }

    public void nextActions(GameProxy gameProxy) {
        assert stratMgr != null : "strategy manager not configured.";
        mergeGameState(gameProxy);
        GameState game = state.getGameState();
        if (replan && gameProxy.getCurrentCycle() > lastPlanningCycle + REPLAN_CYCLES) {
            /*try {
                GameStateWriter.write(state, "game_state_" + System.currentTimeMillis() + ".txt");
            } catch (IOException ex) {
                log.error("Unable to write game state.");
            }*/
            logPlanState(stratMgr.getPlan());
            // redefine groups?  new units will have been created by engine commands.
            // are the new units added to groups by game events?
            // TODO: check for units that are not in groups?
            //  clear old commands?
            // game.getCommandQueue().clear();
            state.updateGroups(); // merge incomplete combat groups in same regions.
            StrategicPlan plan = planner.replan(playerId,
                                                stratMgr.getPlan(),
                                                state);
            logStats(game.getScores(),"plan");
            logPlan(plan, game);
            stratMgr.setPlan(plan);
            lastPlanningCycle = gameProxy.getCurrentCycle();
        }
        stratMgr.nextActions(game);

        // Managers have placed Commands in the GameState command queue.
        // Get the commands and execute them in the GameProxy.
        CommandTranslator.sendUnitCommands(game.getCommandQueue(), gameProxy);

        game.getCommandQueue().clear();
        game.getEvents().clear();
    }

    public StrategicState getState(GameProxy gameProxy) {
        GameMap m = getGameMap(gameProxy);
        m.setCells(gameProxy.getMapCells());

        GameState game  = new GameState();
        game.setMap(m);
        game.update(gameProxy);

        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        StrategicState s = new StrategicState(gs, game);
        return s;
    }

    public void mergeGameState(GameProxy game) {
        // add any new units.
        state.getGameState().update(game);
    }

    public void beginSession(GameProxy game) {
    }

    public void beginCycle(GameProxy game, boolean training) {
    }

    public void beginEpisode(GameProxy gameProxy) {
        // set strategy here.
        assert stratMgr != null : "strategy manager not configured.";
        assert playerId != -1;
        mapPath = gameProxy.getMapPath();
        state = getState(gameProxy);
        GameState game = state.getGameState();
        lastPlanningCycle = game.getCycle();
        StrategicPlan plan = planner.makePlan(playerId, state);
        logStats(game.getScores(), "plan");
        logPlan(plan, game);
        stratMgr.setPlan(plan);
        stratMgr.beginEpisode(game);
/*        try {
            GameStateWriter.write(state, "game_state_" + System.currentTimeMillis() + ".txt");
        } catch (IOException ex) {
            log.error("Unable to write game state.");
        } */
    }

    public void endEpisode(GameProxy gameProxy) {
        mergeGameState(gameProxy);
        stratMgr.endEpisode(state.getGameState());

        if (stats != null) {
            // log the results.
            stats.setValue("player", playerName);
            stats.setValue("opponent", opponentName);
            stats.setValue("simReplan", simReplan ? 1 : 0);
            int[] scores = state.getGameState().getScores();
            if (scores[0] > scores[1]) {
                stats.setValue("actual", 0);
            } else if (scores[0] < scores[1]) {
                stats.setValue("actual", 1);
            } else {
                stats.setValue("actual", -1);
            }
            logStats(scores, "end");
        } 
    }

    public void endCycle(GameProxy game, boolean training) {
    }

    public void endSession(GameProxy game) {
    }

    protected Manager createManager(String className) {
        assert className != null : "no class name given";
        try {
            Manager mgr = (Manager) Class.forName(className).newInstance();
            return mgr;
        } catch (InstantiationException ex) {
            log.fatal("unable to create manager '" + className + "'", ex);
        } catch (IllegalAccessException ex) {
            log.fatal("unable to create manager '" + className + "'", ex);
        } catch (ClassNotFoundException ex) {
            log.fatal("unable to create manager '" + className + "'", ex);
        }
        throw new RuntimeException("unable to create manager '" + className + "'");
    }

    protected StrategicPlanner createPlanner(Map params) {
        String className = (String) params.get("className");
        try {
            StrategicPlanner p =
                    (StrategicPlanner) Class.forName(className).newInstance();
            p.configure(params);
            return p;
        } catch (InstantiationException ex) {
            log.fatal("unable to create planner '" + className + "'", ex);
        } catch (IllegalAccessException ex) {
            log.fatal("unable to create planner '" + className + "'", ex);
        } catch (ClassNotFoundException ex) {
            log.fatal("unable to create planner '" + className + "'", ex);
        }
        throw new RuntimeException("unable to create planner '" + className + "'");
    }

    private void logPlan(StrategicPlan plan, GameState game) {
        if (!log.isDebugEnabled()) {
            return;
        }
        final String dotfile = "plan_" + playerId + "_" + System.currentTimeMillis() + ".dot";
        try {
            String label = String.format("Strategy %s, Cycle %d, Time %s",
                                          plan.getName(),
                                          game.getCycle(),
                                          DateFormat.getTimeInstance().format(new Date()));
            StrategicPlanDOT.writeDOTFile(plan, label, dotfile);
        } catch (IOException ex) {
            log.error("unable to write " + dotfile, ex);
        }
        final String filename = "plans" + playerId + ".txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            StrategicPlanWriter.write(plan, out);
            out.close();
        } catch (IOException ex) {
            log.error("unable to write " + filename, ex);
        }
    }

    private void logPlanState(StrategicPlan plan) {
        if (!log.isDebugEnabled()) {
            return;
        }
        Set<Task> tasks = plan.getActiveTasks();
        final String filename = "plans" + playerId + ".txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            out.write("# player " + playerId + " " + plan.getName() + " active tasks:" + NL);
            out.write("#" + NL);
            for (Task t : tasks) {
                if (!t.isComplete()) {
                    out.write("# " + t + " using " + t.getUsingGroup() + NL);
                }
            }
            out.close();
        } catch (IOException ex) {
            log.error("unable to write " + filename, ex);
        }
    }

    /**
     * Match given map to pre-coded map.
     */
    private GameMap getGameMap(GameProxy game) {
        if (game.getMapWidth() == 64) {
            GameMap map = getGameMap("one-way-map.txt");
            map.setName("2bases");
            if (cellsMatch(map.getCells(), game.getMapCells())) {
                log.debug("load map one-way-map.txt");
                return map;
            }
            map = getGameMap("the-right-strategy-map.txt");
            map.setName("the-right-strategy");
            if (cellsMatch(map.getCells(), game.getMapCells())) {
                log.debug("load map the-right-strategy-map.txt");
                return map;
            }             
        } else if (game.getMapWidth() == 128) {
            log.debug("three-ways-to-cross-map.txt");
            return getGameMap("three-ways-to-cross-map.txt");
        }
        throw new RuntimeException("unknown game map of width " + game.getMapWidth());
    }

    public static GameMap getGameMap(String filename) {
        // load from resource file.
        InputStream is =
                ClassLoader.getSystemResourceAsStream(filename);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        try {
            return GameMapReader.load(br);
        } catch (IOException e) {
            throw new RuntimeException("unable to load " + filename + " as resource");
        }
    }
    

    private void logStats(int[] scores, String event) {
        if (stats == null) {
            return;
        }
        stats.setValue("event", event);
        stats.setValue("cycle", state.getCycle());
        // game value from perspective of player 0.
        stats.setValue("diff", scores[0]-scores[1]);
        stats.setValue("map", mapPath);
        stats.nextRow();
    }

    private boolean cellsMatch(char[][] cells1, char[][] cells2) {
        assert cells1.length == cells2.length;
        final int lim = 3; // verify only 3 rows.
        for (int row = 0; row < cells1.length && row < lim; row++) {
            for (int col = 0; col < cells1[row].length; col++) {
                if (cells1[row][col] != cells2[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }
}
