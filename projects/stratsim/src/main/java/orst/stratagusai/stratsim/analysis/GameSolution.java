package orst.stratagusai.stratsim.analysis;

import org.apache.commons.math.optimization.RealPointValuePair;

/**
 *
 * @author Brian
 */
public class GameSolution {
    protected RealPointValuePair rowSolution;
    protected RealPointValuePair colSolution;

    public GameSolution() {}

    public GameSolution(RealPointValuePair rowSolution,
                        RealPointValuePair colSolution) {
        this.rowSolution = rowSolution;
        this.colSolution = colSolution;
    }

    public RealPointValuePair getColSolution() {
        return colSolution;
    }

    public void setColSolution(RealPointValuePair colSolution) {
        this.colSolution = colSolution;
    }

    public RealPointValuePair getRowSolution() {
        return rowSolution;
    }

    public void setRowSolution(RealPointValuePair rowSolution) {
        this.rowSolution = rowSolution;
    }
}
