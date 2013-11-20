package orst.stratagusai.stratsim.planner;

import orst.stratagusai.stratplan.UnitGroup;

/**
 *
 * @author Brian
 */
public class Assignment {
    UnitGroup group;
    StrategicGoal goal;

    public Assignment(UnitGroup group, StrategicGoal goal) {
        this.group = group;
        this.goal = goal;
    }

    @Override
    public String toString() {
        return "group " + group.getId() + " assigned goal " + goal + ". ";
    }
}
