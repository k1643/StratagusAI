package orst.stratagusai.stratsim.model;

/**
 *
 * @author bking
 */
public enum ActionStatus {
    NOT_STARTED,  // waiting for conditions to be satisfied before starting
    ACTIVE,
    CANNOT_COMPLETE,
    COMPLETE;

    public boolean isActive() {
        return ordinal() == ACTIVE.ordinal();
    }

    public boolean isStarted() {
        return ordinal() != NOT_STARTED.ordinal();
    }
}
