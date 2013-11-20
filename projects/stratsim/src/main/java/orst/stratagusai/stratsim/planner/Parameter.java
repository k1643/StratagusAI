package orst.stratagusai.stratsim.planner;

/**
 *
 * @author Brian
 */
public class Parameter {
    protected String name;
    protected float min;
    protected float max;
    protected boolean isIntegral;

    public Parameter() {}

    public Parameter(String name, float min, float max, boolean isIntegral) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.isIntegral = isIntegral;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public boolean isIntegral() {
        return isIntegral;
    }

    public void setIntegral(boolean isIntegral) {
        this.isIntegral = isIntegral;
    }

    public float getMax() {
        return max;
    }

    public void setMax(float max) {
        this.max = max;
    }

    public float getMin() {
        return min;
    }

    public void setMin(float min) {
        this.min = min;
    }

    @Override
    public String toString() {
        return String.format("[Param %s %.1f %.1f %s]",
                name, min, max, isIntegral ? "integral" : "");
    }


}
