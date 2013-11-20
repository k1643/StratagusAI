package orst.stratagusai.stratsim.model;

import java.util.Map;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.Unit;

/**
 * Game is over when one player has all the living Units.
 *
 * @author bking
 */
public class CombatEvaluator implements GameEvaluator {

    protected GameState game;

    public CombatEvaluator() {}

    public void setGame(GameState game) {
        this.game = game;
    }
    

    /** get scores for players.  the score is the remaining hitpoints. */
    public int[] getScores() {
        // assume two players
        // calculate remaining hitpoints
        final int nPlayers = 2;
        int [] scores = new int[nPlayers];
        for (int i = 0; i < nPlayers; i++) {
            scores[i] = getHitPoints(i);
        }
        return scores;
    }

    /**
     *  game is over if
     *  - longer than MAX_CYCLES
     *  - either player has zero hitpoints
     *  - one player has more than 3 times as many hitpoints as the other.
     *
     * @see orst.stratagusai.Goal
     */
    public boolean isGameOver() {
        if (game.getCycle() >= SimController.MAX_CYCLES) {
            return true;
        }
        int hp0 = getHitPoints(0);
        int hp1 = getHitPoints(1);
        if (hp0 == 0 && hp1 == 0) {
            return true;
        } else if (hp0 == 0 || hp0 * 3 < hp1) {
            return true;
        } else if (hp1 == 0 || hp1 * 3 < hp0) {
            return true;
        }
        return false;
    }

    protected int getHitPoints(int playerId) {
        // code from orst.stratagusai.Goal
        int hp = 0;
        Map<Integer, Unit> units = game.getUnits(playerId);
        for (Unit u : units.values()) {
            hp += u.getHitPoints();
        }
        return hp;
    }
}
