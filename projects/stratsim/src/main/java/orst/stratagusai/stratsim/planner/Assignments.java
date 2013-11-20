package orst.stratagusai.stratsim.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.Region;

/**
 * Assignments of groups to target regions.
 * @author Brian
 */
public class Assignments {
    protected Map<UnitGroup,List<StrategicGoal>> a = new LinkedHashMap<UnitGroup,List<StrategicGoal>>();

    public void addAssignment(UnitGroup g, StrategicGoal goal) {
        List<StrategicGoal> goals;
        if (a.containsKey(g)) {
            goals = a.get(g);
        } else {
            goals = new ArrayList<StrategicGoal>();
            a.put(g, goals);
        }
        goals.add(goal);
    }

    public List<StrategicGoal> getGoals(UnitGroup g) {
        if (a.containsKey(g)) {
            return a.get(g);
        }
        return new ArrayList<StrategicGoal>();
    }

    public List<Region> getTargetRegions(UnitGroup g) {
         List<Region> rids = new ArrayList<Region>();
         List<StrategicGoal> goals = a.get(g);
         if (goals != null) {
            for (StrategicGoal goal : goals) {
                rids.add(goal.getRegion());
            }
         }
         return rids;
    }

    public boolean isAssigned(UnitGroup g, int regionId) {
        List<StrategicGoal> goals = a.get(g);
        if (goals != null) {
            for (StrategicGoal goal : goals) {
                if (goal.getRegion().getId() == regionId) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }


    public boolean isAssigned(StrategicGoal goal) {
        for (List<StrategicGoal> goals : a.values()) {
            for (StrategicGoal g : goals) {
                if (g == goal) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clear() {
        a.clear();
    }
}
