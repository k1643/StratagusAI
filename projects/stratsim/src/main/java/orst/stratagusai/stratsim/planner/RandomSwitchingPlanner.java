package orst.stratagusai.stratsim.planner;

import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.util.Randomizer;


/**
 * Switch randomly among strategies.
 * 
 * @author Brian
 */
public class RandomSwitchingPlanner extends BasePlanner {
    private static final Logger log = Logger.getLogger(RandomSwitchingPlanner.class);

    protected String strategy_set;

    /** same stratgies as those in SwitchingPlanner */
    protected StrategyTemplate[] strategies;

    protected GoalDrivenPlanner planner = new GoalDrivenPlanner();

    /** enemy strategy name for statistics */
    protected String enemyStrategyName;

    public RandomSwitchingPlanner() {}

    @Override
    public void configure(Map params) {
        strategy_set = (String) params.get("strategy_set");
        strategies = SwitchingPlanner.getStrategies(strategy_set);
        enemyStrategyName = (String) params.get("opponent");
    }
    
    public StrategicPlan makePlan(int playerId, StrategicState state) {
        StrategyTemplate strategy = (StrategyTemplate) Randomizer.select(strategies);
        planner.setTemplate(strategy);
        logStats(playerId, strategy);
        logStrategy(playerId, strategy.getName(), state.getCycle());
        return planner.makePlan(playerId, state);
    }

    public StrategicPlan replan(int playerId, StrategicPlan plan, StrategicState state) {
        StrategyTemplate strategy = (StrategyTemplate) Randomizer.select(strategies);
        planner.setTemplate(strategy);
        logStats(playerId, strategy);
        logStrategy(playerId, strategy.getName(), state.getCycle());
        return planner.replan(playerId, plan, state);
    }

    private void logStats(int playerId, StrategyTemplate strategy) {
        if (stats != null) {
            stats.setValue("player ID", playerId);
            stats.setValue("player", "random");
            stats.setValue("strategy", strategy.getName());
            stats.setValue("opponent", enemyStrategyName);
        }
    }
}
