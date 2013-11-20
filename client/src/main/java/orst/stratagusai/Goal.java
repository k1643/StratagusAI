package orst.stratagusai;

import java.util.Map;
import org.apache.log4j.Logger;

/**
 * The goal class tests when an episode is done.  "episodeDone() is called
 * in the GameRunner game loop.
 *
 * @author bking
 */
public class Goal {

    private static final Logger log = Logger.getLogger(Goal.class);

    /** episode is over if either player has zero hitpoints
     *  or one player has more than 3 times as many as the other.
     * 
     */
    public boolean episodeDone(GameProxy game) {
        int hp0 = getHitPoints(game, 0);
        int hp1 = getHitPoints(game, 1);
        if (hp0 == 0 && hp1 == 0) {
            log.debug("Both players dead.");
            return true;
        } else if (hp0 == 0 || hp0 * 3 < hp1) {
            log.debug("player 1 wins");
            return true;
        } else if (hp1 == 0 || hp1 * 3 < hp0) {
            log.debug("player 0 wins");
            return true;
        }
        return false;
    }

    protected int getHitPoints(GameProxy game, int playerId) {
        int hp = 0;
        Map<Integer, Unit> units = game.getUnits(playerId);
        for (Unit u : units.values()) {
            hp += u.getHitPoints();
        }
        return hp;
    }
}
