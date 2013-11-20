package orst.stratagusai;

import java.util.*;

public class Unit implements Cloneable {

    /** Id of player that owns this unit */
    private int ownerId = -1;
    /**
     * The globally unique unit identifier.*/
    private int unitId = -1;
    /**
     * Unit type
     */
    private int type = -1;
    private String typeString = "";

    private int locX = -1;  // The location of this unit on the X and Y grid
    private int locY = -1;

    /** the direction of motion. This will be set by UnitStateReader when
        the state is updated.
     */
    private Direction direction = Direction.STANDING;
    
    /** The hit points of this unit */
    private int hitPoints;

    /** ResourcesHeld */
    private long rAmount = 0;
    private long kills = 0;  // How many kills this unit has
    private ActionState actionState = new ActionState(0, -1, -1);

    protected int basicDamage;
    private int piercingDamage;
    protected int armor;
    
    // the next properties are not sent by the engine.  They
    // are properties of the unit type.

    protected int max_speed;

    protected int maxAttackRange = 1;

    private int maxHP = 60;

    private int extentX = 1;
    private int extentY = 1;

    public Unit(int unitId) {
        this.unitId = unitId;
    }


    @Override
    public Object clone() throws CloneNotSupportedException {
        // remember to change this method if member variables change.
        Unit u = (Unit) super.clone();
        u.actionState = (ActionState) u.actionState.clone();
        return u;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public int getUnitId() {
        return this.unitId;
    }

    public void setUnitType(int Type) { // FIXME: why is this duplicated?
        this.type = Type;
    }

    public void setUnitTypeString(String typeString) {
        this.typeString = typeString;
    }

    public void setLocX(int LocX) {
        this.locX = LocX;
    }

    public void setLocY(int LocY) {
        this.locY = LocY;
    }

    public void setLoc(int x, int y) {
        locX = x;
        locY = y;
    }

    public void setHitPoints(int hitPoints) {
        if (hitPoints < 0) {
            this.hitPoints = 0;
        } else {
            this.hitPoints = hitPoints;
        }
    }

    /** resource amount */
    public void setRAmount(long RAmount) {
        this.rAmount = RAmount;
    }

    public void setKills(int Kills) {
        this.kills = Kills;
    }

    public void setStatus(int Status) {
        actionState.setStatusCode(Status);
    }

    public void setStatusArgs(String StatusArgs) {
        int statusArg1 = 0;
        int statusArg2 = 0;
        StringTokenizer st = new StringTokenizer(StatusArgs, " ");
        try {
            if (st.countTokens() == 1) {
                statusArg1 = Integer.valueOf(st.nextToken(), 10).intValue();
                //this.statusArg2 = -1;
            } else if (st.countTokens() == 2) {
                statusArg1 = Integer.valueOf(st.nextToken(), 10).intValue();
                statusArg2 = Integer.valueOf(st.nextToken(), 10).intValue();
            } else if ((st.countTokens() > 2) || (st.countTokens() < 0)) {
                System.out.println("ERROR: There should be 0-2 Status Args only...");
                System.exit(1);
            }
            actionState.setArg1(statusArg1);
            actionState.setArg2(statusArg2);
        } catch (NumberFormatException e) {
            System.out.println(e.toString());
            System.exit(1);
        }
    }

    public int getCurrentTarget() {
        if (isAttacking()) {
            return actionState.getArg1();
        }
        return -1;
    }

    public int getType() {
        return this.type;
    }

    public String getUnitTypeString() {
        return this.typeString;
    }

    public void setType(int Type) { // FIXME: why is this duplicated?
        this.type = Type;
    }

    public int getLocX() {
        return this.locX;
    }

    public int getLocY() {
        return this.locY;
    }

    /**
     * Direction of motion.
     */
    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public int getHitPoints() {
        return this.hitPoints;
    }

    /**
     *
     */
    public int getMaxHP() {
        return this.maxHP;
    }

    public void setMaxHP(int hp) {
        this.maxHP = hp;
    }

    public long getRAmount() {
        return this.rAmount;
    }

    public long getKills() {
        return this.kills;
    }

    public int getBasicDamage() {
        return basicDamage;
    }

    public void setBasicDamage(int basicDamage) {
        this.basicDamage = basicDamage;
    }

    public int getPiercingDamage() {
        return piercingDamage;
    }

    public void setPiercingDamage(int pierceingDamage) {
        this.piercingDamage = pierceingDamage;
    }

    public int getMaxAttackRange() {
        return maxAttackRange;
    }

    public void setMaxAttackRange(int maxAttackRange) {
        this.maxAttackRange = maxAttackRange;
    }

    public int getArmor() {
        return armor;
    }

    public void setArmor(int armor) {
        this.armor = armor;
    }


    public void setMaxSpeed(int max_speed) {
        this.max_speed = max_speed;
    }

    public int getMaxSpeed() {
        return max_speed;
    }

    public int getExtentX() {
        return extentX;
    }

    public void setExtentX(int extentX) {
        this.extentX = extentX;
    }

    public int getExtentY() {
        return extentY;
    }

    public void setExtentY(int extentY) {
        this.extentY = extentY;
    }

    public void setExtent(int dx, int dy) {
        extentX = dx;
        extentY = dy;
    }

    public ActionState getActionState() {
        return actionState;
    }
    
    public int getStatus() {
        return this.actionState.getStatus().getCode();
    }

    public boolean isAttacking() {
        return actionState.isAttacking();
    }

    public int getAttackTarget() {
        return actionState.getArg1();
    }

    public int getStatusArg1() {
        return actionState.getArg1();
    }

    public void setStatusArg1(int arg) {
        actionState.setArg1(arg);
    }

    /** @deprecated use get/setActionStatus() */
    public int getStatusArg2() {
        return actionState.getArg2();
    }

    /** @deprecated use get/setActionStatus() */
    public void setStatusArg2(int arg) {
        actionState.setArg2(arg);
    }

    public boolean isDead() {
        return hitPoints <= 0;
    }

    public boolean isDying() {
        return actionState.isDying();
    }

    @Override
    public String toString() {
        String unitDescription = "[Id: " + unitId
                + " ownerId: " + ownerId
                + " Type: " + type
                + " Loc: (" + locX + "," + locY + ")"
                + " maxHP: " + maxHP
                + " currHP: " + hitPoints
                + " rAmount: " + rAmount
                + " kills: " + kills
                + " target " + getCurrentTarget()
                + " status: " + actionState.getStatus()
                + " statusArgs: (" + actionState.getArg1()
                + ","
                + actionState.getArg2() + ")"
                + "]";

        return unitDescription;
    }


}
