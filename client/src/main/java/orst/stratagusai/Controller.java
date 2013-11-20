package orst.stratagusai;

import orst.stratagusai.config.ControllerConfig;

/**
 * A controller acts on behalf of a player.  The GameRunner calls
 * nextActions() from its game event loop.
 *
 *
 * @author bking
 */
public interface Controller {

    /**
     * Set parameters from the configuration.
     */
    public void configure(ControllerConfig conf);
    
    /**
     * Set the player that the controller is acting for.
     * @param i
     */
    public void setPlayerId(int id);
    
    /**
     * Set the next actions of a Player's objects.  Called on every
     * frame update.
     */
    void nextActions(GameProxy game);

    /**
     * A session consists of evaluation or training cycles.
     */
    void beginSession(GameProxy game);

    /**
     * Begin training or evaluation cycle.
     */
    void beginCycle(GameProxy game, boolean training);

    /**
     * Begin an episode.  An episode is one stratagus game.
     */
    void beginEpisode(GameProxy game);

    /**
     * Notify controller when game is over.
     */
    void endEpisode(GameProxy game);

    /**
     * Training or evaluation cycle is done.
     */
    void endCycle(GameProxy game, boolean training);

    /**
     * Notify controller when all episodes are done.  Time to clean up.
     */
    void endSession(GameProxy game);
}
