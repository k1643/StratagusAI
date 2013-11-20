package orst.stratagusai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.util.Randomizer;

/**
 * agent sends attackers to random enemy and continues battle to the death.
 *
 * @author kingbria
 */
public class RandomAgent extends BaseController {
    private static final Logger log = Logger.getLogger(RandomAgent.class);

    public RandomAgent() {}

    public RandomAgent(int playerId) {
        this.playerId = playerId;
    }

    @Override
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
        if (aliveEnemies.isEmpty()) {
            log.debug("found no enemy to attack");
            return;
        }
        // for each friendly unit, select enemy unit to attack
        int attacks = 0;
        for (Unit ally : myUnits.values()) {
            if (ally.isDead()) {
                continue;
            }
            Unit enemy = enemyUnits.get(ally.getCurrentTarget());
            if (enemy != null && !enemy.isDead()) {
                log.debug(ally.getUnitId() + " keeps attacking " + enemy.getUnitId());
                attacks++;
                continue;
            }
            // choose enemy
            int r = Randomizer.nextInt(aliveEnemies.size());
            int targetId = aliveEnemies.get(r);
            game.myUnitCommandAttack(ally.getUnitId(), targetId);
            //ally.setCurrentTarget(targetId);
            attacks++;
        }
        log.debug("attacking " + attacks + " units.");
    }
}
