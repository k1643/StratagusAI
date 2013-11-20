package orst.stratagusai.stratsim.planner;

import java.util.Comparator;
import java.util.Set;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.model.StrategicState;

/**
 * Sort goals by priority.
 *
 * @author Brian
 */
public class GoalComparator implements Comparator {

    protected int playerId;

    protected StrategicState state;

    public GoalComparator(int playerId, StrategicState state) {
        this.playerId = playerId;
        this.state = state;
    }

    /**
     * Collections.sort() will sort ascending, so a positive return
     * value means the first object will come after the second.
     *
     * @return Returns negative integer, zero, or a positive integer as the
     * first argument is less than, equal to, or greater than the second.
     */
    public int compare(Object o1, Object o2) {
        StrategicGoal g1 = (StrategicGoal) o1;
        StrategicGoal g2 = (StrategicGoal) o2;

        int p = g2.getPriority() - g1.getPriority(); // higher priority should
                                                     // be sorted lower in list.
        if (p == 0) {
            // if priorities are the same, then order by
            // production strength.
            Region region1 = g1.getRegion();
            Region region2 = g2.getRegion();
            double r1 = getEnemyProductionStrength(state, region1);
            double r2 = getEnemyProductionStrength(state, region2);
            return (int) Math.signum(r1 - r2); // weaker should be sorted lower
                                               //   in list.
        } else {
            return p;
        }
    }

    private double getEnemyProductionStrength(StrategicState game,
                                              Region region) {
        int opponentId = playerId == 0 ? 1 : 0;
        Set<UnitGroup> gs = game.getGroups(opponentId, region);
        int hp = 0;
        for (UnitGroup g : gs) {
            if (g.isProduction()) {
                hp += g.getHitPoints();
            }
        }
        return hp;
    }
}
