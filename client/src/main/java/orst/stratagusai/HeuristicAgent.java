package orst.stratagusai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.config.ControllerConfig;
import orst.stratagusai.util.Randomizer;

/**
 * agent sends attackers to nearest enemy and continues battle to death.
 * @author kingbria
 */
public class HeuristicAgent implements Controller {

    private static final Logger log = Logger.getLogger(HeuristicAgent.class);

    /** the player this agent plays for */
    private int playerId;

    public HeuristicAgent() {}

    public HeuristicAgent(int playerId) {
        this.playerId = playerId;
    }

    public void configure(ControllerConfig conf) {}
    
    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public void nextActions(GameProxy game) {
        // find alive units
        Map<Integer, Unit> myUnits = game.getUnits(playerId);
        Map<Integer, Unit> enemyUnits = game.getEnemyUnits(playerId);

        // find alive enemy units
        List<Integer> aliveEnemies = new ArrayList<Integer>();
        for (Unit enemy : enemyUnits.values()) {
            if (!enemy.isDead()) {
                aliveEnemies.add(enemy.getUnitId());
            }
        }

        // for each friendly unit, select enemy unit to attack
        int attacks = 0;
        for (Unit ally : myUnits.values()) {
            if (ally.isDead()) {
                continue;
            }
            // choose enemy
            int targetId = -1;
            if (Randomizer.nextFloat() > .28) {
                targetId = nearestEnemy(game, ally);
            } else if (!aliveEnemies.isEmpty()) {
                int r = Randomizer.nextInt(aliveEnemies.size());
                targetId = aliveEnemies.get(r);
            }

            if (targetId >= 0) {
                game.myUnitCommandAttack(ally.getUnitId(), targetId);
                //ally.setCurrentTarget(targetId);
                attacks++;
            }
        }
        log.debug("attacking " + attacks + " units.");
    }

    public void beginSession(GameProxy game) {}

    public void beginCycle(GameProxy game, boolean training) {}

    public void beginEpisode(GameProxy game) {}

    public void endEpisode(GameProxy game) {}

    public void endCycle(GameProxy game, boolean training) {}

    public void endSession(GameProxy game) {}

    private int nearestEnemy(GameProxy game, Unit curr) {
        Map<Integer, Unit> enemyUnits = game.getEnemyUnits(playerId);
        float min = Float.POSITIVE_INFINITY;
        int closestId = -1;
        for (Unit enemy : enemyUnits.values()) {
            if (enemy.isDead()) {
                continue;
            }
            float d = distance(curr.getLocX(), curr.getLocY(),
                    enemy.getLocX(), enemy.getLocY());
            if (d < min) {
                closestId = enemy.getUnitId();
                min = d;
            }
        }
        return closestId;
    }

    private float distance(int x1, int y1, int x2, int y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }
}
