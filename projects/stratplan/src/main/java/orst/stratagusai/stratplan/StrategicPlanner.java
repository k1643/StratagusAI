package orst.stratagusai.stratplan;

import orst.stratagusai.stratplan.model.StrategicState;
import java.util.Map;
import orst.stratagusai.stratplan.mgr.Statistics;

/**
 *
 * Creates a plan for a given GameState.
 *
 */
public interface StrategicPlanner {

    void setStatistics(Statistics stats);
    
    void configure(Map params);

    StrategicPlan makePlan(int playerId, StrategicState state);

    StrategicPlan replan(int playerId, StrategicPlan plan, StrategicState state);
}
