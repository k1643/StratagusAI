package orst.stratagusai.stratplan;

import edu.uci.ics.jung.graph.DirectedGraph;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * Represents a unit in the strategy plan. Composed of a status state and two
 * ports that belong to the strategy plan graph.
 * Used by the planner to create a task network.
 * @author Brian, Sean
 */
public class Task {
    private static Logger log = Logger.getLogger(Task.class);
    protected String name;
    protected String type = StrategicPlan.NOOP_TYPE;
    
    /** task arguments */
    protected int targetGroupId = -1;

    /** task arguments */
    protected int targetRegionId = -1;

    /** unit group to be used to accomplish the task */
    protected int usingGroupId = -1;

    protected Port start;
    protected Port end;

    /** the strategy that this Task is part of */
    protected StrategicPlan plan;

    protected boolean active = false;
    protected boolean complete = false;

    protected String comment;

    /** estimated duration. -1 means indefinite. */
    protected int duration = -1;

    /** estimated completion time. -1 means indefinite. */
    protected int estimatedCompletionTime = -1;

    public Task() {}

    /**
     * Initializes task with given type and name.
     * @param name identifies the task
     * @param type matches definition and manager that may handle the task
     */
    public Task(String name, String type) {
        this.name = name;
        this.type = type;
    }

    /**
     * initialize Task state.
     */
    public void initialize() {
        active = false;
        complete = false;
    }

    public StrategicPlan getPlan() {
        return plan;
    }

    public void setPlan(StrategicPlan strategy) {
        this.plan = strategy;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }

    public void setTargetGroup(UnitGroup group) {
        targetGroupId = group.getId();
    }

    public void setTargets(UnitGroup group, int regionId) {
        targetGroupId = group.getId();
        targetRegionId = regionId;
    }

    public void setTargetRegionId(int regionId) {
        targetRegionId = regionId;
    }

    public UnitGroup getTargetGroup() {
        if (targetGroupId == -1) {
            return null;
        }
        UnitGroup g = plan.getGroup(targetGroupId);
        assert g != null : "group " + targetGroupId + " not found in plan.";
        return g;
    }

    public int getTargetRegionId() {
        return targetRegionId;
    }

    public UnitGroup getUsingGroup() {
        UnitGroup g = plan.getGroup(usingGroupId);
        return g;
    }

    public void setUsing(int groupId) {
        this.usingGroupId = groupId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getEstimatedCompletionTime() {
        return estimatedCompletionTime;
    }

    public void setEstimatedCompletionTime(int estimatedCompletionTime) {
        this.estimatedCompletionTime = estimatedCompletionTime;
    }
    
    /**
     * Adds two ports (start and end) to the graph and registers them with
     * this task.
     * @param graph in which the ports will exist
     */
    public void setGraph(DirectedGraph graph) {
        start = new Port(graph, this);
        end = new Port(graph, this);
    }

    public Port getStartPort() {
        return start;
    }

    public Port getEndPort() {
        return end;
    }

    public void setActive() {
        active = true;
    }

    public boolean isActive() {
        if (complete) return false;
        if (active) return true;
        // conjunctive
        for (Trigger trigger : start.getIncomingTriggers()) {
            if (!trigger.isActive()) {
                return false;
            }
        }
        active = true;
        return true;
    }

    /**
     * Sets the task complete. Activates outgoing end port triggers.
     */
    public void setComplete() {
        complete = true;
        end.activateTriggers();
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isLeaf() {
        assert start != null : "task has not been added to plan.";
        return end.getSuccessors().isEmpty() && start.getSuccessors().isEmpty();
    }

    /**
     * Returns set of predecessor tasks connected via triggers.
     * @return predecessor tasks
     */
    public Set<Task> getPredecessors() {
        assert start != null : "task has not been added to plan.";
        Set<Task> predecessors = new LinkedHashSet<Task>();
        for (Port port : start.getPredecessors()) {
            predecessors.add(port.getTask());
        }
        for (Port port : end.getPredecessors()) {
            predecessors.add(port.getTask());
        }
        return predecessors;
    }

    /**
     * Returns set of successor tasks connected via triggers.
     * @return successor tasks set
     */
    public Set<Task> getSuccessors() {
        assert end != null : "task has not been added to plan.";
        Set<Task> successors = new LinkedHashSet<Task>();
        for (Port port : start.getSuccessors()) {
            successors.add(port.getTask());
        }
        for (Port port : end.getSuccessors()) {
            successors.add(port.getTask());
        }
        return successors;
    }

    @Override
    public String toString() {
        String rep = "[Task " + name + " " + type + " (";
        if (targetGroupId != -1) {
            rep += ":group " + targetGroupId;
        }
        if (targetRegionId != -1) {
            rep += ":region " + targetRegionId;
        }
        rep += ")";
        if (usingGroupId != -1) {
            rep += " :using " + usingGroupId;
        }
        return rep + "]";
    }
}
