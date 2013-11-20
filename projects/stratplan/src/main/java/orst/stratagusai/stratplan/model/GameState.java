package orst.stratagusai.stratplan.model;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.GameProxy;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.UnitEvent;
import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.persist.GameStateReader;
import orst.stratagusai.stratplan.persist.GameStateWriter;

/**
 * GameState.
 * Call update to synchronize with the given GameProxy.
 */
public class GameState {
    private static final Logger log = Logger.getLogger(GameState.class);

    public static final int NEUTRAL_PLAYER = 15;
    
    protected GameMap map;
    
    /** units by id. */
    protected Map<Integer, Unit> units = new LinkedHashMap<Integer, Unit>();
    
    protected int cycle;

    protected Map<Integer,Player> players = new LinkedHashMap<Integer,Player>();

    /**
     * Set of commands. Use a LinkedHashSet for a repeatable order.  A
     * repeatable iteration order is useful for testing.
     */
    protected Set<UnitCommand> commands = new LinkedHashSet<UnitCommand>();

    /** events since last update */
    protected Set<UnitEvent> events = new LinkedHashSet<UnitEvent>();

    /** Unit distance calculations */
    protected Plane plane;
    
    public GameState() {}

    /**4
     * Synchronizes with given GameProxy to create a snapshot of game state.
     */
    public void update(GameProxy game) {
        assert plane != null;
        cycle = game.getCurrentCycle();

        // sync players
        Set<Player> ps = game.getPlayers();
        for (Player p : ps) {
            players.put(p.getId(), p);
        }

        // sync units
        Map<Integer, Unit> us = game.getUnits();
        for (Unit u : us.values()) {
            if (!units.containsKey(u.getUnitId())) {
                addUnit(u);
            }
        }
        setEvents(game.getEvents());

        plane.update(); // update distances.
    }

    public GameMap getMap() {
        return map;
    }

    public void setMap(GameMap map) {
        this.map = map;
        this.plane = new Plane(map.getExtentX(), map.getExtentY());
    }

    public int getCycle() {
        return cycle;
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
    }

    public void addUnit(Unit u) {
        if (units.containsKey(u.getUnitId())) {
            throw new IllegalArgumentException("attempt to add duplicate unit id=" + u.getUnitId());
        }
        units.put(u.getUnitId(), u);
        plane.add(u);
    }

    public Unit getUnit(int id) {
        return units.get(id);
    }

    public Unit getUnit(int x, int y) {
        for (Unit u : units.values()) {
            if (u.getLocX() == x && u.getLocY() == y) {
                return u;
            }
        }
        return null;
    }

    public void removeUnit(Unit u) {
        units.remove(u.getUnitId());
        plane.remove(u);
    }

    public void removeDeadUnits() {
        Iterator<Unit> itr = units.values().iterator();
        while (itr.hasNext()) {
            Unit u = itr.next();
            if (u.isDead()) {
                itr.remove();
                plane.remove(u);
            }
        }
    }

    public Map<Integer, Unit> getUnits(int playerId) {
        Map<Integer,Unit> us = new LinkedHashMap<Integer,Unit>();
        for (Unit unit : units.values()) {
            if (unit.getOwnerId() == playerId) {
                us.put(unit.getUnitId(), unit);
            }
        }
        return us;
    }

    public Map<Integer, Unit> getEnemyUnits(int playerId) {
        Map<Integer,Unit> us = new LinkedHashMap<Integer,Unit>();
        for (Unit unit : units.values()) {
            if (unit.getOwnerId() != playerId &&
                unit.getOwnerId() != NEUTRAL_PLAYER) {
                us.put(unit.getUnitId(), unit);
            }
        }
        return us;
    }

    /** get enemy units in the given Region */
    public Set<Unit> getEnemyUnits(int playerId, Region r) {
        Set<Unit> us = new LinkedHashSet<Unit>();
        for (Unit unit : units.values()) {
            if (unit.getOwnerId() != playerId &&
                unit.getOwnerId() != NEUTRAL_PLAYER &&
                r.contains(unit.getLocX(), unit.getLocY())) {
                us.add(unit);
            }
        }
        return us;
    }

    public Map<Integer, Unit> getNeutralUnits() {
        Map<Integer,Unit> us = new LinkedHashMap<Integer,Unit>();
        for (Unit unit : units.values()) {
            if (unit.getOwnerId() == NEUTRAL_PLAYER) {
                us.put(unit.getUnitId(), unit);
            }
        }
        return us;
    }

    public List<Unit> getUnits() {
        return new ArrayList<Unit>(units.values());
    }

    /** used to reconstruct game state */
    public void setUnits(List<Unit> os) {
        this.units.clear();
        //this.players_units.clear();
        for (Unit u : os) {
            addUnit(u);
        }
    }

    /**
     * get units at given region.
     */
    public Set<Unit> getUnits(Region r) {
        return map.getUnits(units.values(), r);
    }

    /**
     * get the units of a player at the given region
     */
    public Set<Unit> getUnits(int playerId, Region r) {
        return map.getUnits(units.values(), playerId, r);
    }

    /** get units in range of the given group. */
    public Set<Unit> getInRange(Set<Unit> units, float range) {
        return plane.getInRange(units, range);
    }


    /**
     * get regions controlled by enemies.  TODO: this may be too much
     * analysis to put into the GameState.
     * 
     * @param playerId
     * @return
     */
    public Set<Region> getEnemyRegions(int playerId) {
        return map.getEnemyRegions(units.values(), playerId);
    }

    public Set<Player> getPlayers() {
        Set<Player> ps = new LinkedHashSet<Player>();
        ps.addAll(players.values());
        return ps;
    }

    public Player getPlayer(int id) {
        return players.get(id);
    }

    public void addPlayer(Player p) {
        players.put(p.getId(), p);
    }

    public void addCommand(UnitCommand command) {
        commands.add(command);
    }

    public void removeCommand(UnitCommand command) {
        commands.remove(command);
    }

    public Set<UnitCommand> getCommandQueue() {
        return this.commands;
    }

    public Set<UnitEvent> getEvents() {
        return events;
    }

    public void setEvents(Set<UnitEvent> events) {
        this.events.clear();
        this.events.addAll(events);
    }

    public void setEvents(List<UnitEvent> events) {
        this.events.clear();
        this.events.addAll(events);
    }

    public void addEvent(UnitEvent evt) {
        events.add(evt);
    }

    public int[] getScores() {
        // assume two players
        // calculate remaining hitpoints
        final int nPlayers = 2;
        int [] scores = new int[nPlayers];
        for (int i = 0; i < nPlayers; i++) {
            Map<Integer,Unit> us = getUnits(i);
            for (Unit u : us.values()) {
                scores[i] += u.getHitPoints();
            }
        }
        return scores;
    }
}
