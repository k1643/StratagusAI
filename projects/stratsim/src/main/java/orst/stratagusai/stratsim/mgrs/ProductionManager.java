package orst.stratagusai.stratsim.mgrs;

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
import orst.stratagusai.UnitEvent;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.mgr.Manager;
import orst.stratagusai.stratplan.mgr.TaskTerminationException;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.mgr.StrategyManager;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratsim.analysis.BuildState;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;
import orst.stratagusai.stratsim.analysis.UnitAnalysis;
import orst.stratagusai.stratsim.model.Action;
import orst.stratagusai.stratsim.model.ActionBuild;
import orst.stratagusai.stratsim.model.GroupSim;
import orst.stratagusai.stratsim.planner.UnitRequirement;
import orst.stratagusai.stratsim.planner.UnsatisfiableGoalException;
import orst.stratagusai.util.Spatial;

/**
 * Produce units
 */
public class ProductionManager implements Manager {
    private static final Logger log = Logger.getLogger(ProductionManager.class);

    protected int playerId = -1;

    protected StrategyManager parent;

    protected Queue<Task> nextTasks = new LinkedList<Task>();

    /** each active Task has a ProductionPlan */
    protected Map<Task,ProductionPlan> plans = new LinkedHashMap<Task,ProductionPlan>();

    protected ProductionEstimation estimator = ProductionEstimation.getEstimator();

    public ProductionManager() {}

    public ProductionManager(int playerId) {this.playerId = playerId;}

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public void setParentManager(Manager parent) {
        // assume the parent is a StrategyManager so we can get to the
        // GroupSpecs
        this.parent = (StrategyManager) parent;
    }

    public StrategicPlan getPlan() {
        return parent.getPlan();
    }

    public void addTask(Task task) {
        nextTasks.add(task);
        log.debug("new Task " + task + " ownerId=" + playerId);
    }

    public void terminateTask(Task task) throws TaskTerminationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void terminateTasks() {
        nextTasks.clear();
        plans.clear();
    }

    public Set<Task> getTasks() {
        return new LinkedHashSet<Task>(nextTasks);
    }

    public void notifyComplete(Task task) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    public void nextActions(GameState state) {

        // check for completed tasks.
        Iterator<Task> planItr = plans.keySet().iterator();
        while(planItr.hasNext()) {
            Task t = planItr.next();
            if (isTaskDone(t, state)) {
                parent.notifyComplete(t);
                planItr.remove();
            }
        }
        // make plans for new tasks
        /*
        Iterator<Task> taskIter = nextTasks.iterator();
        while (taskIter.hasNext()) {
            Task task = taskIter.next();
            if (makePlan(state, task)) {
                taskIter.remove();
                log.debug("made plan for " + task);
            } else {
                // log.debug("unable to make plan for " + task);
            }
        } */
        while (!nextTasks.isEmpty()) {
            Task task = nextTasks.remove();
            makePlan(state, task);
        }

        // start waiting actions that have been assigned producers
        for (Map.Entry<Task,ProductionPlan> entry : plans.entrySet()) {
            ProductionPlan plan = entry.getValue();
            Task task = entry.getKey();
            for (Action a : plan.getWaiting()) {
                assignRegion(state, task, a);
                log.debug("start action " + a);
                plan.setActive(a);
                state.addCommand(a);
            }
        }
    }

    public boolean makePlan(GameState state, Task task) {
        UnitGroup producers = task.getUsingGroup();
        assert producers != null : "no production group provided for " + task;
        if (producers.isEmpty()) {
            // "no units in production group " + producers + " for player " + playerId);
            return false;
        }
        GroupSim sim = (GroupSim) producers.getRepresentative();
        assert sim.getOwnerId() == playerId: "unit owned by player " + sim.getOwnerId() + " controlled by player " + playerId;

        int regionId = task.getTargetRegionId();
        Region region;
        if (regionId == -1) {
            // what region are the producers in?
            region = state.getMap().getRegion(sim);
        } else {
            // get specified region
            region = state.getMap().getRegion(regionId);
        }

        // get output group specifications.
        UnitGroup output = task.getTargetGroup();
        UnitRequirement[] unitReqs = getUnitRequirements(output);

        // create a plan for creating the targets
        List<UnitRequirement> plan;
        try {
            BuildState buildState = getBuildState(state, region);
            BuildState goal = getGoal(state, region, unitReqs);
            plan = estimator.getPlan(buildState, goal);

        } catch (UnsatisfiableGoalException e) {
            // couldn't make a plan from current state.
            return false;
        }

        ProductionPlan prodPlan = new ProductionPlan(playerId, 
                                                     region.getId(),
                                                     unitReqs,
                                                     plan,
                                                     output,
                                                     task.getUsingGroup());
        plans.put(task, prodPlan);

        return true;
    }

    private void assignRegion(GameState state, Task task, Action a) {
        // get producing region.
        UnitGroup g = task.getUsingGroup();
        Unit u = (GroupSim) g.getRepresentative();
        // get region where to produce
        if (a instanceof ActionBuild) {
            ActionBuild build = (ActionBuild) a;
            int regionId = task.getTargetRegionId();
            if (regionId != -1) {
                build.setTargetID(regionId);
            } else {
                Region r = state.getMap().getRegion(u);
                build.setTargetID(r.getId());
            }
        }
    }
    
    public void beginEpisode(GameState state) {}

    public void endEpisode(GameState state) {}

    private boolean isTaskDone(Task t, GameState state) {
        // match completion event to action (command).
        Set<UnitEvent> events = state.getEvents();
        ProductionPlan plan = plans.get(t);

        // make map of active actions
        Map<Integer,Action> active = new LinkedHashMap<Integer,Action>();
        for (Action a : plan.getActive()) {
            active.put(a.getUnitID(), a);
        }
        
        for (UnitEvent event : events) {
            // assumes unit only assigned one action.
            Action a = active.get(event.getUnitId());
            if (a != null) {
                plan.setComplete(a);
            }
        }
        return plan.isComplete();
    }

    public Set<Task> getActiveTasks() {
        return plans.keySet();
    }

    private Unit closestPeasants(GameState state, Region r) {
        assert r != null;
        int x = r.getX();
        int y = r.getY();
        return closestPeasants(state, x, y);
    }

    private Unit closestPeasants(GameState state, int x, int y) {
        double min = Double.POSITIVE_INFINITY;
        Unit closest = null;
        for (Unit u : state.getUnits()) {
            if (!UnitAnalysis.isPeasant(u)) {
                continue;
            }
            double d = Spatial.distance(u.getLocX(), u.getLocY(), x, y);
            if (d < min) {
                min = d;
                closest = u;
            }
        }
        return closest;
    }

    /** 
     * Get the player's build state in the given region.
     */
    private BuildState getBuildState(GameState game, Region r) {
        BuildState state = new BuildState();

        Set<Unit> units = game.getUnits(r);
        for (Unit u : units) {
            if (u.getOwnerId() != playerId) {
                continue;
            }
            if (u instanceof GroupSim) {
                GroupSim g = (GroupSim) u;
                for (Map.Entry<String,Integer> entry : g.getUnitTypes().entrySet()) {
                    state.increment(entry.getKey(), entry.getValue());
                }                
            } else {
                state.increment(u.getUnitTypeString());
            }
        }
        return state;
    }

    private BuildState getGoal(GameState state, Region r, UnitRequirement [] reqs) {
        BuildState goal = getBuildState(state, r);
        for (int i = 0; i < reqs.length; i++) {
            assert reqs[i].getAmount() > 0 : "0 amount requirement for " + reqs[i].getType();
            goal.increment(reqs[i].getType(), reqs[i].getAmount());
        }
        return goal;
    }

    private UnitRequirement[] getUnitRequirements(UnitGroup group) {
        List<Argument> unitArgs = group.getUnitTypeReqs();
        UnitRequirement [] reqs = new UnitRequirement[unitArgs.size()];
        for (int i = 0; i < unitArgs.size(); i++) {
            Argument arg = unitArgs.get(i);
            assert arg.getValue() > 0 : "0 amount requirement for " + arg.getName() + " for " + group;
            reqs[i] = new UnitRequirement(arg.getName(),
                                          arg.getValue());
        }
        return reqs;
    }
}
