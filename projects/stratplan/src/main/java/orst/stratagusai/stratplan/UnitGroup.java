package orst.stratagusai.stratplan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import orst.stratagusai.Unit;

/**
 * Specification of a UnitGroup.  The specification includes the type and
 * number of units that should be included in the group.
 * 
 * @author Brian
 */
public class UnitGroup {

    private int id = -1;
    private int ownerId = -1;
    /** production, combat, or peasant group.  See GroupType. */
    private String type;
    /** unit type requirements set by planner. */
    private List<Argument> unitTypeReqs = new ArrayList<Argument>();
    private Set<Unit> units = new LinkedHashSet<Unit>();
    /** representative provides group properties derived from units.
     *  This is a settable object so that we can change how
     *  representative is a calculated.
     */
    private GroupRepresentative representative;

    /**
     * inner group representative calculates properties on request.
     */
    protected class Representative implements GroupRepresentative {

        public Map<String, Integer> getUnitTypes() {
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            for (Unit u : units) {
                String utype = u.getUnitTypeString();
                if (!counts.containsKey(utype)) {
                    counts.put(utype, 1);
                } else {
                    int c = counts.get(utype);
                    counts.put(utype, c + 1);
                }
            }
            return counts;
        }

        public boolean contains(String unitType, int amount) {
            int count = 0;
            for (Unit u : units) {
                if (u.getUnitTypeString().equals(unitType)) {
                    count++;  // what if unit is dead?
                }
            }
            return count >= amount;
        }

        /** get average position */
        public int getLocX() {
            if (units.isEmpty()) {
                throw new RuntimeException("cannot get average position of empty unit group.  group " + UnitGroup.this);
            }
            int x = 0;
            for (Unit u : units) {
                if (!u.isDead()) {
                    x += u.getLocX();
                }
            }
            return Math.round(x / (float) units.size());
        }

        /** get average position */
        public int getLocY() {
            if (units.isEmpty()) {
                throw new RuntimeException("cannot get average position of empty unit group.");
            }
            int y = 0;
            for (Unit u : units) {
                if (!u.isDead()) {
                    y += u.getLocY();
                }
            }
            return Math.round(y / (float) units.size());
        }

        public int getBasicDamage() {
            int dmg = 0;
            for (Unit u : units) {
                if (!u.isDead()) {
                    dmg += u.getBasicDamage();
                }
            }
            return dmg;
        }

        public int getPiercingDamage() {
            int dmg = 0;
            for (Unit u : units) {
                if (!u.isDead()) {
                    dmg += u.getPiercingDamage();
                }
            }
            return dmg;
        }
    };

    public UnitGroup() {
    }

    public UnitGroup(int id, int ownerId) {
        this.id = id;
        this.ownerId = ownerId;
    }

    public UnitGroup(int id, int ownerId, String type) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
    }

    /** constructor does not copy GroupRepresentative */
    public UnitGroup(UnitGroup g) {
        this.id = g.id;
        this.ownerId = g.ownerId;
        this.type = g.type;
        try {
            // clone Units
            for (Unit u : g.units) {
                units.add((Unit) u.clone());
            }
            // clone unit requirements
            for (Argument a : g.unitTypeReqs) {
                unitTypeReqs.add((Argument) a.clone());
            }
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * groups are the same if the have the same ID and owner.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UnitGroup other = (UnitGroup) obj;
        if (this.id != other.id) {
            return false;
        }
        if (this.ownerId != other.ownerId) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + this.id;
        hash = 41 * hash + this.ownerId;
        return hash;
    }
    
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isProduction() {
        assert type != null;
        return GroupType.GROUP_BUILDING.equals(type)
                || GroupType.GROUP_PEASANT.equals(type);
    }

    public boolean isCombat() {
        assert type != null;
        return GroupType.GROUP_COMBAT.equals(type);
    }

    /**
     * Requirements for group set by planner.
     */
    public List<Argument> getUnitTypeReqs() {
        return unitTypeReqs;
    }

    public void addUnitTypeReq(Argument arg) {
        unitTypeReqs.add(arg);
    }

    public void addUnitTypeReq(String unitType, int number) {
        unitTypeReqs.add(new Argument(unitType, number));
    }

    /**
     * Do group units meet group requirements?
     */
    public boolean meetsUnitReqs() {
        if (unitTypeReqs.isEmpty()) {
            // a group with no requirements always meets requirements.
            return true;
        } else if (units.isEmpty()) {
            // if there are requirments and no units, then group can't meet reqs.
            return false;
        }
        // the above tests are there because
        // an empty group has no representative (null representative).
        GroupRepresentative rep = getRepresentative();
        for (Argument req : unitTypeReqs) {
            if (!rep.contains(req.getName(), req.getValue())) {
                return false;
            }
        }
        return true;
    }

    // runtime management of units
    public Set<Unit> getUnits() {
        return new LinkedHashSet<Unit>(units);
    }

    public void addUnit(Unit u) {
        units.add(u);
    }

    public boolean isEmpty() {
        return units.isEmpty();
    }

    public int size() {
        return units.size();
    }

    public void removeUnits() {
        units.clear();
        representative = null;
    }

    public boolean removeUnit(Unit u) {
        boolean b = units.remove(u);
        if (units.isEmpty()) {
            representative = null;
        }
        return b;
    }

    /**
     * get derived group properties.
     */
    public GroupRepresentative getRepresentative() {
        if (representative == null && !units.isEmpty()) {
            representative = new Representative();
        }
        return representative;
    }

    public void setRepresentative(GroupRepresentative r) {
        this.representative = r;
    }

    /**
     * get total hitpoints of units in group
     */
    public int getHitPoints() {
        int hp = 0;
        for (Unit u : units) {
            hp += u.getHitPoints();
        }
        return hp;
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("[UnitGroup ");
        buf.append(id);
        buf.append(" :type ");
        buf.append(type);
        buf.append(" :ownerId ");
        buf.append(ownerId);
        buf.append(" (:required");
        for (Argument arg : unitTypeReqs) {
            buf.append(" ");
            buf.append(arg.getName());
            buf.append(" ");
            buf.append(arg.getValue());
        }
        buf.append(") (:actual");
        if (!isEmpty()) {
            for (Map.Entry<String, Integer> entry : getRepresentative().getUnitTypes().entrySet()) {
                buf.append(" ");
                buf.append(entry.getKey());
                buf.append(" ");
                buf.append(entry.getValue());
            }
        }
        buf.append(")]");
        return buf.toString();
    }
}
