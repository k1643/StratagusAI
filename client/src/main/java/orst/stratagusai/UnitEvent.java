package orst.stratagusai;

/**
 *
 * @author bking
 */
public class UnitEvent {
    protected UnitEventType type;

    /** the unit doing the action */
    protected int unitId;

    /** the unit that was acted upon. */
    protected int arg = -1;

    public UnitEvent(UnitEventType eventType, int unitId) {
        this.type = eventType;
        this.unitId = unitId;
    }

    public UnitEvent(UnitEventType eventType, int unitId, int arg) {
        this.type = eventType;
        this.unitId = unitId;
        this.arg = arg;
    }

    public UnitEventType getType() {
        return type;
    }

    public int getUnitId() {
        return unitId;
    }

    public int getArg() {
        return arg;
    }

    @Override
    public String toString() {
        return "[UnitEvent " + type + " unitId=" + unitId + " arg=" + arg + "]";
    }


}
