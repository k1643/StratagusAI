package orst.stratagusai.stratsim.model;

import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;
import orst.stratagusai.Unit;
import orst.stratagusai.UnitEvent;
import orst.stratagusai.UnitEventType;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratsim.analysis.UnitAbstractor;

/**
 * Peasants produce buildings.
 *
 * @author bking
 */
public class ActionBuild extends ActionMove {
    private static final Logger log = Logger.getLogger(ActionBuild.class);
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

    public ActionBuild(int playerId, UnitGroup group) {
        ownerId = playerId;
        this.group = group;
        setType(CommandType.UNIT_BUILD);
        log.debug("build " + group);
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
    
    @Override
    public ActionStatus exec(Simulator sim) {
        // move to build site (region).
        //
        super.exec(sim);
        if (status == ActionStatus.ACTIVE) {
            // not done with move yet.
            return status;
        }
        GameState game = sim.getGameState();
        Unit producer = game.getUnit(getUnitID());
        if (producer.isDead()) {
            status = ActionStatus.CANNOT_COMPLETE;
            return status;
        }

        int i = getCheapestNeeded();
        if (i == -1) {
            // nothing needed.
            simlog.info(game.getCycle() + "\tend build for " + group);
            status = ActionStatus.COMPLETE;
            return status;
        }
        String targetType = targetTypes[i];
        status = ActionStatus.ACTIVE;
        
        int cycle = game.getCycle();
        if (startTime == -1) {
            startTime = cycle;
        }

        GroupSim unit;
        if (!group.isEmpty()) {
            unit = (GroupSim) group.getRepresentative();
        } else {
            throw new RuntimeException("no GroupSim set in " + group);
        }

        int time = estimator.getTime(targetType);  // time for unit when
                                                   // all prerequisites met.
        if (startTime + time < cycle) {
            startTime = cycle;
            numberProduced[i]++;
            UnitAbstractor.addUnit(unit, targetType);
            if (done()) {
                simlog.info(game.getCycle() + "\tcompleted unit " + targetType + " for player " + ownerId);
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
     * get the index of the cheapest unit type that is hasn't been
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
            int time = estimator.getTime(targetTypes[i]);  // time for unit when
                                                   // all prerequisites met.
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
        rep.append("[ActionBuild ownerId:");
        rep.append(ownerId);
        rep.append(" unitId:");
        rep.append(getUnitID());
        rep.append(" regionId:");
        rep.append(getRegionId());
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
