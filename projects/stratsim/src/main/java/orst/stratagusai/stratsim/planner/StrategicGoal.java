package orst.stratagusai.stratsim.planner;

import orst.stratagusai.stratplan.model.Region;

/**
 *
 * @author Brian
 */
public class StrategicGoal implements Comparable {
    protected GoalType type;

    /** 0 means ignore, 1 is lowest priority */
    protected int priority;
    protected Region region;

    public StrategicGoal() {}

    public static StrategicGoal getEnemyBaseGoal(StrategyTemplate template, Region region) {
        StrategicGoal g = new StrategicGoal();
        g.region = region;
        g.type = GoalType.SECURE_ENEMY_BASE;
        g.priority = template.getPriority(g.type);
        return g;
    }

    public static StrategicGoal getChokepointGoal(StrategyTemplate template, Region region) {
        StrategicGoal g = new StrategicGoal();
        g.region = region;
        g.type = GoalType.SECURE_CHOKEPOINT;
        g.priority = template.getPriority(g.type);
        return g;
    }

    public static StrategicGoal getAlliedBaseGoal(StrategyTemplate template, Region region) {
        StrategicGoal g = new StrategicGoal();
        g.priority = 2;
        g.region = region;
        g.type = GoalType.SECURE_ALLIED_BASE;
        g.priority = template.getPriority(g.type);
        return g;
    }

    StrategicGoal(GoalType goalType, int priority, Region region) {
        this.type = goalType;
        this.priority = priority;
        this.region = region;
    }

    public GoalType getType() {
        return type;
    }

    public void setType(GoalType type) {
        this.type = type;
    }

    public boolean hasChokepoint() {
        return type == GoalType.SECURE_CHOKEPOINT;
    }

    public boolean isAlliedBase() {
        return type == GoalType.SECURE_ALLIED_BASE;
    }

    public boolean isEnemyBase() {
        return type == GoalType.SECURE_ENEMY_BASE;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    /**
     * order by decreasing priority.
     */
    public int compareTo(Object o) {
        StrategicGoal g = (StrategicGoal) o;
        return g.priority - priority;
    }

    @Override
    public String toString() {
        String s = "[StrategicGoal";
        s += " priority " + priority;
        s += " region " + region;
        if (hasChokepoint()) {
            s += " hasChokepoint";
        }
        if (isEnemyBase()) {
            s += " isEnemyBase";
        }
        if (isAlliedBase()) {
            s += " isAlliedBase";
        }
        return s + "]";
    }


}
