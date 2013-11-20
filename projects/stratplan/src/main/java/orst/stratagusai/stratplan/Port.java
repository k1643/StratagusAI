package orst.stratagusai.stratplan;

import edu.uci.ics.jung.graph.DirectedGraph;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Node of the strategy plan graph. Triggers act as edges between these nodes.
 * Port pairs are bridged by tasks, as a start and end ports for that task.
 * @author Brian, Sean
 */
public class Port {

    protected DirectedGraph graph;
    protected Task task;

    /**
     * Creates a port belonging to a graph and a task.
     * @param graph the graph the port is part of
     * @param task the task the port belongs to
     */
    public Port(DirectedGraph graph, Task task) {
        assert graph != null && task != null;
        this.graph = graph;
        this.task = task;
    }

    /**
     * Gets outgoing Triggers with this port as the source.
     * @return set of outgoing triggers
     */
    public Set<Trigger> getOutgoingTriggers() {
        return new LinkedHashSet<Trigger>(graph.getOutEdges(this));
    }

    /**
     * Gets incoming Triggers with this port as the destination.
     * @return set of incoming triggers
     */
    public Set<Trigger> getIncomingTriggers() {
        return new LinkedHashSet<Trigger>(graph.getInEdges(this));
    }

    /**
     * Gets the predecessor ports from the graph.
     * @return set of predecessor ports
     */
    public Set<Port> getPredecessors() {
        return new LinkedHashSet<Port>(graph.getPredecessors(this));
    }

    /**
     * Gets the successor ports from the graph.
     * @return set of successor ports
     */
    public Set<Port> getSuccessors() {
        return new LinkedHashSet<Port>(graph.getSuccessors(this));
    }

    /**
     * Gets the task whose pair of ports this port belongs to.
     * @return task this port belongs to
     */
    public Task getTask() {
        return task;
    }

    public void activateTriggers() {
        for (Trigger trigger : getOutgoingTriggers()) {
            trigger.setActive();
        }
    }

    /**
     * @return string with an id followed by the name of the task the port
     * belongs t
     */
    @Override
    public String toString() {
        String whichPort = "E";
        if (this == task.getStartPort()) {
            whichPort = "S";
        }
        return task.getName()+":"+whichPort;
    }
}
