package orst.stratagusai.stratplan.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratplan.GroupType;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.UnitGroup;

/**
 * Cluster units into groups.
 *
 * @author Brian
 */
public class GroupAnalysis {
    
    /**
     * create UnitGroups for two players.
     */
    public static PlayerGroups[] defineGroups(GameState state) {    
        PlayerGroups[] gs = {
            defineGroups(0, state),
            defineGroups(1, state)
        };
        return gs;
    }
    
    /** 
     *  1. Merge incomplete combat groups that are in the same region.
     *  2. Remove empty groups.  Groups become empty when units die.
     * 
     *  update groups in place. 
     */
    /*
    public static void updateGroups(PlayerGroups[] groups, GameState state) {    
        for (int playerId = 0; playerId < groups.length; playerId++) {
            updateGroups(playerId, groups[playerId], state);
        }
    } */

    /**
     * Create UnitGroups of player's Units.
     * 
     * @param playerId
     * @param state
     * @return
     */
    public static PlayerGroups defineGroups(int playerId, GameState state) {
        int nextGroupId = 0;
        PlayerGroups groups = new PlayerGroups();
        GameMap map = state.getMap();

        //
        // for each region create a 
        //     production group for the production units in that region and a
        //     combat group for the combat groups in that region.
        //
        for (Region region : map.getRegions()) {
            Set<Unit> units = state.getUnits(region);
            UnitGroup combat = null;
            UnitGroup prod = null;
            for (Unit u : units) {
                if (!u.isDead() && u.getOwnerId() == playerId) {
                    if (UnitAnalysis.isCombat(u)) {
                        if (combat == null) {
                            combat = new UnitGroup(++nextGroupId,
                                                   playerId,
                                                   GroupType.GROUP_COMBAT);
                        }
                        combat.addUnit(u);
                    } else if (UnitAnalysis.isBuilding(u)
                            || UnitAnalysis.isPeasant(u)) {
                        if (prod == null) {
                            prod = new UnitGroup(++nextGroupId,
                                                 playerId,
                                                 GroupType.GROUP_BUILDING);
                        }
                        prod.addUnit(u);
                    }
                }
            }
            // add unit requirement information to groups.
            // TODO: why do we need this?
            if (combat != null) {
                UnitGroup group = createGroupSpec(combat);
                groups.addGroup(group);
            }
            if (prod != null) {
                UnitGroup group = createGroupSpec(prod);
                groups.addGroup(group);
            }
        }

        return groups;
    }

    /**
     * Merge incomplete combat groups that are in the same region.
     */
    /*
    public static void updateGroups(int playerId, PlayerGroups playerGroups, GameState state) {
        // if combat group does not meet unit type requirements
        //   and if combat group is near another combat group,
        //     merge them
        GameMap map = state.getMap();
        Map<Region,Set<UnitGroup>> to_merge = new LinkedHashMap<Region,Set<UnitGroup>>();
        for (UnitGroup g : playerGroups.getGroups()) {
            if (!g.isEmpty() && g.isCombat() && !g.meetsUnitReqs()) {
                int x = g.getRepresentative().getLocX();
                int y = g.getRepresentative().getLocY();
                Region r = map.getRegion(x, y);
                if (!to_merge.containsKey(r)) {
                    Set<UnitGroup> gs = new LinkedHashSet<UnitGroup>();
                    gs.add(g);
                    to_merge.put(r, gs);
                } else {
                    to_merge.get(r).add(g);
                }
            }
        }
        
        // TODO: merge up to a maximum?
        // merge groups.
        int nextId = playerGroups.getMaxGroupId() + 1;
        for (Region r : to_merge.keySet()) {
            Set<UnitGroup> gs = to_merge.get(r);
            UnitGroup merged = new UnitGroup(nextId++, playerId);
            merged.setType(GroupType.GROUP_COMBAT);
            for (UnitGroup g : gs) {
                for (Unit u : g.getUnits()) {
                    merged.addUnit(u);
                }
                playerGroups.removeGroup(g.getId());
            }
            // remove merged groups from PlayerGroups, and add new group.
            playerGroups.addGroup(merged);         
        }
        
        // remove empty groups.
        for (UnitGroup g : playerGroups.getGroups()) {
            if (g.isEmpty()) {
                playerGroups.removeGroup(g.getId());
            }
        }
    } */

    /** add unit type requirement specification to groups. */
    private static UnitGroup createGroupSpec(UnitGroup group) {
        Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
        for (Unit u : group.getUnits()) {
            String type = u.getUnitTypeString();
            increment(counts,type);
        }
        for (Map.Entry<String,Integer> entry : counts.entrySet()) {
            Argument arg = new Argument(entry.getKey(), entry.getValue());
            group.addUnitTypeReq(arg);
        }
        return group;
    }

    private static void increment(Map<String,Integer> counts, String type) {
        if (!counts.containsKey(type)) {
            counts.put(type, 1);
        } else {
            int i = counts.get(type);
            counts.put(type, i+1);
        }
    }
}
