package orst.stratagusai.stratsim.mgrs;

import orst.stratagusai.stratplan.model.GameState;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.mgr.StrategyManager;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 * Responsible for managing tasks and goals while traversing the strategy plan
 * graph.
 * Other managers can be mapped to a specific task type for communication with
 * this strategy manager.
 * @author Sean
 */
public class StrategyManagerSim extends StrategyManager {
    private static final Logger log = Logger.getLogger(StrategyManagerSim.class);
 
    /**
     * Initializes internal collections. setStrategy must be called before
     * this manager is functional.
     */
    public StrategyManagerSim() {}

    /**
     * Initializes this strategy manager to handle a given strategy.
     * @param strategy
     */
    public StrategyManagerSim(StrategicPlan strategy) {
        super(strategy);
    }

    @Override
    public void initGroup(GameState state, Task task) {
        // task group should be represented by a GroupSim.
        UnitGroup group = task.getTargetGroup();
        assert group != null : "no output specification of init-group task " + task;
        assert group.size() == 1 : group + " for player " + playerId + " should have one GroupSim";
        assert group.getRepresentative() instanceof GroupSim;

        messageTaskQueue.add(task);  // task complete
    }
}
