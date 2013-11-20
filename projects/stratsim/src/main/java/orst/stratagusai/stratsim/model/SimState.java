package orst.stratagusai.stratsim.model;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.GroupRepresentative;
import orst.stratagusai.stratplan.GroupType;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratsim.analysis.UnitAbstractor;

/**
 * Subclass of StrategicState that replaces Units with a Representative
 * GroupSim.
 *
 * @author Brian
 */
public class SimState extends StrategicState {
    private static final Logger log = Logger.getLogger(SimState.class);

    public SimState(PlayerGroups[] gs, GameState s) {
        // set groups, regions, state.
        groups = gs;
        setRegions(gs, s);
        // replace units with GroupSims in UnitGroups and state.
        UnitAbstractor a = new UnitAbstractor();
        state = a.getAbstractState(gs, s);

        verifyGroups();
    }

    public SimState(StrategicState s) {
        // set groups, regions, state.
        groups[0] = new PlayerGroups();
        groups[1] = new PlayerGroups();
        if (s instanceof SimState) {
            // clone groups and add.
            Map<Integer,Unit> units = new LinkedHashMap<Integer,Unit>();
            for (UnitGroup g : s.getGroups()) {
                UnitGroup ng = new UnitGroup(g);  // clones units, but not representative.
                groups[ng.getOwnerId()].addGroup(ng);
                if (!ng.isEmpty()) {
                    // if group is non-empty it should have a single GroupSim.
                    // This GroupSim has been cloned, and the clone has
                    // to be set as the representative.
                    assert ng.size() == 1 : "non-empty GroupSim has " + ng.size() + " units.";
                    // is old GroupSim in old state?
                    //GroupSim sim = (GroupSim) g.getUnits().iterator().next();
                    //assert sim != null;
                    //assert s.getGameState().getUnit(sim.getUnitId()) != null;
                    GroupSim nsim = (GroupSim) ng.getUnits().iterator().next();
                    ng.setRepresentative(nsim);
                    units.put(nsim.getUnitId(), nsim);
                }
            }
            // set regions.
            for (UnitGroup g : s.getGroups()) {
                Region r = s.getInitialRegion(g);
                UnitGroup ng = getGroup(g.getId(), g.getOwnerId());
                assert ng != null;
                assert r != null;
                regions.put(ng, r);
            }
            // set state.  Add units to new state in same order as old state
            // so states can be compared.
            setGameState(s.getGameState());
            for (Unit u : s.getGameState().getUnits()) {
                Unit un = units.get(u.getUnitId());
                // if unit is dead it will not be in a group.
                // TODO: should Simulator remove dead units from state?
                assert un != null || u.isDead();
                if (un != null) {
                    state.addUnit(un);
                }
            }
        } else {
            // copy from StrategicState
            for (UnitGroup g : s.getGroups()) {
                UnitGroup ng = new UnitGroup(g);  // clones units
                groups[ng.getOwnerId()].addGroup(ng);
            }
            GameMap map = s.getMap();
            for (Map.Entry<UnitGroup,Region> entry : s.getInitialRegions().entrySet()) {
                UnitGroup g = entry.getKey();
                assert s.getGroup(g.getId(), g.getOwnerId()) != null : "group " + g.getId() + " in StrategicState regions but not in PlayerGroups";
                UnitGroup ng = getGroup(g.getId(), g.getOwnerId());
                Region r = map.getRegion(entry.getValue().getId());
                assert ng != null : "group " + g.getId() + " in StrategicState regions but not in PlayerGroups";
                assert r != null;
                regions.put(ng, r);
            }            
            // replace units with GroupSims in UnitGroups and state.
            UnitAbstractor a = new UnitAbstractor();
            state = a.getAbstractState(groups, s.getGameState());            
        }
        
        // assert no empty groups.
        for (int playerId = 0; playerId < groups.length; playerId++) {
            for (UnitGroup g : groups[playerId].getGroups()) {
                assert !g.isEmpty();
            }
        }
        
        verifyGroups();
        verifyUnits();
    }

    protected int getNextUnitId() {
        int id = 0;
        for (Unit u : state.getUnits()) {
            id = Math.max(id, u.getUnitId());
        }
        return ++id;
    }

    @Override
    protected void updateGroups(int playerId,
                                       PlayerGroups playerGroups,
                                       Map<UnitGroup,Region> regions,
                                       GameState state) {
        verifyUnits();
        // if combat group does not meet unit type requirements
        //   and if combat group is near another combat group,
        //     merge them

        // put groups to merge into sets
        Map<Region,Set<UnitGroup>> to_merge = toMerge(playerGroups);

        // merge groups.
        int nextId = playerGroups.getMaxGroupId() + 1;
        for (Region r : to_merge.keySet()) {
            Set<UnitGroup> gs = to_merge.get(r);
            // don't merge unless there is more than one group.
            if (gs.size() > 1) {
                UnitGroup merged = new UnitGroup(nextId++, playerId);
                merged.setType(GroupType.GROUP_COMBAT);
                GroupSim sim = new GroupSim(getNextUnitId(), merged.getOwnerId());
                sim.setUnitTypeString(merged.getType());
                sim.setLoc(r.getX(), r.getY());
                merged.addUnit(sim);
                merged.setRepresentative(sim);
                // remove merged groups from PlayerGroups, and add new group.
                for (UnitGroup g : gs) {
                    for (Unit u : g.getUnits()) {
                        addUnit(sim, (GroupSim) u);
                        state.removeUnit(u);
                    }
                    playerGroups.removeGroup(g.getId());
                    regions.remove(g);
                }
                playerGroups.addGroup(merged);
                regions.put(merged, r);
                state.addUnit(sim);
            }
        }

        // remove empty groups.
        for (UnitGroup g : playerGroups.getGroups()) {
            if (g.isEmpty()) {
                playerGroups.removeGroup(g.getId());
                regions.remove(g);
            }
        }
        verifyGroups();
        verifyUnits();
    }

    @Override
    public void addGroup(UnitGroup g, Region r) {
        super.addGroup(g, r);

        // TODO: GroupSim with maxspeed=0 created for empty group.
        // Must allow empty group to have no units?  Fix addUnit to ignore 0 maxspeed?

        // convert group representative to GroupSim.
        // code from UnitAbstractor.replaceUnits().
        if (!(g.getRepresentative() instanceof GroupSim)) {
            GroupSim sim = new GroupSim(getNextUnitId(), g.getOwnerId());
            sim.setUnitTypeString(g.getType());
            sim.setLoc(r.getX(), r.getY());
            // remove group units from state.
            // set group aggregate armor, damage, hitpoints, etc.
            for (Unit u : g.getUnits()) {
                UnitAbstractor.addUnit(sim, u);
                state.removeUnit(u);
            }
            // replace group units with group sim in state.
            g.removeUnits();
            g.addUnit(sim);
            g.setRepresentative(sim);
            state.addUnit(sim);
        }
        log.debug("added " + g);
    }
    
    /**
     * verify that groups contain GroupSim representatives.
     */
    protected void verifyGroups() {
        for (int i = 0; i < groups.length; i++) {
            for (UnitGroup g : groups[i].getGroups()) {
                assert g.size() == 0 || g.size() == 1;
                if (g.size() == 1) {
                    assert g.getRepresentative() instanceof GroupSim : g;
                    Unit u = g.getUnits().iterator().next();
                    GroupSim rep = (GroupSim) g.getRepresentative();
                    if (u != rep) {
                        if (u != null && rep != null) {
                            System.out.println("unit:" + u.hashCode() + " rep:" + rep.hashCode());

                        }
                    }
                    assert rep == u : "representative " + rep + ", unit " + u;
                } else if (g.size() == 0) {
                    assert g.getRepresentative() == null;
                }
            }
        }
    }

    /**
     * verify that all live units are in a group.
     */
    public void verifyUnits() {
        // get all units
        Set<Unit> us = new LinkedHashSet<Unit>(state.getUnits());
        int n = us.size();
        // remove units that are in groups.
        for (int i = 0; i < groups.length; i++) {
            for (UnitGroup g : groups[i].getGroups()) {
                for (Unit u : g.getUnits()) {
                    us.remove(u);
                }
            }
        }
        // remove dead or neutral units
        Iterator<Unit> itr = us.iterator();
        while (itr.hasNext()) {
            Unit u = itr.next();
            if (u.isDead() || u.getOwnerId() == GameState.NEUTRAL_PLAYER) {
                itr.remove();
            }
        }

        // all units accounted for.
        if (!us.isEmpty()) {
            StringBuffer msg = new StringBuffer(us.size() + " live units are not in groups. [");
            for (Unit u : us) {
                msg.append("(id:" + u.getUnitId() + ",owner:" + u.getOwnerId() + ")");
            }
            msg.append("]. ");
            msg.append(n + " units in state.");
            assert us.isEmpty() : msg.toString();
        }
    }

    private void setRegions(PlayerGroups[] gs, GameState s) {
        GameMap map = s.getMap();
        for (int i = 0; i < gs.length; i++) {
            for (UnitGroup g : gs[i].getGroups()) {
                GroupRepresentative rep = g.getRepresentative();
                Region r = map.getRegion(rep.getLocX(), rep.getLocY());
                assert r != null;
                regions.put(g, r);
            }
        }
    }


    /** merge properties of sim2 into sim1. */
    protected void addUnit(GroupSim sim1, GroupSim sim2) {
        // code from UnitAbstractor.addUnit().
        // See "Combat Planner" doc for abstraction rules.
        if (sim2.isEmpty()) {
            return; // nothing to merge.
        }
        int max_speed;
        if (!sim1.isEmpty()) {
            max_speed = Math.min(sim1.getMaxSpeed(), sim2.getMaxSpeed());
        } else {
            max_speed = sim2.getMaxSpeed();
        }

        int basicDamage = sim1.getBasicDamage() + sim2.getBasicDamage();
        int piercingDamage = sim1.getPiercingDamage() + sim2.getPiercingDamage();
        int maxAttackRange = Math.max(sim1.getMaxAttackRange(), sim2.getMaxAttackRange());
        int armor = sim1.getArmor() + sim2.getArmor();
        int hitPoints = sim1.getHitPoints() + sim2.getMaxHP();

        // TODO: substract variance.
        // final int n = l.size();
        // hitPoints -= variance;

        sim1.setMaxSpeed(max_speed);
        sim1.setBasicDamage(basicDamage);
        sim1.setPiercingDamage(piercingDamage);
        sim1.setMaxAttackRange(maxAttackRange);
        sim1.setArmor(armor);
        sim1.setHitPoints(hitPoints);

        //sim1.add(sim2.getUnitTypeString(), 1);
        // here's where merging is different from UnitAbstractor.addUnit().
        for (Map.Entry<String,Integer> entry : sim2.getUnitTypes().entrySet()) {
            sim1.add(entry.getKey(), entry.getValue());
        }
    }
}
