package orst.stratagusai.stratsim.mgrs;


import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.mgr.Manager;
import orst.stratagusai.stratplan.mgr.TaskTerminationException;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratsim.model.Action;
import orst.stratagusai.stratsim.model.ActionAttack;
import orst.stratagusai.stratsim.model.ActionSecure;
import orst.stratagusai.stratsim.model.ActionStatus;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 *
 */
public class TacticalManager implements Manager {
    private static final Logger log = Logger.getLogger(TacticalManager.class);

    private int playerId = -1;

    private Manager parent;

    protected Queue<Task> nextTasks = new LinkedList<Task>();

    protected Set<Task> activeTask = new LinkedHashSet<Task>();

    protected Map<Task,Set<Action>> activeActions = new LinkedHashMap<Task,Set<Action>>();

    public TacticalManager() {}

    public TacticalManager(int playerId) {this.playerId = playerId;}

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    
    public void setParentManager(Manager parent) {
        this.parent = parent;
    }

    public StrategicPlan getPlan() {
        return parent.getPlan();
    }

    public void addTask(Task task) {
        nextTasks.add(task);
        log.debug("new Task " + task);
    }

    public void terminateTask(Task task) throws TaskTerminationException {
        log.debug("terminate " + task + " for player " + playerId);
        nextTasks.remove(task);
        activeTask.remove(task);
        for (Action a : activeActions.get(task)) {
            a.setTerminated(true);
        }
        activeActions.remove(task);
    }

    public void terminateTasks() {
        nextTasks.clear();
        activeTask.clear();
        activeActions.clear();
    }

    public Set<Task> getTasks() {
        return new LinkedHashSet<Task>(nextTasks);
    }

    public void notifyComplete(Task task) {}

    public void nextActions(GameState state) {
        Task task;
        while ((task = nextTasks.poll()) != null) {
            activeTask.add(task);
        }

        for (Task t : activeTask) {
            if (!activeActions.containsKey(t)) {
                assignCommands(state, t);
            }
        }

        // notify parent of task completion.
        Iterator<Task> activeItr = activeActions.keySet().iterator();
        while(activeItr.hasNext()) {
            task = activeItr.next();
            Set<Action> actions = activeActions.get(task);
            Iterator<Action> itr = actions.iterator();
            while (itr.hasNext()) {
                Action a = itr.next();
                if (a.getStatus() == ActionStatus.COMPLETE) {
                    itr.remove();
                }
            }
            if (actions.isEmpty()) {
                activeItr.remove();
                activeTask.remove(task);
                parent.notifyComplete(task);
            }
        }
    }

    public void assignCommands(GameState state, Task activeTask) {
  
        // find group to accomplish task.
        UnitGroup using = activeTask.getUsingGroup();
        if (using == null) {
            throw new RuntimeException("No group provided to do combat task " + activeTask +
                     " for player " + playerId);
        }
        if (using.isEmpty()) {
            // units for group may not have been produced yet, so nothing to do.
            return;
        }

        int regionId = activeTask.getTargetRegionId();
        log.debug("group " + using + " for player " + playerId +
                  " will " + activeTask.getType() + " region " + regionId + ".");

        // get unit ID of unit in the "using" group.
        Unit unit = (GroupSim) using.getRepresentative();
        assert unit.getOwnerId() == playerId: "unit owned by player " + unit.getOwnerId() + " controlled by player " + playerId;

        // convert to region.
        Region r = state.getMap().getRegion(regionId);
        Action cmd = new ActionAttack(unit, r);
        if ("secure".equals(activeTask.getType())) {
            cmd = new ActionSecure(unit, r);
        } else if ("attack".equals(activeTask.getType())) {
            cmd = new ActionAttack(unit, r);
        } else {
            throw new RuntimeException("unknown task type '" + activeTask.getType() + "'.");
        }
        state.addCommand(cmd);
        Set<Action> actions = activeActions.get(activeTask);
        if (actions == null) {
            actions = new LinkedHashSet<Action>();
            activeActions.put(activeTask, actions);
        }
        actions.add(cmd);
    }


    public void beginEpisode(GameState state) {}

    public void endEpisode(GameState state) {}
}
