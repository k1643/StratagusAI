package orst.stratagusai.stratsim.model;

import orst.stratagusai.stratplan.model.GameState;

/**
 * Evaluator decides the status of the game.  For instance, if the objective
 * is to kill all of the opponents, then the GameEvaluator determines
 * that the game is over when one Player loses all their units.
 *
 * @author bking
 */
public interface GameEvaluator {

    void setGame(GameState game);

    /** get scores for players */
    int[] getScores();

    boolean isGameOver();
}
