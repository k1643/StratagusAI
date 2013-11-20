package orst.stratagusai.stratplan.mgr;

import orst.stratagusai.stratplan.model.GameState;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.Port;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.Trigger;
import orst.stratagusai.stratplan.TriggerType;

/**
 * Responsible for managing tasks and goals while traversing the strategy plan
 * graph.
 * Other managers can be mapped to a specific task type for communication with
 * this strategy manager.
 * @author Sean
 */
public class StrategyManager implements Manager {

    private static final Logger log = Logger.getLogger(StrategyManager.class);
    /** the strategy to execute */
    protected StrategicPlan plan;
    /** map of task names to tasks */
    protected Map<String, Task> taskMap;
    /** map of task types to task managers */
    protected Map<String, Manager> managerMap;
    protected Set<Manager> managers = new LinkedHashSet<Manager>();
    /** task activity propagation queue */
    protected Queue<Trigger> propQueue;
    /** tasks that just started or completed and need their status
    propagated through triggers                                 */
    protected Queue<Task> messageTaskQueue;
    /** tasks that are ready for execution */
    protected Queue<Task> newTasks;
    protected Queue<Task> interruptedTasks;
    //protected UnitPool pool;
    protected int playerId = -1;

    /**
     * Initializes internal collections. setStrategy must be called before
     * this manager is functional.
     */
    public StrategyManager() {
        messageTaskQueue = new LinkedList<Task>();
        propQueue = new LinkedList<Trigger>();
        taskMap = new LinkedHashMap<String, Task>();
        managerMap = new LinkedHashMap<String, Manager>();

        newTasks = new LinkedList<Task>();
        interruptedTasks = new LinkedList<Task>();
    }

    /**
     * Initializes this strategy manager to handle a given strategy.
     * @param strategy
     */
    public StrategyManager(StrategicPlan strategy) {
        this();
        setPlan(strategy);
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    /**
     * Sets the plan to traverse with the postcondition that plan task
     * traversal is ready.
     * @param strategy
     */
    public void setPlan(StrategicPlan plan) {
        cleanUp();
        terminateTasks();
        this.plan = plan;

        // initialize task map
        taskMap.clear();
        for (Task task : plan.getTasks()) {
            taskMap.put(task.getName(), task);
        }

        Task root = plan.getStart();
        messageTaskQueue.add(root);
    }

    /**
     * Gets this manager's strategy. The strategy may be manipulated since
     * task traversal is dynamic, but modifications will only be considered if
     * they affect tasks not yet traversed.
     * @return the current strategy
     */
    public StrategicPlan getPlan() {
        return plan;
    }

    /**
     *
     */
    public UnitGroup getGroup(int id) {
        return plan.getGroup(id);
    }

    /**
     * Clears out internal collections.
     */
    protected void cleanUp() {
        messageTaskQueue.clear();
        propQueue.clear();
        taskMap.clear();
        // managerMap.clear();

        newTasks.clear();
        interruptedTasks.clear();
    }

    /**
     * Completes a given task and propagates trigger activation out from the
     * task end port, activating or interrupting tasks as appropriate.
     * Uses a depth-first traversal.
     * @param task
     */
    protected void propagate(Task task) {
        task.setComplete();
        propQueue.addAll(task.getEndPort().getOutgoingTriggers());

        while (!propQueue.isEmpty()) {
            Trigger trigger = propQueue.remove();
            TriggerType type = trigger.getType();
            Port nextPort = plan.getDestination(trigger);
            Task nextTask = nextPort.getTask();

            if (type == TriggerType.StartEnd || type == TriggerType.EndEnd) {
                if (nextTask.isActive()) {
                    interruptedTasks.add(nextTask);
                }
                nextTask.setComplete();
                propQueue.addAll(nextPort.getOutgoingTriggers());
            }

            if (type == TriggerType.StartStart || type == TriggerType.EndStart) {
                if (nextTask.isActive()) {
                    newTasks.add(nextTask);
                    nextPort.activateTriggers();
                    propQueue.addAll(nextPort.getOutgoingTriggers());
                }
            }
        }
    }

    public void setParentManager(Manager parent) {
        throw new UnsupportedOperationException(
                "StrategyManager does not accept a parent manager");
    }

    public void addTask(Task task) {
        throw new UnsupportedOperationException(
                "StrategyManager does not accept tasks.");
    }

    public void terminateTask(Task task) throws TaskTerminationException {
        throw new TaskTerminationException(
                "StrategyManager does not execute tasks.");
    }

    public void terminateTasks() {
        for (Manager mgr : managers) {
            mgr.terminateTasks();
        }
    }

    /**
     * Returns tasks from the internal task map. The task set does not
     * necessarily match the set of tasks in the strategy due to internal
     * modifications and added tasks.
     * @return set of tasks
     */
    public Set<Task> getTasks() {
        return new LinkedHashSet(taskMap.values());
    }

    /**
     * Notifies completion of the given task. This may trigger propagation
     * and new task handling.
     * @param task the task that was completed
     * @return false if task is not known by this manager
     */
    public void notifyComplete(Task task) {
        if (taskMap.containsValue(task)) {
            log.debug("task complete: " + task + " for player " + playerId);
            messageTaskQueue.add(task);
        } else {
            String msg = "Could not match task in task manager: playerId=" + playerId + " " + task;
            log.warn(msg);
            throw new RuntimeException(msg);
        }
    }

    public void beginEpisode(GameState state) {
        plan.intialize();
        for (Manager mgr : managers) {
            mgr.beginEpisode(state);
        }
    }

    /**
     * Runs update loop for task management.
     */
    public void nextActions(GameState state) {

        // remove re-assigned units from groups.  After a unit dies, the
        // engine may reuse a unit ID for a new unit of another player.
        // TODO: is this needed? these should have been removed when they died.
        for (UnitGroup g : plan.getPlayerGroups().getGroups()) {
            for (Unit u : g.getUnits()) {
                if (u.getOwnerId() != playerId) {
                    g.removeUnit(u);
                }
            }
        }
        while (!messageTaskQueue.isEmpty()) {
            propagate(messageTaskQueue.remove());
        }

        // pass new tasks to the appropriate Manager
        Iterator<Task> itr = newTasks.iterator();
        while (itr.hasNext()) {
            Task task = itr.next();
            String type = task.getType();
            if (type.equals(StrategicPlan.NOOP_TYPE)) {
                continue;
            }
            if (type.equals(StrategicPlan.INIT_GROUP)) {
                initGroup(state, task);
            } else {
                Manager man = managerMap.get(type);
                if (man == null) {
                    throw new RuntimeException("no manager matches task type '" + type + "'");
                }
                man.addTask(task);
            }
            itr.remove();
        }

        while (!interruptedTasks.isEmpty()) {
            try {
                Task task = interruptedTasks.remove();
                Manager man = managerMap.get(task.getType());
                if (man == null) {
                    throw new RuntimeException("no manager matches task type");
                }
                man.terminateTask(task);
            } catch (TaskTerminationException tte) {
                log.error("manager could not terminate task");
            }
        }

        // pass nextActions thread to managers.
        for (Manager mgr : managers) {
            mgr.nextActions(state);
        }

        // remove dead units from groups.  After a unit dies, the
        // engine may reuse a unit ID for a new unit.  We remove units
        // after mgr.nextActions() so the sub-managers have a chance to
        // react to unit death.
        for (UnitGroup g : plan.getPlayerGroups().getGroups()) {
            for (Unit u : g.getUnits()) {
                if (u.isDead()) {
                    g.removeUnit(u);
                }
            }
        }
    }

    public void endEpisode(GameState state) {
        for (Manager mgr : managers) {
            mgr.endEpisode(state);
        }
    }

    /**
     * Used for debugging.
     * @param man the external manager to add to this manager
     * @param taskType to associate tasks with the added manager
     */
    public void addManager(Manager man, String taskType) {
        assert playerId != -1 : "player id not set";
        man.setParentManager(this);
        man.setPlayerId(playerId);
        managerMap.put(taskType, man);
        managers.add(man);
    }

    private boolean meetsRequirements(UnitGroup group, List<Argument> reqs) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Unit u : group.getUnits()) {
            String type = u.getUnitTypeString();
            if (counts.containsKey(type)) {
                int n = counts.get(type);
                counts.put(type, n + 1);
            } else {
                counts.put(type, 1);
            }
        }
        for (Argument arg : reqs) {
            String type = arg.getName();
            int number = arg.getValue();
            if (!counts.containsKey(type)) {
                return false;
            }
            int n = counts.get(type);
            if (n < number) {
                return false;
            }
        }
        return true;
    }

    /**
     * Implement "init-group" task.
     *
     */
    public void initGroup(GameState state, Task task) {
        UnitGroup group = task.getTargetGroup();
        List<Argument> reqs = group.getUnitTypeReqs();
        if (meetsRequirements(group, reqs)) {
            messageTaskQueue.add(task);  // task complete
        } else {
            log.warn("initial group does not meet reqs.: " + group);
        }
    }
}
