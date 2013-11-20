package orst.stratagusai.stratsim.model;

import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;

/**
 * ActionSecure is like ActionAttack except that it doesn't complete
 * when all enemies in region killed.
 * 
 * @author bking
 */
public class ActionSecure extends ActionAttack {
    private static final Logger log = Logger.getLogger(ActionSecure.class);
    private static final Logger simlog = Logger.getLogger("game_sim_event");
   
    public ActionSecure(Unit u, Region r) {
        this(u.getOwnerId(), u.getUnitId(), r.getId());
    }
    
    public ActionSecure(int ownerId, int unitId, int regionId) {
        super(ownerId, unitId, regionId);
        setType(CommandType.UNIT_ATTACK);
    }

    @Override
    public ActionStatus exec(Simulator sim) {
        status = ActionStatus.ACTIVE;
        GameState state = sim.getGameState();
        Set<Unit> enemies = getEnemies(state);
        Unit ally = state.getUnit(getUnitID());
        if (ally == null) {
            throw new RuntimeException("unit " + getUnitID() + " not in game state at cycle " + sim.getCycle());
        }
        if (ally.isDead()) {
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

    @Override
    public String toString() {
        return "[ActionSecure ownerId:" + ownerId + " id:" + getUnitID() + " secures region " +
                getRegionId() + "]";
    }
}
