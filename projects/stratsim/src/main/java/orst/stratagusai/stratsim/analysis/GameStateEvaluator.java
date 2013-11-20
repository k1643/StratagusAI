package orst.stratagusai.stratsim.analysis;

import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;

/**
 *
 * @author Brian
 */
public class GameStateEvaluator {

    public double evaluate(GameState state, int playerId) {
        int allyHP = 0;
        int enemyHP = 0;
        for (Unit u : state.getUnits(playerId).values()) {
            allyHP += u.getHitPoints();
        }
        for (Unit u : state.getEnemyUnits(playerId).values()) {
            enemyHP += u.getHitPoints();
        }

        return allyHP - enemyHP;
    }
}
