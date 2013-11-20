package orst.stratagusai.stratplan.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.GroupRepresentative;
import orst.stratagusai.stratplan.GroupType;
import orst.stratagusai.stratplan.PlayerGroups;

/**
 * GameState along with player groups.
 *
 * @author Brian
 */
public class StrategicState {
    private static final Logger log = Logger.getLogger(StrategicState.class);
    
    /**  */
    protected PlayerGroups[] groups = new PlayerGroups[2];

    /** locations of groups */
    protected Map<UnitGroup,Region> regions = new LinkedHashMap<UnitGroup,Region>();

    protected GameState state;

    public StrategicState() {}

    /**
     * StrategicState.  Update groups.
     *
     * @param groups groups indexed by player ID.
     */
    public StrategicState(PlayerGroups[] groups, GameState state) {
        setGameState(state);
        setGroups(state.getMap(), groups); // set groups and regions.  remove empty groups.
        for (PlayerGroups pg : this.groups) {
            for (UnitGroup g : pg.getGroups()) {
                for (Unit u : g.getUnits()) {
                    this.state.addUnit(u);
                }
            }
        }
    }

    final protected void setGameState(GameState state) {
        this.state = new GameState();
        this.state.setCycle(state.getCycle());
        // copy Player data
        for (Player p : state.getPlayers()) {
            p = new Player(p);
            this.state.addPlayer(p);
        }
        // share the map
        this.state.setMap(state.getMap());
    }

    public GameState getGameState() {
        return state;
    }

    public int getCycle() {
        return state.getCycle();
    }

    public Set<UnitGroup> getGroups() {
        Set<UnitGroup> gs = new LinkedHashSet<UnitGroup>();
        gs.addAll(groups[0].getGroups());
        gs.addAll(groups[1].getGroups());
        return gs;
    }

    public Set<UnitGroup> getGroups(int playerId) {
        Set<UnitGroup> gs = new LinkedHashSet<UnitGroup>();
        gs.addAll(groups[playerId].getGroups());
        return gs;
    }

    public UnitGroup getGroup(int id, int ownerId) {
        return groups[ownerId].getGroup(id);
    }
    
    public Set<UnitGroup> getGroups(int playerId, Region region) {
        Set<UnitGroup> gs = new LinkedHashSet<UnitGroup>();
        for (UnitGroup g : groups[playerId].getGroups()) {
            Region r = regions.get(g);
            if (r == region) {
                gs.add(g);
            }
        }
        return gs;
    }

    private void setGroups(GameMap map, PlayerGroups[] groups) {
        this.groups = groups;
        for (int id = 0; id < groups.length; id++) {
            PlayerGroups gs = groups[id];
            for (UnitGroup g : gs.getGroups()) {
                GroupRepresentative rep = g.getRepresentative();
                Region r = map.getRegion(rep.getLocX(), rep.getLocY());
                assert r != null;
                regions.put(g, r);
            }
        }
    }

    /** add a new group and set initial region.  A new group has no units,
     *  so its Region can't be determined by the group representative.
     */
    public void addGroup(UnitGroup g, Region r) {
        assert g != null;
        assert r != null;
        groups[g.getOwnerId()].addGroup(g);
        regions.put(g, r);
    }

    /** merge incomplete combat groups that are in the same region. */
    public void updateGroups() {
        for (int playerId = 0; playerId < groups.length; playerId++) {
            updateGroups(playerId, groups[playerId], regions, state);
        }
    }

    /**
     * Merge incomplete combat groups that are in the same region.
     */
    protected void updateGroups(int playerId,
                                       PlayerGroups playerGroups,
                                       Map<UnitGroup,Region> regions,
                                       GameState state) {
        // if combat group does not meet unit type requirements
        //   and if combat group is near another combat group,
        //     merge them

        // put groups to merge into sets
        Map<Region,Set<UnitGroup>> to_merge = toMerge(playerGroups);

        // merge groups.
        int nextId = playerGroups.getMaxGroupId() + 1;
        for (Region r : to_merge.keySet()) {
            Set<UnitGroup> gs = to_merge.get(r);
            if (gs.size() > 1) {
                UnitGroup merged = new UnitGroup(nextId++, playerId);
                merged.setType(GroupType.GROUP_COMBAT);
                // remove merged groups from PlayerGroups, and add new group.
                for (UnitGroup g : gs) {
                    for (Unit u : g.getUnits()) {
                        merged.addUnit(u);
                    }
                    playerGroups.removeGroup(g.getId());
                    regions.remove(g);
                }
                playerGroups.addGroup(merged);
                regions.put(merged, r);
            }
        }

        // remove empty groups.
        for (UnitGroup g : playerGroups.getGroups()) {
            if (g.isEmpty()) {
                playerGroups.removeGroup(g.getId());
                regions.remove(g);
            }
        }
    }

    protected Map<Region,Set<UnitGroup>> toMerge(PlayerGroups playerGroups) {
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
        return to_merge;
    }
    
    public List<Unit> getUnits() {
        return state.getUnits();
    }

    public Unit getUnit(int id) {
        return state.getUnit(id);
    }

    public Set<Player> getPlayers() {
        return state.getPlayers();
    }

    public Player getPlayer(int id) {
        return state.getPlayer(id);
    }

    public GameMap getMap() {
        return state.getMap();
    }

    public Map<UnitGroup,Region> getInitialRegions() {
        return new LinkedHashMap<UnitGroup,Region>(regions);
    }

    public Region getInitialRegion(UnitGroup g) {
        return regions.get(g);
    }
    /**
     * get regions that have player production groups.
     * @param playerId
     * @return
     */
    public Set<Region> getBases(int playerId) {
        Set<Region> rs = new LinkedHashSet<Region>();
        for (UnitGroup g : groups[playerId].getGroups()) {
            if (g.getOwnerId() == playerId && g.isProduction()) {
                Region r = regions.get(g);
                assert r != null : "no initial location for " + g;
                rs.add(r);
            }
        }
        return rs;
    }

    public Set<Region> getChokepoints() {
        Set<Region> rs = new LinkedHashSet<Region>();
        GameMap map = state.getMap();
        for (Region r : map.getRegions()) {
            if (r.isChokepoint()) {
                rs.add(r);
            }
        }
        return rs;
    }
    
    /**
     * get regions occupied by any player unit.
     * @param playerId
     * @return
     */
    public Set<Region> getOccupied(int playerId) {
        Set<Region> rs = new LinkedHashSet<Region>();
        for (UnitGroup g : groups[playerId].getGroups()) {
            if (g.getOwnerId() == playerId) {
                Region r = regions.get(g);
                assert r != null : "no initial location for " + g;
                rs.add(r);
            }
        }
        return rs;
    }
}
