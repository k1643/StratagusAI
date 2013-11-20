package orst.stratagusai;

/**
 *
 * @author bking
 */
public class ActionState implements Cloneable {

    protected UnitStatus status;
    protected int arg1;
    protected int arg2;

    public ActionState() {}

    public ActionState(UnitStatus status) {
        this.status = status;
    }

    public ActionState(int code) {
        this.status = UnitStatus.valueOf(code);
    }

    public ActionState(int code, int arg1, int arg2) {
        this.status = UnitStatus.valueOf(code);
        this.arg1 = arg1;
        this.arg2= arg2;
    }

    public int getArg1() {
        return arg1;
    }

    public void setArg1(int arg1) {
        this.arg1 = arg1;
    }

    public int getArg2() {
        return arg2;
    }

    public void setArg2(int arg2) {
        this.arg2 = arg2;
    }

    public UnitStatus getStatus() {
        return status;
    }

    public void setStatusCode(int code) {
        this.status = UnitStatus.valueOf(code);
    }

    public boolean isDefault() {
        return status == UnitStatus.DEFAULT;
    }

    public boolean isMoving() {
        // if MOVING then x=arg1 and y=arg2.
        return status == UnitStatus.MOVING;
    }

    public boolean isAttacking() {
        return status == UnitStatus.ATTACKING;
    }

    public boolean isBuildingOrTraining() {
        return status == UnitStatus.BUILDING_OR_TRAINING;
    }

    public boolean isRepairing() {
        return status == UnitStatus.REPAIRING;
    }

    public boolean isHarvesting() {
        return status == UnitStatus.HARVESTING;
    }

    public boolean isReturningResource() {
        return status == UnitStatus.RETURNING_RESOURCE;
    }

    public boolean isDying() {
        return status == UnitStatus.DIE;
    }

    public int getAttackTarget() {
        return getArg1();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        // remember to change this method if member variables change.
        return super.clone();
    }
}
