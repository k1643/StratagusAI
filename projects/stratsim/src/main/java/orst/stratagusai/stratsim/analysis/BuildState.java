package orst.stratagusai.stratsim.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 * Count units to know that state of a player's development.
 * 
 */
public class BuildState implements Cloneable {

    protected Map<String,Integer> count = new LinkedHashMap<String,Integer>();

    public void countUnits(GameState game, int playerId) {
        Map<Integer, Unit> units = game.getUnits(playerId);
        for (Unit u : units.values()) {
            if (u instanceof GroupSim) {
                Map<String, Integer> counts = ((GroupSim)u).getUnitTypes();
                for (Map.Entry<String,Integer> entry : counts.entrySet()) {
                    increment(entry.getKey(), entry.getValue());
                }
            } else {
                increment(u.getUnitTypeString());
            }
        }
    }

    public void increment(String type) {
        increment(type,1);
    }

    public void increment(String type, int amount) {
        if (!count.containsKey(type)) {
            count.put(type, amount);
        } else {
            int n = count.get(type);
            count.put(type, n+amount);
        }
    }

    public boolean exists(String type) {
        return getCount(type) > 0;
    }

    public int getCount(String type) {
        if (!count.containsKey(type)) {
            return 0;
        } else {
            return count.get(type);
        }
    }

    /**
     * return true if the state's resources exceed the goal resources.
     * @param goal
     * @return
     */
    public boolean meets(BuildState goal) {
        for (Map.Entry<String,Integer> entry : goal.count.entrySet()) {
            if (getCount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * return name of unit type that does not meet goal amounts.
     */
    public String unmet(BuildState goal) {
        for (Map.Entry<String,Integer> entry : goal.count.entrySet()) {
            if (getCount(entry.getKey()) < entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public Object clone() {
        try {
            BuildState s = (BuildState) super.clone();
            s.count = new LinkedHashMap<String,Integer>();
            s.count.putAll(count);
            return s;
        } catch(CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        StringBuffer s = new StringBuffer("[BuildState");
        for (Map.Entry<String,Integer> entry : count.entrySet()) {
            s.append(" ");
            s.append(entry.getKey());
            s.append(" ");
            s.append(entry.getValue());
        }
        s.append("]");
        return s.toString();
    }
}
