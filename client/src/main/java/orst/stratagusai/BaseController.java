package orst.stratagusai;

import orst.stratagusai.config.ControllerConfig;

/**
 * Empty implementations of Controller methods.
 *
 *
 * @author bking
 */
public class BaseController implements Controller {

    protected int playerId;
    
    /**
     * Set parameters from the configuration.
     */
    public void configure(ControllerConfig conf) {}
    
    /**
     * Set the player that the controller is acting for.
     * @param i
     */
    public void setPlayerId(int id) { playerId = id; }
    
    /**
     * Set the next actions of a Player's objects.  Called on every
     * frame update.
     */
    public void nextActions(GameProxy game) {}

    /**
     * A session consists of evaluation or training cycles.
     */
    public void beginSession(GameProxy game) {}

    /**
     * Begin training or evaluation cycle.
     */
    public void beginCycle(GameProxy game, boolean training) {}

    /**
     * Begin an episode.  An episode is one stratagus game.
     */
    public void beginEpisode(GameProxy game) {}

    /**
     * Notify controller when game is over.
     */
    public void endEpisode(GameProxy game) {}

    /**
     * Training or evaluation cycle is done.
     */
    public void endCycle(GameProxy game, boolean training) {}

    /**
     * Notify controller when all episodes are done.  Time to clean up.
     */
    public void endSession(GameProxy game) {}
}
