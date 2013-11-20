package orst.stratagusai.stratsim.model;

import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;
import orst.stratagusai.Unit;
import orst.stratagusai.UnitEvent;
import orst.stratagusai.UnitEventType;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratsim.analysis.UnitAbstractor;

/**
 * Produce units.
 *
 * @author bking
 */
public class ActionTrain extends Action {
    private static final Logger log = Logger.getLogger(ActionTrain.class);
    private static final Logger simlog = Logger.getLogger("game_sim_event");

    protected int[] targetAmounts;

    protected String[] targetTypes;

    /** amount produced so far */
    protected int[] numberProduced;

    /** the group being produced */
    protected UnitGroup group;

    /** time step when production of new unit started. */
    protected int startTime = -1;

    protected ProductionEstimation estimator = ProductionEstimation.getEstimator();

    public ActionTrain(int ownerId, UnitGroup group) {
        super(CommandType.UNIT_BUILD);
        this.group = group;
        this.ownerId = ownerId;
    }
   

    public int[] getTargetAmounts() {
        return targetAmounts;
    }

    public void setTargetAmount(int ... targetAmounts) {
        this.targetAmounts = targetAmounts;
        numberProduced = new int[targetAmounts.length];
    }

    public String[] getTargetTypes() {
        return targetTypes;
    }

    public void setTargetTypes(String...targetTypes) {
        this.targetTypes = targetTypes;
    }
    
    public ActionStatus exec(Simulator sim) {

        GameState game = sim.getGameState();
        Unit producer = game.getUnit(getUnitID());

        int i = getCheapestNeeded();
        if (i == -1) {
            // nothing needed.
            status = ActionStatus.COMPLETE;
            simlog.info(game.getCycle() + "\tend training for " + group);
            return status;
        }
        String targetType = targetTypes[i];

        if (!estimator.hasRequirements(producer, targetType)) {
            // cannot complete group because producer does not have required units.
            status = ActionStatus.CANNOT_COMPLETE;
            return status;
        }
        status = ActionStatus.ACTIVE;
        
        int cycle = game.getCycle();
        if (startTime == -1) {
            startTime = cycle;
        }        

        int time = estimator.getTime(targetType);  // time for unit when
                                                   // all prerequisites met.
        if (startTime + time < cycle) {
            startTime = cycle;
            numberProduced[i]++;
            GroupSim unit;
            if (!group.isEmpty()) {
                unit = (GroupSim) group.getRepresentative();
                UnitAbstractor.addUnit(unit, targetType);
            } else {
                simlog.info(game.getCycle() + "\tbegin training for player " + ownerId + " " + group);
                // location of producer
                Region region = game.getMap().getRegion(producer);
                int id = sim.getNextUnitId();
                unit = UnitAbstractor.createUnitGroup(id,
                                                                ownerId,
                                                                group.getType(),
                                                                1,
                                                                targetType,
                                                                region);
                group.setRepresentative(unit);
                group.addUnit(unit);
                game.addUnit(unit);
            }

            if (done()) {
                simlog.info(game.getCycle() + "\tcompleted unit " + unit.getOwnerId() + ":" + unit.getUnitId() + " " + targetType + " in group " + group.getId());
                UnitEvent evt = new UnitEvent(UnitEventType.TRAINED, getUnitID(), unit.getUnitId());
                game.addEvent(evt);
                status = ActionStatus.COMPLETE;
            }
        }
        return status;
    }

    /**
     * done if we've produced all the required units.
     * @return
     */
    private boolean done() {
        for (int i = 0; i < targetTypes.length; i++) {
            if (targetAmounts[i] > numberProduced[i]) {
                return false; // not done
            }
        }
        return true;
    }

    /**
     * get the index of the cheapest unit type that hasn't been
     * completed.
     * 
     */
    public int getCheapestNeeded() {
        boolean [] complete = new boolean[targetTypes.length];
        for (int i = 0; i < targetTypes.length; i++) {
            if (targetAmounts[i] <= numberProduced[i]) {
                complete[i] = true;
            }
        }

        int needed = -1;
        int minTime = Integer.MAX_VALUE;
        for (int i = 0; i < targetTypes.length; i++) {
            if (complete[i]) {
                continue;
            }
            int time = estimator.getTime(targetTypes[i]);
            if (time < minTime) {
                minTime = time;
                needed = i;
            }
        }
        return needed;
    }

    @Override
    public String toString() {
        StringBuffer rep = new StringBuffer();
        rep.append("[ActionTrain ownerId:");
        rep.append(ownerId);
        rep.append(" unitId:");
        rep.append(getUnitID());
        rep.append(" produces");
        if (targetAmounts != null && targetTypes != null &&
            targetAmounts.length == targetTypes.length) {
            for (int i = 0; i < targetAmounts.length; i++) {
                rep.append(" ");
                rep.append(targetAmounts[i]);
                rep.append(" ");
                rep.append(targetTypes[i]);
            }
        }
        if (group != null) {
            rep.append(" group:");
            rep.append(group.getId());
        }
        rep.append("]");
        return rep.toString();
    }
}
