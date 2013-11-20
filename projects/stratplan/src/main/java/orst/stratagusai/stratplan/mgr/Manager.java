package orst.stratagusai.stratplan.mgr;

import orst.stratagusai.stratplan.model.GameState;
import java.util.Set;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;

/**
 * Interface for simple task manager that can communicate with other managers.
 * @author Sean
 */
public interface Manager {

    /**
     * Sets the manager's parent manager for communication up the hierarchy.
     * @param parent
     */
    public void setParentManager(Manager parent);

    /**
     * the Manager must be configured with a player Id.
     */
    public void setPlayerId(int playerId);

    /**
     * Get the current strategy.
     */
    public StrategicPlan getPlan();

    /**
     * Adds task to the manager's task list.
     * @param task
     */
    public void addTask(Task task);

    /**
     * Requests termination of a given task belonging to this manager.
     * Throws TaskTerminationException.
     * @param task
     */
    public void terminateTask(Task task) throws TaskTerminationException;

    /**
     * terminate all tasks.
     */
    public void terminateTasks();

    /**
     * Gets the manager's set of tasks.
     * @return task set
     */
    public Set<Task> getTasks();

    /**
     * Notify the manager of the completion of the given task.
     * @param task
     */
    public void notifyComplete(Task task);

    /**
     * A game is beginning.
     */
    public void beginEpisode(GameState state);

    /**
     * Triggers the manager's main actions.
     * @param game game proxy providing execution of actions
     */
    public void nextActions(GameState state);

    /**
     * A game is ending.
     */
    public void endEpisode(GameState state);
}
