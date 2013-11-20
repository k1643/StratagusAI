package orst.stratagusai.stratsim.analysis;

import orst.stratagusai.stratsim.planner.UnsatisfiableGoalException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratsim.io.ProductionEstimationReader;
import orst.stratagusai.stratsim.model.BuildRequirements;
import orst.stratagusai.stratsim.model.GroupSim;
import orst.stratagusai.stratsim.planner.UnitRequirement;


/**
 *
 * @author Brian
 */
public class ProductionEstimation {
    private static final Logger log = Logger.getLogger(ProductionEstimation.class);
    /** map unit type to build requirements */
    protected Map<String, BuildRequirements> reqs =
            new LinkedHashMap<String, BuildRequirements>();

    public static void main(String[] args) {
        ProductionEstimation est = getEstimator();

        String [] types = {
            "unit-town-hall",
            "unit-footman",
            "unit-archer",
            "unit-knight"
        };
        for (String type : types) {
            BuildState s = new BuildState();
            s.increment("unit-peasant");
            int time = est.getTimeEstimate(type, s);
            System.out.println("time for " + type + " is " + time);

            s = new BuildState();
            s.increment("unit-peasant");
            for (String req : est.getAllRequiredUnits(type, s)) {
                System.out.println("  " + req);
            }
        }
    }

    public Map<String, BuildRequirements> getRequirements() {
        return reqs;
    }

    public static ProductionEstimation getEstimator() {
        ProductionEstimation.class.getClassLoader();
        InputStream is =
            ClassLoader.getSystemResourceAsStream("build_requirements.yaml");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        ProductionEstimation est = ProductionEstimationReader.load(br);
        return est;
    }

    public void setRequirements(Map<String, BuildRequirements> reqs) {
        this.reqs = reqs;
    }

    public void addRequirements(String type, BuildRequirements req) {
        reqs.put(type, req);
    }

    /**
     * get the immediate prerequisites of the given type.
     */
    public BuildRequirements getRequirements(String type) {
        return reqs.get(type);
    }

    public List<UnitRequirement> getPlan(BuildState state, BuildState goal) throws UnsatisfiableGoalException {
        List<UnitRequirement> plan = new ArrayList<UnitRequirement>();
        BuildState s = (BuildState) state.clone();
        String unmet;
        while ((unmet = s.unmet(goal)) != null) {
            int amount = goal.getCount(unmet) - state.getCount(unmet);
            getPlan(0, unmet, amount, s, plan);
        }
        return plan;
    }



    /**
     * Add required amount of required type.
     */
    public void getPlan(int depth, String type, int amount, BuildState projectedState, List<UnitRequirement> plan) throws UnsatisfiableGoalException {
        // prepare for recursive call.  If a plan requires a type that
        // was previously unmet, then the goal can not be satisfied.
        if (depth > 20) {
            throw new RuntimeException("ProductionEstimation.getPlan() exceeds recursion limit.");
        }
        Set<String> unmet = new LinkedHashSet<String>();
        UnitRequirement req = getPlan(++depth,type, amount, projectedState, plan, unmet);
        projectedState.increment(type, req.getAmount());

    }

    /**
     * Add one unit of required type.
     */
    public UnitRequirement getPlan(int depth, String type, int amount, BuildState projectedState, List<UnitRequirement> plan, Set<String> unmet) throws UnsatisfiableGoalException {
        if (depth > 20) {
            throw new RuntimeException("ProductionEstimation.getPlan() exceeds recursion limit.");
        }
        if (plan.size() > 100) {
            for (UnitRequirement req : plan) {
                log.debug(req);
            }
            throw new RuntimeException("plan length exceeds limit.");
        }
        unmet.add(type);
        BuildRequirements req = reqs.get(type);
        if (req == null) {
            throw new RuntimeException("no requirements specified for " + type);
        }
        for (BuildRequirements prereq : req.getPreconditions()) {
            String r = prereq.getType();
            if (unmet.contains(r)) {
                // cannot satisfy previously unmet requirement.
                throw new UnsatisfiableGoalException("cannot create plan for " + r);
            }
            if (!projectedState.exists(r)) {
                getPlan(++depth, r, 1, projectedState, plan, unmet);
            }
        }
        // all prereqs satisfied, so add the required type and amount to projectedState.
        UnitRequirement action = new UnitRequirement(type);  // add one
        action.setAmount(amount);
        plan.add(action);
        projectedState.increment(type, amount);
        return action;
    }

    /**
     * get total time needed to produce a type including time for all
     * requisite units.
     *
     */
    public int getTimeEstimate(String type, BuildState s) {
        BuildRequirements req = reqs.get(type);
        if (req == null) {
            throw new RuntimeException("no requirements specified for " + type);
        }
        int time = req.getTime();
        for (BuildRequirements prereq : req.getPreconditions()) {
            if (!s.exists(prereq.getType())) {
                time += getTimeEstimate(prereq.getType(), s);
            }
        }
        s.increment(type);  // add another unit in the ProductionState.
        return time;
    }

    public boolean hasRequirements(Unit u, String[] types) {
        for (String type : types) {
            if (!hasRequirements(u, type)) {
                return false;
            }
        }
        return true;
    }

    /**
     * return true if the given unit group can produce the given type.
     * Unit may be a UnitGroup, and so may contain other units.
     */
    public boolean hasRequirements(Unit u, String type) {
        assert type != null;
        assert u != null;
        assert u.getUnitTypeString() != null;
        if (u instanceof GroupSim) {
            return hasRequirements((GroupSim) u, type);
        }

        // is u.getUnitTypeString() among the preconditions?
        Set<BuildRequirements> pre = getRequirements(type).getPreconditions();
        if (pre.size() > 1) {
            return false;
        }
        BuildRequirements r = pre.iterator().next();
        return type.equals(r.getType());
    }

    /**
     * return true if the given unit group can produce the given type.
     * Unit may be a UnitGroup, and so may contain other units.
     */
    public boolean hasRequirements(GroupSim u, String type) {
        Set<BuildRequirements> pre = getRequirements(type).getPreconditions();
        for (BuildRequirements r : pre) {
            if (!u.contains(r.getType(), 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * get build time of type when prerequisites are satisfied.
     */
    public int getTime(String type) {
        BuildRequirements req = reqs.get(type);
        if (req == null) {
            throw new RuntimeException("unknown prerequisites for type '" + type + "'.");
        }
        return req.getTime();
    }

    /**
     * list all the units required to build the given unit.
     * @param type
     * @return
     */
    public Collection<String> getAllRequiredUnits(String type, BuildState s) {
        BuildRequirements req = reqs.get(type);
        if (req == null) {
            throw new RuntimeException("no requirements specified for " + type);
        }
        List<String> types = new ArrayList<String>();
        for (BuildRequirements prereq : req.getPreconditions()) {
            if (!s.exists(prereq.getType())) {
                types.add(prereq.getType());
                types.addAll(getAllRequiredUnits(prereq.getType(), s));
            }
        }
        s.increment(type);  // add another unit in the ProductionState.
        return types;
    }
}
