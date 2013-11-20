package orst.stratagusai.stratsim.planner;

import java.util.ArrayList;
import java.util.List;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.GameState;

/**
 *
 * @author Brian
 */
public class GameMatrix {
    protected List<StrategicPlan> s0;
    protected List<StrategicPlan> s1;
    protected GameState[][] states = new GameState[0][0];

    protected GameGoal goal = new GameGoal();

    public GameMatrix() {}

    public GameMatrix extend() {
        // for any GameState that is not terminal, create a new extended strategy
        // that attempts to finish the game.  Also extend the corresponding
        // opponent strategy.
        List<StrategicPlan> strat0 = new ArrayList<StrategicPlan>();
        List<StrategicPlan> strat1 = new ArrayList<StrategicPlan>();
        for (int i = 0; i < states.length; i++) {
            for (int j = 0; j < states.length; j++) {
                if (!goal.episodeDone(states[i][j])) {
                    
                }
            }
            // if any states were termial for the row, then add the
            // strategy.f
        }
        

        return new GameMatrix();
    }

    public boolean isTerminal() {
        for (int i = 0; i < states.length; i++) {
            for (int j = 0; j < states.length; j++) {
                if (!goal.episodeDone(states[i][j])) {
                    return false;
                }
            }
        }
        return true; // all games are done.
    }
}
