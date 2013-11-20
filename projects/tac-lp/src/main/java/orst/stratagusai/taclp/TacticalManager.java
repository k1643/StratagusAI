package orst.stratagusai.taclp;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.mgr.AbstractManager;
import orst.stratagusai.stratplan.mgr.TaskTerminationException;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.UnitGroup;

/**
 *
 */
public class TacticalManager extends AbstractManager {
    private static final Logger log = Logger.getLogger(TacticalManager.class);

    protected List<Task> nextTasks = new LinkedList<Task>();

    protected Map<Task,CombatGroupManager> activeTasks = new LinkedHashMap<Task,CombatGroupManager>();

    protected Queue<Task> completeTasks = new LinkedList<Task>();

    public TacticalManager() {}

    public TacticalManager(int playerId) {this.playerId = playerId;}

    public int getPlayerId() {
        return playerId;
    }

    public void addTask(Task task) {
        nextTasks.add(task);
    }

    public void terminateTask(Task task) throws TaskTerminationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void terminateTasks() {
        nextTasks.clear();
        activeTasks.clear();
    }

    public Set<Task> getTasks() {
        return new LinkedHashSet<Task>(nextTasks);
    }

    @Override
    public void notifyComplete(Task task) {
        completeTasks.add(task);
        parent.notifyComplete(task);
    }

    @Override
    public void beginEpisode(GameState state) {
        nextTasks.clear();
        activeTasks.clear();
        completeTasks.clear();
    }

    public void nextActions(GameState state) {
        Iterator<Task> itr = nextTasks.iterator();
        while (itr.hasNext()) {
            Task task = itr.next();
            if (nextTask(state, task)) {
                itr.remove();
            }
        }

        for (CombatGroupManager mgr : activeTasks.values()) {
            mgr.nextActions(state);
        }

        // cleanup
        Task t;
        while ((t=completeTasks.poll()) != null) {
            activeTasks.remove(t);
        }
    }

    public boolean nextTask(GameState state, Task task) {
        UnitGroup group = task.getUsingGroup();
        int regionId = task.getTargetRegionId();
        CombatGroupManager mgr = new CombatGroupManager(this, playerId, regionId, task, group);
        activeTasks.put(task, mgr);
        return true;
    }

}
