package orst.stratagusai.taclp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.apache.log4j.Logger;
import orst.stratagusai.ActionState;
import orst.stratagusai.Direction;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.command.CommandTranslator;
import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Location;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.mgr.Manager;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.RegionNode;
import orst.stratagusai.util.Randomizer;

/**
 * Manage the execution of a combat task.
 * 
 * @author Brian
 */
public class CombatGroupManager {
    private static final Logger log = Logger.getLogger(CombatGroupManager.class);

    protected Task task;

    protected UnitGroup group;

    protected Manager parent;

    protected int playerId = -1;

    protected int regionId = -1;

    /** path to target region */
    protected List<Passage> path;

    /** next region on path */
    protected Region nextRegion;

    /** direction to next region center */
    protected RealVector direction;

    protected Unit leader;

    /**
     * parameters learned by coordinate ascent on 2011-07-12.
     * Adjusted COMBAT_BLDG param from 0 to .1.
     * Parameters for Features2.
     */
    protected final double[] params = {0.5, 0.5, 0.3, 0.4, .1, 0.3, .1};

    protected WargusUnitPrototypes prototypes = new WargusUnitPrototypes();

    protected CommandTranslator tx = new CommandTranslator();

    protected Map<Unit,UnitCommand> lastCmd = new LinkedHashMap<Unit,UnitCommand>();

    public CombatGroupManager() {}

   public CombatGroupManager(Manager parent, int playerId, int regionId, Task task, UnitGroup group) {
       this.parent = parent;
       this.playerId = playerId;
       this.regionId = regionId;
       this.task = task;
       this.group = group;
    }

    public UnitGroup getGroup() {
        return group;
    }

    public void setGroup(UnitGroup group) {
        this.group = group;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public void nextActions(GameState state) {
        Region region = state.getMap().getRegion(regionId);
        // check if task is done
        Set<Unit> enemies = state.getEnemyUnits(playerId, region);
        removeDeadOrDying(enemies);
        if (isGroupDead() || (isAttackTask() && enemies.isEmpty())) {
            parent.notifyComplete(task);
            return;
        }
        enemies = getEnemiesInRange(state);
        if (!enemies.isEmpty()) {
            assignTargets(state, enemies, region);
        } else {
            moveToRegion(state, region);
        }
    }

    protected void moveToRegion(GameState state, Region region) {
        if (path == null) {
            path = findPath(state, region);
        }
        // select a leader.
        Set<Unit> units = group.getUnits();
        if (leader == null || leader.isDead() || leader.isDying()) {
            leader = null; // clear dead or dying leader.
            List<Unit> leaders = new ArrayList<Unit>();
            for (Unit u : units) {
                if (!u.isDead() && WargusUnitType.ARCHER.getName().equals(u.getUnitTypeString())) {
                    leaders.add(u);
                }
            }
            if (!leaders.isEmpty()) {
                Randomizer.shuffle(leaders);
                leader = (Unit) leaders.get(0);
            }
        }
        // get next region on path
        getNextRegion(units, region);

        // move each unit in group to next region.
        for (Unit u : units) {
            if (u.isDead()) {
                continue;
            }
            moveToRegion(state, u);
        }
    }

    protected void assignTargets(GameState state, Set<Unit> enemies, Region region) {
        // get the set of regions that the group is in.
        GameMap map = state.getMap();
        Set<Region> rs = new LinkedHashSet<Region>();
        for (Unit ally : group.getUnits()) {
            rs.add(map.getRegion(ally));
        }
        // get enemies in the regions that the group is in
        //List<Unit> enemies = new ArrayList<Unit>();
        for (Region r : rs) {
            for (Unit enemy : state.getEnemyUnits(playerId, r)) {
                if (!enemy.isDead()) {
                    enemies.add(enemy);
                }
            }
        }

        List<Unit> allies = new ArrayList<Unit>();
        for (Unit ally : group.getUnits()) {
            if (!ally.isDead() && !ally.isDying()) {
                allies.add(ally);
            }
        }

        List<Unit> enemyList = new ArrayList<Unit>(enemies);
        Features f = Features2.extract(allies, enemyList);
        Set<Assignment> assign = TargetAssigner.assign(params, f, allies, enemyList);

        // issue commands
        for (Assignment a : assign) {
            if (a.attacker.getUnitId() == a.target.getUnitId()) {
                throw new RuntimeException("unit " + a.attacker.getUnitId() + " assigned to attack itself.");
            }
            UnitCommand cmd = tx.createComUnitAttack(a.attacker.getUnitId(),
                                                     a.target.getUnitId());
            setCommand(state, a.attacker, cmd);
        }
    }

    private void moveToRegion(GameState state, Unit u) {
        assert nextRegion != null;
        if (nextRegion.contains(u.getLocX(), u.getLocY())) {
            // already in region.
            return;
        }

        if (u == leader) {
            UnitCommand cmd = tx.createComUnitMove(u.getUnitId(),
                                                   nextRegion.getX(),
                                                   nextRegion.getY());
            setCommand(state, u, cmd);
            return;
        }

        // If there is a leader
        //  If blocking the leader, move to next region.
        //  If ahead of the leader, stop (wait for leader)
        //  else follow the leader.
        // else (no leader)
        //  move to next region
        UnitCommand cmd;
        if (leader != null) {

            if (!leader.getActionState().isMoving()) {
                cmd = tx.createComUnitMove(u.getUnitId(),
                                                   nextRegion.getX(),
                                                   nextRegion.getY());
            } else if (isAheadOfLeader(u)) {
                cmd = tx.createComUnitStop(u.getUnitId());
            } else {
                cmd = tx.createComFollow(u.getUnitId(), leader.getUnitId());
            }
        } else {
            cmd = tx.createComUnitMove(u.getUnitId(),
                                               nextRegion.getX(),
                                               nextRegion.getY());
        }
        
        setCommand(state, u, cmd); 
    }

    /**
     * Used for MoveAttack ("search and destroy").
     * @return
     */
    private Set<Unit> getEnemiesInRange(GameState game) {
        // get all enemies within the range of a ballista
        Unit ballista = prototypes.getPrototype(WargusUnitType.BALLISTA.getName());
        // ballista max attack range is 8
        Set<Unit> n = game.getInRange(group.getUnits(), ballista.getMaxAttackRange());
        Iterator<Unit> itr = n.iterator();
        while (itr.hasNext()) {
            Unit u = itr.next();
            if (u.isDead() || u.getOwnerId() == playerId || u.getOwnerId() == GameState.NEUTRAL_PLAYER) {
                itr.remove();
            }
        }
        return n;
    }

    protected void removeDeadOrDying(Set<Unit> units) {
        Iterator<Unit> itr = units.iterator();
        while (itr.hasNext()) {
            Unit u = itr.next();
            if (u.isDead() || u.isDying()) {
                itr.remove();
            }
        }
    }

    private Location getRandomLocation(Region region) {
        final int RADIUS = 5;
        int x = Randomizer.nextInt(2*RADIUS) - RADIUS + region.getX();
        int y = Randomizer.nextInt(2*RADIUS) - RADIUS + region.getY();
        Location loc = new Location(x,y);
        return loc;
    }

    private boolean isAttackTask() {
        return "attack".equals(task.getType());
    }

    private boolean isGroupDead() {
        Set<Unit> units = group.getUnits();
        for (Unit u: units) {
            if (!u.isDead()) {
                return false;
            }
        }
        return true; // no live units found
    }

    private List<Passage> findPath(GameState state, Region region) {
        GameMap map = state.getMap();
        for (Unit u : group.getUnits()) {
            Region curr = map.getRegion(u); // assume all units start in same region.
            return map.getShortestPath(curr, region);
        }
        return null;
    }

    private Region getNextRegion(Set<Unit> units, Region region) {
        if (nextRegion == null) {
            if (path.isEmpty()) {
                nextRegion = region;
            } else {
                nextRegion = path.get(1).getRegionNode().getRegion();
                Region start = path.get(0).getRegionNode().getRegion();
                // direction = end - start
                direction = new ArrayRealVector(2);
                direction.setEntry(0, nextRegion.getX()-start.getX());
                direction.setEntry(1, nextRegion.getY()-start.getY());
            }
        } else {
            boolean inRegion = true;
            for (Unit u : units) {
                if (!nextRegion.contains(u.getLocX(), u.getLocY())) {
                    inRegion = false;
                    break; // not all units are in region
                }
            }
            if (inRegion && nextRegion != region) {
                Region next = null;
                for (int i = 0; i < path.size(); i++) {
                    Passage p = path.get(i);
                    if (p.getRegionNode().getRegion() == nextRegion && i < path.size()-1) {
                        next = path.get(i+1).getRegionNode().getRegion();
                    }
                }
                if (next == null) {
                    next = region;
                }
                // direction = end - start
                direction.setEntry(0, next.getX() - nextRegion.getX());
                direction.setEntry(1, next.getY() - nextRegion.getY());
                nextRegion = next;
            }
        }
        return nextRegion;
    }

    private void setCommand(GameState state, Unit u, UnitCommand cmd) {
        UnitCommand prev = lastCmd.get(u);
        if (!cmd.equals(prev)) {
            lastCmd.put(u, cmd);
            state.addCommand(cmd);
        }
    }

    private boolean isAheadOfLeader(Unit u) {
         if (direction == null) {
             return false; // direction unknown
         }
         RealVector v = new ArrayRealVector(2);

         v.setEntry(0, u.getLocX() - leader.getLocX());
         v.setEntry(1, u.getLocY() - leader.getLocY());
         return direction.dotProduct(v) > 0;
     }
}
