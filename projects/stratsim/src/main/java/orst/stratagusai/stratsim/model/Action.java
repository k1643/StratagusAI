package orst.stratagusai.stratsim.model;

import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.command.CommandType;

/**
 * Extension of UnitCommand for use in Simulator.
 * 
 * @author bking
 */
public abstract class Action extends UnitCommand {

    protected ActionStatus status = ActionStatus.NOT_STARTED;

    /** player ID for logging. */
    protected int ownerId = -1;

    protected boolean terminated;

    public Action(CommandType type) {
        super(type);
    }

    public Action(CommandType type, int unitId) {
        super(type);
        setUnitID(unitId);
    }
    
    public ActionStatus getStatus() {
        return status;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }
    
    public abstract ActionStatus exec(Simulator sim);
}
