package orst.stratagusai.stratsim.mgrs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratsim.analysis.UnitAnalysis;
import orst.stratagusai.stratsim.model.Action;
import orst.stratagusai.stratsim.model.ActionBuild;
import orst.stratagusai.stratsim.model.ActionTrain;
import orst.stratagusai.stratsim.model.GroupSim;
import orst.stratagusai.stratsim.planner.UnitRequirement;

/**
 * A plan is a set of active and inactive Actions needed to complete a Task.
 * 
 * @author Brian
 */
public class ProductionPlan {
    private static final Logger log = Logger.getLogger(ProductionPlan.class);

    protected int ownerId = -1;

    /** the target region where the products are produced. */
    protected int regionId = -1;

    protected List<Action> waiting = new ArrayList<Action>();
    protected List<Action> active = new ArrayList<Action>();
    protected List<Action> complete = new ArrayList<Action>();

    protected UnitGroup using;

    public ProductionPlan(int playerId, int regionId,
                          UnitRequirement[] goal,
                          List<UnitRequirement> plan, 
                          UnitGroup output,
                          UnitGroup using) {
        this.ownerId = playerId;
        this.regionId = regionId;
        this.using = using;

        // get total counts.
        Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
        for (UnitRequirement req : plan) {
            String type = req.getType();
            if (counts.containsKey(type)) {
                int i = counts.get(type);
                counts.put(type, i+ req.getAmount());
            } else {
                counts.put(type, req.getAmount());
            }
        }
        // remove goal units from counts.
        for (UnitRequirement req : goal) {
            if (!counts.containsKey(req.getType())) {
                throw new RuntimeException("plan does not contain goal for " + req.getType());

            }
            int i = counts.get(req.getType()) - req.getAmount();
            if (i < 0) {
                throw new RuntimeException("plan does not contain enough units to reach goal. " + req.getType() + " actual " + counts.get(req.getType()) + " required " + req.getAmount());
            } else if (i == 0) {
                counts.remove(req.getType());
            } else {
                counts.put(req.getType(), i);
            }
        }

        // separate unit requirements into built units and trainable units.
        List<UnitRequirement> trainable = new ArrayList<UnitRequirement>();
        List<UnitRequirement> building = new ArrayList<UnitRequirement>();
        for (Map.Entry<String,Integer> req : counts.entrySet()) {
            String type = req.getKey();
            int amount = req.getValue();
            if (UnitAnalysis.isBuilding(type)) {
                building.add(new UnitRequirement(type, amount));
            } else if (UnitAnalysis.isPeasant(type) || UnitAnalysis.isCombat(type)) {
                trainable.add(new UnitRequirement(type, amount));
            }
        }
        
        makeBuildActions(playerId, regionId, using, building);
        makeTrainActions(playerId, using, trainable);

        trainable = new ArrayList<UnitRequirement>();
        building = new ArrayList<UnitRequirement>();
        for (UnitRequirement req : goal) {
            String type = req.getType();
            int amount = req.getAmount();
            if (UnitAnalysis.isBuilding(type)) {
                building.add(new UnitRequirement(type, amount));
            } else if (UnitAnalysis.isPeasant(type) || UnitAnalysis.isCombat(type)) {
                trainable.add(new UnitRequirement(type, amount));
            }
        }
        
        makeBuildActions(playerId, regionId, output, building);
        makeTrainActions(playerId, output, trainable);
    }

    private void makeBuildActions(int playerId,
                                  int regionId,
                                  UnitGroup group,
                                  List<UnitRequirement> building) {
        assert regionId != -1;
        if (building.isEmpty()) {
            return;
        }
        String[] targetTypes = new String[building.size()];
        int[] targetAmounts = new int[building.size()];

        for (int i = 0; i < targetTypes.length; i++) {
            UnitRequirement req = building.get(i);
            targetTypes[i] = req.getType();
            targetAmounts[i] = req.getAmount();
        }

        // get unit ID of unit in the "using" group.
        Unit unit = (Unit) using.getRepresentative();

        ActionBuild a = new ActionBuild(playerId, group);
        a.setTargetTypes(targetTypes);
        a.setTargetAmount(targetAmounts);
        a.setUnitID(unit.getUnitId());
        waiting.add(a);
    }

    private void makeTrainActions(int playerId,
                                  UnitGroup group,
                                  List<UnitRequirement> trainable) {
        if (trainable.isEmpty()) {
            return;
        }
        String[] targetTypes = new String[trainable.size()];
        int[] targetAmounts = new int[trainable.size()];

        for (int i = 0; i < targetTypes.length; i++) {
            UnitRequirement req = trainable.get(i);
            targetTypes[i] = req.getType();
            targetAmounts[i] = req.getAmount();
        }

        // get unit ID of unit in the "using" group.
        Unit unit = (GroupSim) using.getRepresentative();

        ActionTrain a = new ActionTrain(playerId, group);
        a.setTargetTypes(targetTypes);
        a.setTargetAmount(targetAmounts);
        a.setUnitID(unit.getUnitId());
        waiting.add(a);
    }

    public int getRegionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }


    public List<Action> getWaiting() {
        return new ArrayList<Action>(waiting);
    }

    public List<Action> getActive() {
        return new ArrayList<Action>(active);
    }

    /**
     * get all actions - waiting and active.
     * @return
     */
    public List<Action> getActions() {
        List<Action> a = new ArrayList<Action>();
        a.addAll(active);
        a.addAll(waiting);
        return a;
    }

    public void setActive(Action a) {
        if (waiting.remove(a)) {
            active.add(a);
        }
    }

    public void setComplete(Action a) {
        if (active.remove(a)) {
            complete.add(a);
        }
    }

    public boolean isComplete() {
        return waiting.isEmpty() && active.isEmpty();
    }
}
