package orst.stratagusai.stratsim.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 * Replace Units with GroupSims.
 * 
 * @author Brian
 */
public class UnitAbstractor {

    private static final Logger log = Logger.getLogger(UnitAbstractor.class);
    private static WargusUnitPrototypes prototypes = new WargusUnitPrototypes();
    private int nextUnitId;

    private int getNextUnitId() {
        return ++nextUnitId;
    }

    /**
     * Replace Units with GroupSims using groups defined in the
     * given UnitGroups.
     *
     */
    public GameState getAbstractState(PlayerGroups[] gs, GameState state) {
        nextUnitId = 0;

        // create GroupSims for Groups.
        // use map to find location of group
        GameMap map = state.getMap();
        for (int playerId = 0; playerId < gs.length; playerId++) {
            replaceUnits(playerId, map, gs[playerId]);
        }
        state = copyGame(state); // copy without units.

        // add new GroupSims.
        for (int playerId = 0; playerId < gs.length; playerId++) {
            for (UnitGroup g : gs[playerId].getGroups()) {
                if (!g.isEmpty()) {
                    assert g.size() == 1;
                    state.addUnit((GroupSim) g.getRepresentative());
                }
            }
        }
        return state;
    }

    private void replaceUnits(int ownerId, GameMap map, PlayerGroups groups) {
        for (UnitGroup group : groups.getGroups()) {
            replaceUnits(ownerId, group, map);
        }
    }

    /**
     * Replace units in UnitGroup
     */
    private void replaceUnits(int ownerId, UnitGroup group, GameMap map) {
        if (group.isEmpty()) {
            return;
        }
        GroupSim sim = new GroupSim(getNextUnitId(), ownerId);
        sim.setUnitTypeString(group.getType());
        Set<Unit> units = group.getUnits();
        Region r = getMajorityRegionOfUnits(map, units);
        sim.setLoc(r.getX(), r.getY());

        // set aggregate armor, damage, hitpoints, etc.
        for (Unit u : units) {
            addUnit(sim, u);
        }
        // replace Units with GroupSim
        group.removeUnits();
        group.addUnit(sim);
        group.setRepresentative(sim);
    }

    private static Region getMajorityRegionOfUnits(GameMap map, Set<Unit> units) {
        // if group has no units
        //      if group spec has initial-units
        //          use region of initial-units
        //      else
        //          use region of group's producer
        //
        Map<Region, Integer> counts = new LinkedHashMap<Region, Integer>();
        for (Unit u : units) {
            Region r = map.getRegion(u);
            if (counts.containsKey(r)) {
                int c = counts.get(r);
                counts.put(r, c + 1);
            } else {
                counts.put(r, 1);
            }
        }
        Region region = null;
        int c = 0;
        for (Map.Entry<Region, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > c) {
                region = entry.getKey();
                c = entry.getValue();
            }
        }
        return region;
    }

    /** used by ActionTrain. */
    public static GroupSim createUnitGroup(int id, int ownerId, String groupType, int number, String unitType, Region r) {
        GroupSim groupSim = new GroupSim(id, ownerId);
        groupSim.setUnitTypeString(groupType);
        groupSim.setLoc(r.getX(), r.getY());

        addUnits(groupSim, unitType, number);

        return groupSim;
    }

    public static void addUnits(GroupSim group, String unitType, int number) {
        Unit prototype = prototypes.getPrototype(unitType);
        for (int i = 0; i < number; i++) {
            addUnit(group, prototype);
        }
    }

    public static void addUnit(GroupSim group, String unitType) {
        Unit prototype = prototypes.getPrototype(unitType);
        addUnit(group, prototype);
    }

    /**
     * Add a unit to the group simulation.
     */
    public static void addUnit(GroupSim group, Unit u) {
        // See "Combat Planner" doc for abstraction rules.
        WargusUnitType type = WargusUnitType.getType(u.getUnitTypeString());
        if (type == null) {
            // sometimes unit type is something like
            // 'unit-destroyed-3x3-place-water' to show dying animation.
            // ignore these for groups.
            return;
        }
        int max_speed;
        if (group.getSize() > 0) {
            max_speed = Math.min(group.getMaxSpeed(), u.getMaxSpeed());
        } else {
            max_speed = u.getMaxSpeed();
        }

        int basicDamage = group.getBasicDamage() + u.getBasicDamage();
        int piercingDamage = group.getPiercingDamage() + u.getPiercingDamage();
        int maxAttackRange = Math.max(group.getMaxAttackRange(), u.getMaxAttackRange());
        int armor = group.getArmor() + u.getArmor();
        int hitPoints = group.getHitPoints() + u.getMaxHP();

        // TODO: substract variance.
        // final int n = l.size();
        // hitPoints -= variance;

        group.setMaxSpeed(max_speed);
        group.setBasicDamage(basicDamage);
        group.setPiercingDamage(piercingDamage);
        group.setMaxAttackRange(maxAttackRange);
        group.setArmor(armor);
        group.setHitPoints(hitPoints);

        group.add(type, 1);
    }

    /**
     * copy player state and map of game.
     */
    private GameState copyGame(GameState state) {
        GameState s = new GameState();
        s.setCycle(state.getCycle());
        Set<Player> players = state.getPlayers();
        for (Player p : players) {
            p = new Player(p);
            s.addPlayer(p);
        }
        GameMap map = state.getMap();
        s.setMap(map); // share the map.

        return s;
    }
}
