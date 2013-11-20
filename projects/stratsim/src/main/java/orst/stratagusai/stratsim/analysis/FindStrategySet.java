package orst.stratagusai.stratsim.analysis;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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
 */
public class FindStrategySet {

    /**
     * Number of effective strategies required for each map.
     * The Nov. 1 2011 hand-coded strategy set had 11 strategies.
     * 
     */
    private static final int STRATEGIES_PER_MAP = 8;

    public static void main(String[] args) throws Exception {
        //Randomizer.init(12);  // initialize common randomizer for repeatability.

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
        Set<StrategyTemplate> strategies = new LinkedHashSet<StrategyTemplate>();
        for (String map : maps) {
            System.out.println("Find strategy set for " + map);
            Set<StrategyTemplate> s = findStrategySet(strategies, map);
            if (s != null) {
                strategies.addAll(s);
            } else {
                throw new RuntimeException("no strategy set found for " + map);
            }
        }
        System.out.println("Strategy Set:");
        int i = 0;
        for (StrategyTemplate s : strategies) {
            System.out.print(s.asConstructor("synth" + i));
            System.out.println(",");
            i++;
        }
    }

    public static Set<StrategyTemplate> findStrategySet(Set<StrategyTemplate> strategies, String map) {
        System.out.println("findStrategySet() in map " + map);
        Set<StrategyTemplate> S = new LinkedHashSet<StrategyTemplate>(strategies);
        // start with at least 2 strategies.
        while (S.size() < 2) {
            S.add(StrategyTrainer.nextStrategy());
        }
        int n = effective_size(S, map);
        for (int t = 0; t < 1000 && n < STRATEGIES_PER_MAP; t++) {
            StrategyTemplate c = StrategyTrainer.nextStrategy();
            // if candidate c wins against at least one strategy,
            //    then add it to S.
            for (StrategyTemplate s : S) {
                if (wins(c, s, map)) {
                    S.add(c);
                    n++;
                    break;
                }
            }
            System.out.println("effective size " + n);
        }

        if (n < STRATEGIES_PER_MAP) {
            System.out.println("Strategy set not found.");
            return null;
        } else {   
            return S;
        }
    }

    protected static boolean wins(StrategyTemplate player, StrategyTemplate opponent, String map) {

        final int playerId = 0;
        final int opponentId = 1;

        GoalDrivenPlanner p0 = new GoalDrivenPlanner();
        p0.setTemplate(player);
        GoalDrivenPlanner p1 = new GoalDrivenPlanner();

        p1.setTemplate(opponent);

        GameState s = GameStates.getGameState(map);

        PlayerGroups[] gs = GroupAnalysis.defineGroups(s);
        SimState state = new SimState(gs, s);
        StrategicPlan allyPlan = p0.makePlan(playerId, state);
        StrategicPlan opponentPlan = p1.makePlan(opponentId, state);

        long start = System.currentTimeMillis();
        SimController sim = new SimController(state);
        sim.setPlan(playerId,allyPlan);
        sim.setPlan(opponentId,opponentPlan);
        sim.simulate(StrategyController.REPLAN_CYCLES);
        while (!sim.isTerminal()) {
            sim.update(); // prepare for planning: remove dead units, remove commands, update groups.
            allyPlan = p0.replan(playerId, allyPlan, state);
            opponentPlan = p1.replan(opponentId, opponentPlan, state);
            sim.setPlan(playerId, allyPlan);
            sim.setPlan(opponentId, opponentPlan);

            sim.simulate(StrategyController.REPLAN_CYCLES);
        }
        //System.out.println("simulation takes " +
        //            (System.currentTimeMillis()-start)/1000.0 +
        //            " secs.");

        int[] scores = sim.getScores();
        return scores[0] - scores[1] > 0;
    }

    /**
     * return number of strategies in strategy set S that win against at 
     * least one other strategy in S.
     */
    private static int effective_size(Set<StrategyTemplate> S, String map) {
        int n = 0;
        for (StrategyTemplate s0 : S)
            for (StrategyTemplate s1 :S)
                if (s0 != s1)
                    if (wins(s0, s1, map))
                        n++;
        return n;
    }
}