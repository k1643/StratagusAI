package orst.stratagusai.stratsim.model;

import java.util.Iterator;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;

/**
 *
 * @author bking
 */
public class ActionAttack extends ActionMove {
    private static final Logger log = Logger.getLogger(ActionAttack.class);
    private static final Logger simlog = Logger.getLogger("game_sim_event");
   
    public ActionAttack(Unit u, Region r) {
        this(u.getOwnerId(), u.getUnitId(), r.getId());
    }
    
    public ActionAttack(int ownerId, int unitId, int regionId) {
        super(ownerId, unitId, regionId);
        setType(CommandType.UNIT_ATTACK);
    }

    @Override
    public ActionStatus exec(Simulator sim) {
        status = ActionStatus.ACTIVE;
        GameState state = sim.getGameState();
        Unit ally = state.getUnit(getUnitID());
        assert ally != null : "no unit " + getUnitID() + " in game state.";
        Set<Unit> enemies = getEnemies(state);        
        if (ally.isDead() || enemies.isEmpty() && isInTargetRegion(ally, state)) {
            status = ActionStatus.COMPLETE;
            return status;
        }

        if (!enemies.isEmpty()) {
            assignTargets(sim, enemies);
        } else {
            moveToRegion(sim);
        }

        return status;
    }

    /** get enemies in current region */
    public Set<Unit> getEnemies(GameState state) {
        Unit ally = state.getUnit(getUnitID());
        if (ally == null) {
            return null;
        }
        Region region = state.getMap().getRegion(ally);
        Set<Unit> enemies = state.getEnemyUnits(ally.getOwnerId(), region);
        Iterator<Unit> itr = enemies.iterator();
        while (itr.hasNext()) {
            Unit enemy = itr.next();
            if (enemy.isDead()) {
                itr.remove();
            }
        }
        return enemies;
    }

    public void assignTargets(Simulator sim, Set<Unit> enemies) {

        GameState s = sim.getGameState();
        Unit ally = s.getUnit(getUnitID());
        Region region = sim.getState().getMap().getRegion(ally);

        // divide damage equally among enemies.
        //
        final int M = enemies.size();
        for (Unit enemy : enemies) {
            String msg = ally.getOwnerId() + ":" + ally.getUnitId() + " attacks " + enemy.getOwnerId() + ":" + enemy.getUnitId() + " " + enemy.getUnitTypeString() + " in region " + region.getId();
            simlog.info(s.getCycle() + "\t" + msg);
            int damage = Math.max(Math.round(Attack.getDamage(ally, enemy) / (float) M), 1);
            if (damage > 0) {
                sim.addChange(new AttributeDifference(enemy.getUnitId(),
                                                      "hitPoints",
                                                      -damage));
                simlog.info(s.getCycle() + "\t" + "damage -" + damage + " to enemy " +
                               enemy.getOwnerId() + ":" + enemy.getUnitId() + " " + enemy.getUnitTypeString() + " in region " + region.getId());
            }
        }
    }

    public void moveToRegion(Simulator sim) {
        super.exec(sim);
        status = ActionStatus.ACTIVE; // not done yet.
    }


    @Override
    public String toString() {
        return "[ActionAttack ownerId:" + ownerId + " id:" + getUnitID() + " attacks region " +
                getRegionId() + "]";
    }

    private boolean isInTargetRegion(Unit ally, GameState state) {
        Region region = state.getMap().getRegion(getRegionId());
        return region.contains(ally.getLocX(), ally.getLocY());
    }
}
