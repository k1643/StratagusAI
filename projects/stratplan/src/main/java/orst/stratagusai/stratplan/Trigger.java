package orst.stratagusai.stratplan;

/**
 * An edge of the strategy plan graph. Can be active or not and has a type.
 * @author Brian, Sean
 */
public class Trigger implements Cloneable {

    protected TriggerType type;
    protected boolean active = false;

    /**
     * Creates a trigger with the given type. The trigger type is an immutable
     * encoding of the directed connection relationship between ports.
     * @param type relationship between connected ports
     */
    public Trigger(TriggerType type) {
        this.type = type;
    }

    public TriggerType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive() {
        this.active = true;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    /**
     * @return the trigger id followed by abbreviation for the type.
     */
    @Override
    public String toString() {
        String myType;
        switch(type) {
            case StartStart:
                myType = "SS";
                break;
            case StartEnd:
                myType = "SE";
                break;
            case EndStart:
                myType = "ES";
                break;
            case EndEnd:
                myType = "EE";
                break;
            default:
                myType = "unknown";
        }
        return myType;
    }
}
