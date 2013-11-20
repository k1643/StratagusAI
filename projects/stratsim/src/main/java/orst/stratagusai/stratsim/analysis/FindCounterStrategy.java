package orst.stratagusai.stratsim.analysis;

import orst.stratagusai.EngineProcess;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import orst.stratagusai.GameProxy;
import orst.stratagusai.GameRunner;
import orst.stratagusai.config.ControllerConfig;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.mgr.StrategyController;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameStates;
import orst.stratagusai.stratsim.model.SimController;
import orst.stratagusai.stratsim.model.SimState;
import orst.stratagusai.stratsim.planner.GoalDrivenPlanner;
import orst.stratagusai.stratsim.planner.StrategyTemplate;

/**
 *
 * @author Brian
 */
public class FindCounterStrategy {

    /** the strategy to beat */
    private static final String strategy0 = "rush_9";

    public static void main(String[] args) throws Exception {
        // Randomizer.init(12);  // initialize common randomizer for repeatability.

        Logger orstlog = Logger.getLogger("orst");
        Logger msglog = Logger.getLogger("msglog");
        orstlog.setLevel(Level.WARN);
        msglog.setLevel(Level.WARN);

        String[] maps = {
            "2bases-game.txt",
            "2bases_switched.txt",
            "the-right-strategy-game.txt",
            "the-right-strategy-game_switched.txt"
        };

        StrategyTemplate s = null;
        for (int t = 0; t < 500 && s == null; t++) {
            System.out.println("iteration " + t + ".");
            // find counter strategy in simulation.
            s = find(maps[0]);
            if (s != null) {
                // verify that strategy wins on other maps.
                for (int i = 1; i < maps.length; i++) {
                    if (!verify(s, maps[i])) {
                        s = null;
                        break;
                    }
                }
            }
            if (s != null) {
                // verify that strategy wins in Strategus Engine.
                if (!verifyInEngine(s)) {
                    s = null;
                }
            }
        }
        if (s != null) {
            System.out.println(s.asConstructor("synth"));
        } else {
            System.out.println("no counter strategy found.");
        }
        System.out.println("Done!");
    }

    public static StrategyTemplate find(String mapFile) {

        boolean done = false;
        GoalDrivenPlanner p0 = new GoalDrivenPlanner();
        p0.configure(strategy0);
        GoalDrivenPlanner p1 = new GoalDrivenPlanner();
        GameState s = GameStates.getGameState(mapFile);

        for (int i = 0; i < 1000 && !done; i++) {
            StrategyTemplate curr = StrategyTrainer.nextStrategy();
            p1.setTemplate(curr);

            int[] scores = sim(p0, p1, s);
            
            System.out.println("Game value " + (scores[0] - scores[1]));

            if (scores[0] - scores[1] < 0) {
                System.out.println("Found counter-strategy on map " + mapFile + ": " + p1.getTemplate());
                done = true;
            }
        }
        if (!done) {
            System.out.println("No counter-strategy found.");
            return null;
        }
        return p1.getTemplate();
    }

    /**
     * verify that
     * @param mapFile
     * @return
     */
    private static boolean verify(StrategyTemplate strategy1, String mapFile) {
        GoalDrivenPlanner p0 = new GoalDrivenPlanner();
        p0.configure(strategy0);
        GoalDrivenPlanner p1 = new GoalDrivenPlanner();
        p1.setTemplate(strategy1);
        GameState s = GameStates.getGameState(mapFile);

        int[] scores = sim(p0, p1, s);

        return scores[1] > scores[0];
    }

    private static int[] sim(GoalDrivenPlanner p0, GoalDrivenPlanner p1, GameState s) {

        final int playerId = 0;
        final int opponentId = 1;

        PlayerGroups[] gs = GroupAnalysis.defineGroups(s);
        SimState state = new SimState(gs, s);
        StrategicPlan allyPlan = p0.makePlan(playerId, state);
        StrategicPlan opponentPlan = p1.makePlan(opponentId, state);

        SimController sim = new SimController(state);
        sim.setPlan(playerId, allyPlan);
        sim.setPlan(opponentId, opponentPlan);
        sim.simulate(StrategyController.REPLAN_CYCLES);
        while (!sim.isTerminal()) {
            sim.update(); // prepare for planning: remove dead units, remove commands, update groups.
            allyPlan = p0.replan(playerId, allyPlan, state);
            opponentPlan = p1.replan(opponentId, opponentPlan, state);
            sim.setPlan(playerId, allyPlan);
            sim.setPlan(opponentId, opponentPlan);

            sim.simulate(StrategyController.REPLAN_CYCLES);
        }
        return sim.getScores();
    }

    private static boolean verifyInEngine(StrategyTemplate s) throws IOException {

        String[] mapPaths = {"../../maps/2bases.smp",
                             "../../maps/2bases_switched.smp",
                             "../../maps/the-right-strategy.smp",
                             "../../maps/the-right-strategy_switched.smp"};
        boolean verified = true;
        for (String mapPath : mapPaths) {
            // start engine
            Process proc = EngineProcess.startStratagus(false);

            // start client
            GameRunner loop= configure(s, mapPath);
            // add game lister to track average scores.
            GameListener c = new GameListener();
            loop.addController(c);
            loop.getGame().connect();
            loop.run();
            loop.getGame().killStratagus();
            loop.getGame().closeGameProxySocket();
            proc.destroy();

            // did generated strategy win (on average)?
            float r = c.getWinRate(1);
            System.out.println("win rate " + r);
            verified = r > .5;
            if (!verified) {
                break;
            }
        }
        return verified;
    }

    private static GameRunner configure(StrategyTemplate s, String mapPath) {
        GameProxy game = new GameProxy();
        GameRunner loop = new GameRunner(game);
        String[] mapPaths = { mapPath };
        loop.setMaxCycles(80000);
        loop.setEpisodes(3);
        loop.setMapPaths(mapPaths);
        loop.setShowVideo(false);
        loop.setWarpSpeed(true);

        // get player controllers
        final int N_PLAYERS = 2;
        for (int playerId = 0; playerId < N_PLAYERS; playerId++) {
            StrategyController c = new StrategyController();
            c.setPlayerId(playerId);
            c.configure(getControllerConfig(playerId));
            if (playerId == 1) {
                ((GoalDrivenPlanner)c.getPlanner()).setTemplate(s);
            }
            loop.addController(c); // controller for player i
        }
        return loop;
    }

    private static ControllerConfig getControllerConfig(int playerId) {
        ControllerConfig config = new ControllerConfig();
        Map<String,Object> params = new LinkedHashMap<String,Object> (); // (Map) conf.getParam("planner");
        params.put("tactical", "orst.stratagusai.taclp.TacticalManager");
        params.put("production", "orst.stratagusai.prodh.ProductionManager");
        if (playerId == 0) {
            Map<String,Object> plannerParams = new LinkedHashMap<String,Object> ();
            plannerParams.put("className", "orst.stratagusai.stratsim.planner.GoalDrivenPlanner");
            plannerParams.put("strategy", strategy0);
            params.put("planner", plannerParams);
        } else {
            Map<String,Object> plannerParams = new LinkedHashMap<String,Object> ();
            plannerParams.put("className", "orst.stratagusai.stratsim.planner.GoalDrivenPlanner");
            plannerParams.put("strategy", "balanced_7"); // dummy value, replace strategy later.  could set strategy parameters here.
            params.put("planner", plannerParams);
        }
        config.setParams(params);
        return config;
    }
}
