package orst.stratagusai.stratplan.mgr;

import java.util.Set;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.model.GameState;

/**
 * Partial implementation of Manager.
 * @author Brian
 */
public abstract class AbstractManager implements Manager {

    protected Manager parent;

    protected int playerId = -1;

    public void setParentManager(Manager parent) {
        this.parent = parent;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public StrategicPlan getPlan() {
        return parent.getPlan();
    }

    public abstract void addTask(Task task);

    public abstract void terminateTask(Task task) throws TaskTerminationException;

    public abstract Set<Task> getTasks();

    public void notifyComplete(Task task) {}

    public void beginEpisode(GameState state) {}

    public abstract void nextActions(GameState state);

    public void endEpisode(GameState state) {}
}
