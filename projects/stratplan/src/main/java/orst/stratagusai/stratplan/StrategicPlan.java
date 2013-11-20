package orst.stratagusai.stratplan;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedOrderedSparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * Strategy graph plan consisting of ports, triggers, and tasks. Ports are the
 * nodes and triggers are the edges of the graph implementation. Tasks, which
 * bridge pairs or ports, are the conceptual nodes of the plan graph and
 * represent a unit in the plan. Tasks and triggers may be added to the graph
 * but not removed.
 * 
 * @author Brian, Sean
 */
public class StrategicPlan {
    private static final Logger log = Logger.getLogger(StrategicPlan.class);
    private String name;
    private int playerId = -1;
    private DirectedGraph<Port, Trigger> graph;
    private Map<String,Task> tasks;

    /** the start task */
    private Task start;

    // built in task types.
    public static final String NOOP_TYPE = "noop";
    public static final String START_NAME = "START";
    public static final String INIT_GROUP = "init-group";

    /** unit group specifications */
    PlayerGroups groups = new PlayerGroups();

    protected int nextGroupId;
    
    /**
     * Initializes a DirectedSparseGraph for implementation of the plan graph
     * and set containing all the tasks in the graph.
     */
    public StrategicPlan() {
        // use an ordered graph so that we get a repeatable iteration
        // order on collections produced by the graph.
        graph = new DirectedOrderedSparseMultigraph<Port, Trigger>();
        // and LinkedHashMap iterators have a repeatable order.
        tasks = new LinkedHashMap<String,Task>();
    }

    /**
     * Same as default constructor but also initializes the strategy's name.
     * @param name identifies the strategy
     */
    public StrategicPlan(int playerId, String name) {
        this();
        this.playerId = playerId;
        this.name = name;
    }

    /**
     * Reset task and trigger states.
     */
    public void intialize() {
        for (Task t : tasks.values()) {
            t.initialize();
        }
        for (Trigger trg : graph.getEdges()) {
            trg.setActive(false);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public int getNextGroupId() {
        nextGroupId = Math.max(nextGroupId, groups.getMaxGroupId());
        return ++nextGroupId;
    }

    /** */
    public void setGroups(Map<Integer,UnitGroup> specs) {
        // setGroups() called by PlanEval
        assert playerId != -1;
        this.groups.clear();
        for (UnitGroup g : specs.values()) {
            assert g.getOwnerId() == playerId;
            this.groups.addGroup(g);
        }
    }

    public void addGroup(UnitGroup g) {
        assert !groups.containsGroup(g.getId()) : "cannot add UnitGroup with existing ID.";
        assert g.getOwnerId() == playerId : "plan player and group owner must be the same. Plan player " + playerId + ", group owner " + g.getOwnerId();
        groups.addGroup(g);
        nextGroupId = Math.max(nextGroupId, g.getId());
    }

    /**
     * Get the groups defined for this strategy.
     * @return
     */
    public PlayerGroups getPlayerGroups() {
        return groups;
    }
    
    public Set<UnitGroup> getGroups() {
        return groups.getGroups();
    }

    public UnitGroup getGroup(int id) {
        return groups.getGroup(id);
    }

    public Set<Task> getTasks() {
        return new LinkedHashSet<Task>(tasks.values());
    }

    /**
     * Adds the given task to this strategy plan and to the task set.
     * The start and end ports of the task are generated and added to the
     * implementation graph.
     * @param task
     */
    public void addTask(Task task) {
        assert task.getName() != null;
        assert !tasks.containsKey(task.getName());
        tasks.put(task.getName(), task);
        task.setPlan(this);
        task.setGraph(graph);
        graph.addVertex(task.getStartPort());
        graph.addVertex(task.getEndPort());
    }

    /**
     * Adds a trigger connecting the given tasks.
     * The trigger type is assumed to be the default EndStart type, meaning
     * that task t2 sees the trigger activation when task t1 completes.
     * @param t1 task with connected end port
     * @param t2 task with connected start port
     */
    public void addTrigger(Task t1, Task t2) {
        addTrigger(t1, t2, TriggerType.EndStart);
    }

    /**
     * Adds a trigger with the given type connecting the given tasks.
     * The trigger is connected to the appropriate underlying ports of the
     * tasks based on the trigger type.
     * @param t1 source task
     * @param t2 destination task
     * @param triggerType enumerated trigger type
     */
    public void addTrigger(Task t1, Task t2, TriggerType triggerType) {      
        if (!tasks.containsKey(t1.getName())) {
            addTask(t1);
        }
        if (!tasks.containsKey(t2.getName())) {
            addTask(t2);
        }

        checkCycle(t1, t2); // check if this trigger would produce a cycle
        switch (triggerType) {
            case StartEnd:
                graph.addEdge(new Trigger(triggerType),
                        t1.getStartPort(),
                        t2.getEndPort(),
                        EdgeType.DIRECTED);
                break;
            case StartStart:
                graph.addEdge(new Trigger(triggerType),
                        t1.getStartPort(),
                        t2.getStartPort(),
                        EdgeType.DIRECTED);
                break;
            case EndEnd:
                graph.addEdge(new Trigger(triggerType),
                        t1.getEndPort(),
                        t2.getEndPort(),
                        EdgeType.DIRECTED);
                break;
            case EndStart:
                graph.addEdge(new Trigger(triggerType),
                        t1.getEndPort(),
                        t2.getStartPort(),
                        EdgeType.DIRECTED);
                break;
        }
    }

    /**
     * Gets next tasks in the task network.
     * If no other tasks can be reached, returns empty set.
     * @return set of tasks
     */
    public Set<Task> getNext(Task task) {
        Set<Task> taskSet = new LinkedHashSet<Task>();
        for (Task nextTask : task.getSuccessors()) {
            if (nextTask.isActive()) {
                taskSet.add(nextTask);
            }
        }
        return taskSet;
    }

    /**
     * Get start task.
     */
    public Task getStart() {
        if (start == null) {
            start = tasks.get(StrategicPlan.START_NAME);
            if (start == null) {
                // get tasks that have no predecessors, and add START task
                // before them.
                Set<Task> taskSet = new LinkedHashSet<Task>();
                for (Task task : tasks.values()) {
                    if (task.getPredecessors().isEmpty()) {
                        taskSet.add(task);
                    }
                }
                start = new Task(StrategicPlan.START_NAME, StrategicPlan.NOOP_TYPE);
                addTask(start);

                for (Task t : taskSet) {
                    addTrigger(start, t);
                }
            }
        }
        return start;
    }

    /**
     *
     */
    public Set<Task> getLeafTasks() {
        Set<Task> leaves = new LinkedHashSet<Task>();
        for (Task task : tasks.values()) {
            if (task.isLeaf()) {
                leaves.add(task);
            }
        }
        return leaves;
    }

    /**
     * Gets active tasks. Active tasks are those whose incoming start
     * triggers are active.
     * @return set of tasks
     */
    public Set<Task> getActiveTasks() {
        Set<Task> taskSet = new LinkedHashSet<Task>();
        for (Task task : tasks.values()) {
            if (task.isActive()) {
                taskSet.add(task);
            }
        }
        return taskSet;
    }

    /**
     * get tasks to be executed by the given UnitGroup.
     * @param group
     * @return
     */
    public Set<Task> getTasks(UnitGroup group) {
        Set<Task> taskSet = new LinkedHashSet<Task>();
        for (Task task : tasks.values()) {
            if (task.getUsingGroup() == group) {
                taskSet.add(task);
            }
        }
        return taskSet;
    }

    /**
     * Gets the destination port of a trigger.
     * @param tr the trigger to consider
     * @return destination port trigger is connected to
     */
    public Port getDestination(Trigger tr) {
        return graph.getDest(tr);
    }
    
    /**
     * 
     */
    @Override
    public String toString() {
        return "[StrategicPlan " + name + " :player " + playerId + "]";
    }

    /**
     * if t1 is a successor of t2, then adding trigger t1 -> t2 would
     * produce a cycle.
     *
     * @param t1
     * @param t2
     */
    private void checkCycle(Task t1, Task t2) {
        for (Task t : t2.getSuccessors()) {
            if (t == t1) {
                throw new RuntimeException("Trigger " + t1 + " -> " + t2 + " would produce a cycle.");
            }
            checkCycle(t1, t);
        }
    }
}
