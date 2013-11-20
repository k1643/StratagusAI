package orst.stratagusai.stratsim.analysis;

import orst.stratagusai.Controller;
import orst.stratagusai.GameProxy;
import orst.stratagusai.Unit;
import orst.stratagusai.config.ControllerConfig;

/**
 * Controller used to monitor game state.  Calculates win rate over a
 * session.
 *
 * @author Brian
 */
public class GameListener implements Controller {
    protected static final int N_PLAYERS = 2;
    protected int[] wins;
    protected int episodes;

    /** win rate for player 1. */
    public float getWinRate(int playerId) {
        return wins[playerId]/(float)episodes;
    }

    public void configure(ControllerConfig conf) {}

    public void setPlayerId(int id) {}

    public void nextActions(GameProxy game) {}

    public void beginSession(GameProxy game) {
        wins = new int[N_PLAYERS];
        episodes = 0;
    }

    public void beginCycle(GameProxy game, boolean training) {}

    public void beginEpisode(GameProxy game) {}

    public void endEpisode(GameProxy game) {
        int[] scores = {
            getHitPoints(0, game),
            getHitPoints(1, game)
        };
        if (scores[0] > scores[1]) {
            wins[0]++;
        } else if (scores[0] < scores[1]) {
            wins[1]++;
        }
        // else tie.
        episodes++;
    }

    public void endCycle(GameProxy game, boolean training) {}

    public void endSession(GameProxy game) {}

    protected int getHitPoints(int playerId, GameProxy game) {
        int total = 0;
        for (Unit u : game.getUnits(playerId).values()) {
            total += u.getHitPoints();
        }
        return total;
    }
}
