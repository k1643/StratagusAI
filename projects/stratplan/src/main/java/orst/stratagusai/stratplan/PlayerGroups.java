package orst.stratagusai.stratplan;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The groups defined for a player.  A PlayerGroup object is shared between
 * the player's StrategicPlan and the StrategicState.
 * 
 * @author Brian
 */
public class PlayerGroups {   
    /** player's groups by group ID. */
    protected Map<Integer,UnitGroup> groups = new LinkedHashMap<Integer,UnitGroup>();
    
    protected int maxGroupId;

    public void addGroup(UnitGroup g) {
        assert !groups.containsKey(g.getId());
        groups.put(g.getId(), g);
        maxGroupId = Math.max(g.getId(), maxGroupId);
    }
    
    public UnitGroup removeGroup(int id) {
        return groups.remove(id);
    }
    
    public Set<UnitGroup> getGroups() {
        Set<UnitGroup> gs = new LinkedHashSet<UnitGroup>();
        for (UnitGroup g : groups.values()) {
            gs.add(g);
        }
        return gs;
    }
    
    public UnitGroup getGroup(int id) {
        return groups.get(id);
    }
    
    public boolean containsGroup(int id) {
        return groups.containsKey(id);
    }

    public void setGroups(Map<Integer, UnitGroup> groups) {
        this.groups.clear();
        for (UnitGroup g : groups.values()) {
            this.groups.put(g.getId(), g);
            maxGroupId = Math.max(g.getId(), maxGroupId);
        }
    }

    public void clear() {
        groups.clear();
    }
    
    /** number of groups */
    public int size() {
        return groups.size();
    }
    
    public int getMaxGroupId() {
        return maxGroupId;
    }
}
