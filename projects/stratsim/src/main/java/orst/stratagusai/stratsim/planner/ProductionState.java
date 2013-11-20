package orst.stratagusai.stratsim.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratsim.analysis.BuildState;

/**
 *
 * @author Brian
 */
public class ProductionState {
    protected Map<String,Integer> targets = new LinkedHashMap<String,Integer>();

    public void clear() {
        targets.clear();
    }

    public void setTarget(String type, int amount) {
        targets.put(type, amount);
    }

    public Map<String,Integer> getTargets() {
        return targets;
    }

    public void setTargets(BuildState state, List<Argument> unitArgs) {
        // targets are the current amounts plus the given unit arg amounts.
        clear();
        for (Argument arg : unitArgs) {
            String type = arg.getName();
            targets.put(type, state.getCount(type)+arg.getValue());
        }
    }
}
