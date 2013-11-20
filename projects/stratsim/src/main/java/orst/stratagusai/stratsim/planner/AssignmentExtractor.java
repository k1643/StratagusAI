package orst.stratagusai.stratsim.planner;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.Region;

/**
 * Get combat assignments from plan.
 *
 * @author Brian
 */
public class AssignmentExtractor {
    private static final Logger log = Logger.getLogger(AssignmentExtractor.class);

    public static Map<UnitGroup,Set<Region>> getCombatAssignments(GameMap map, StrategicPlan plan) {
        Map<UnitGroup,Set<Region>> a = new LinkedHashMap<UnitGroup,Set<Region>>();
        PlayerGroups gs = plan.getPlayerGroups();
        for (UnitGroup g: gs.getGroups()) {
            for (Task t: plan.getTasks(g)) {
                if ("secure".equals(t.getType()) || "attack".equals(t.getType())) {
                    Region r = map.getRegion(t.getTargetRegionId());
                    if (r != null) {
                        Set<Region> rs;
                        if (a.containsKey(g)) {
                            rs = a.get(g);
                        } else {
                            rs = new LinkedHashSet<Region>();
                            a.put(g, rs);
                        }
                        rs.add(r);
                        log.info("Group " + g.getId() + " previously assigned to " + r);
                    } else {
                        log.error("no region " + t.getTargetRegionId());
                    }
                }
            }
        }
        return a;
    }
}
