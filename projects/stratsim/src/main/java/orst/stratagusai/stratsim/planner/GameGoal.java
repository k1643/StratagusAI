package orst.stratagusai.stratsim.planner;

import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;

/**
 * The goal class tests when an episode is done.  "episodeDone() is called
 * in the GameRunner game loop.
 *
 * @author bking
 */
public class GameGoal {

    private static final Logger log = Logger.getLogger(GameGoal.class);

    public boolean episodeDone(GameState game) {
        // check if all units of any player are dead.
        if (unitsAreDead(game, 0)) {
            log.debug("player 1 wins");
            return true;
        }
        if (unitsAreDead(game, 1)) {
            log.debug("player 0 wins");
            return true;
        }
        return false;
    }

    protected boolean unitsAreDead(GameState game, int playerId) {
        Map<Integer, Unit> units = game.getUnits(playerId);
        int dead = 0;
        for (Unit u : units.values()) {
            if (u.isDead()) {
                dead++;
            }
        }
        if (dead == units.size()) {
            return true;
        }
        return false;
    }
}
