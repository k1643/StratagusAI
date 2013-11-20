package orst.stratagusai.stratsim.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.GroupRepresentative;

/**
 *
 * @author Brian
 */
public class GroupSim extends Unit implements GroupRepresentative, Cloneable {
    private static final Logger log = Logger.getLogger(GroupSim.class);

    /** type and number of units in the group */
    private Map<String,Integer> unitTypes = new LinkedHashMap<String,Integer>();

    public GroupSim(int id, int ownerId) {
        super(id);
        setOwnerId(ownerId);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        GroupSim g = (GroupSim) super.clone();
        g.unitTypes = new LinkedHashMap<String,Integer>(g.unitTypes);
        return g;
    }
    
    /** add a unit type to the group sim */
    public void add(String unitType, int number) {
        // TODO: types should not be tied to game.  Should be able to use other type like StratagusUnitType.
        WargusUnitType type = WargusUnitType.getType(unitType);
        if (type == null) {
            throw new RuntimeException("unknown unit type " + unitType);
        }
        add(type, number);
    }

    /** add a unit type to the group sim */
    public void add(WargusUnitType type, int number) {
        String typeStr = type.getName();
        if (unitTypes.containsKey(typeStr)) {
            number += unitTypes.get(typeStr);
        }
        unitTypes.put(typeStr, number);
    }

    public void remove(String unitType, int number) {
        if (!unitTypes.containsKey(unitType)) {
            log.warn("attempt to remove units from group that has none of type " + unitType);
            return;
        }
        number = unitTypes.get(unitType) - number;
        if (number < 0) {
            unitTypes.put(unitType, 0);
        } else {
            unitTypes.put(unitType, number);
        }
    }

    /**
     * does the group contain at least the given number of the given type?
     */
    public boolean contains(String unitType, int amount) {
        if (amount < 1) {
            return true;
        }
        if (!unitTypes.containsKey(unitType)) {
            return false;
        }
        return amount <= unitTypes.get(unitType);
    }

    public Map<String,Integer> getUnitTypes() {
        return unitTypes;
    }

    public int getSize() {
        int size = 0;
        for (Integer count : unitTypes.values()) {
            size += count;
        }
        return size;
    }

    public boolean isEmpty() {
        return getSize() == 0;
    }

    /**
     * remove unit counts.
     */
    public void clear() {
        unitTypes.clear();
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();

        buf.append("[GroupSim ");
        buf.append(" id:" + getUnitId());
        buf.append(" type:" + getUnitTypeString());
        buf.append(" owner: " + getOwnerId());
        buf.append(" ("+getLocX()+","+getLocY()+")");
        buf.append(" HitPoints:" + getHitPoints());
        for (Map.Entry<String,Integer> entry : unitTypes.entrySet()) {
            buf.append(" ");
            buf.append(entry.getKey());
            buf.append(" ");
            buf.append(entry.getValue());
        }
        buf.append("]");
        return buf.toString();
    }
}
