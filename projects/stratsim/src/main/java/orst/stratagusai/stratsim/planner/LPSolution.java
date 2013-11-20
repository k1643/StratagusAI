package orst.stratagusai.stratsim.planner;

/**
 *
 * @author Brian
 */
public class LPSolution {
    /** LP objective function value */
    private double value;

    /** solution vector */
    double[] x;

    public LPSolution() {}

    public LPSolution(double value, double[] x) {
        this.value = value;
        this.x = x;
    }

    public double getValue() {
        return value;
    }

    public double[] getVector() {
        return x;
    }
}
