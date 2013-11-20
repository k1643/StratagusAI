package orst.stratagusai.stratsim.analysis;

import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.log4j.Logger;
import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.GlpkException;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_prob;
import org.gnu.glpk.glp_smcp;

/**
 * LP game matrix solver used by SwitchingPlanner.
 * 
 * @author Brian
 */
public class GameMatrixSolver {
    private static final Logger log = Logger.getLogger(GameMatrixSolver.class);
    
    /**
     * solve for row player as maximizer.
     */
    public static GameSolution solveGame(RealMatrix A) {
        return solveGame(A, false); // maximize
    }

    /**
     * solve for row player.
     */
    public static GameSolution solveGame(RealMatrix A, boolean minimize) {
        // Type     Bounds              Comment
        // -------------------------------------------------------
        // GLP_FR   -Inf < x < +Inf     Free (unbounded) variable
        // GLP_LO   lb  <= x < +Inf     Variable with lower bound
        // GLP_UP   -Inf <= x <= ub     Variable with upper bound
        // GLP_DB   lb <= x <= ub       Double-bounded variable
        // GLP_FX   lb =  x  = bu       Fixed
        //
        // GLP_CV continuous variable;
        // GLP_IV integer variable;
        // GLP_BV binary variable.

        glp_prob lp = null;
        try {
            // Create problem
            lp = GLPK.glp_create_prob();

            // Define columns (variables).  There is a probability
            // for each row of A, and a variable for the game value.
            final int nVars = A.getRowDimension()+1;
            GLPK.glp_add_cols(lp, nVars);
            for (int i = 1; i <= A.getRowDimension(); i++) {
                GLPK.glp_set_col_name(lp, i, "x"+i);
                GLPK.glp_set_col_kind(lp, i, GLPKConstants.GLP_CV);
                GLPK.glp_set_col_bnds(lp, i, GLPKConstants.GLP_LO, 0, 0);
            }

            GLPK.glp_set_col_name(lp, nVars, "v");
            GLPK.glp_set_col_kind(lp, nVars, GLPKConstants.GLP_CV);
            GLPK.glp_set_col_bnds(lp, nVars, GLPKConstants.GLP_FR, 0, 0);

            // setup two sets of constraints.  Let the row probabilities be x.
            // [-A' 1] <= 0      difference beween game value and expected value of each row is 0.
            // sum x = 1         probabilities sum to 1.
            //

            // difference beween game value and expected value of each row is 0.
            GLPK.glp_add_rows(lp, A.getColumnDimension());
            for (int j = 1; j <= A.getColumnDimension(); j++) {
                SWIGTYPE_p_int indices = GLPK.new_intArray(nVars+1);  // why the extra length?  without it, crashes when setting coefficient of last variable.
                SWIGTYPE_p_double values = GLPK.new_doubleArray(nVars+1);
                GLPK.glp_set_row_name(lp, j, "c"+j);
                int rel = minimize ? GLPKConstants.GLP_LO : GLPKConstants.GLP_UP;
                GLPK.glp_set_row_bnds(lp, j, rel, 0, 0);
                // x variables
                for (int i = 1; i <= nVars-1; i++) {
                    GLPK.intArray_setitem(indices, i, i);
                    GLPK.doubleArray_setitem(values, i, -A.getEntry(i-1, j-1));
                }
                // include game value v.
                GLPK.intArray_setitem(indices, nVars, nVars);
                // the next line causes a crash
                GLPK.doubleArray_setitem(values, nVars, 1.0D);
                GLPK.glp_set_mat_row(lp, j, nVars, indices, values);
            }

            // probabilities sum to 1.
            int row = A.getColumnDimension()+1;
            GLPK.glp_add_rows(lp, 1);
            GLPK.glp_set_row_name(lp, row, "probabilities");
            GLPK.glp_set_row_bnds(lp, row, GLPKConstants.GLP_FX, 1, 1);
            // include all x variables in probability constraint
            SWIGTYPE_p_int indices = GLPK.new_intArray(nVars+1);
            for (int i = 1; i <= nVars; i++) {
                GLPK.intArray_setitem(indices, i, i);
            }
            SWIGTYPE_p_double values = GLPK.new_doubleArray(nVars+1);
            for (int i = 1; i <= nVars-1; i++) {
                GLPK.doubleArray_setitem(values, i, 1);
            }
            GLPK.glp_set_mat_row(lp, row, nVars, indices, values);

            // Define objective
            GLPK.glp_set_obj_name(lp, "z");
            GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MIN);
            GLPK.glp_set_obj_coef(lp, nVars, minimize ? 1 : -1);

            // Solve model
            glp_smcp parm = new glp_smcp(); // algorithm control parameters.
            GLPK.glp_init_smcp(parm);
            int ret = GLPK.glp_simplex(lp, parm);

            // Retrieve solution
            if (ret == 0) {
                // get solution for row player.
                double[] resultArray = new double[nVars];
                for (int i = 1; i <= nVars; i++) {
                                       // retrieve column primal value
                                       // (solved matrix transpose)
                    resultArray[i-1] = GLPK.glp_get_col_prim(lp, i);
                }
                RealPointValuePair rowSolution = new RealPointValuePair(resultArray, resultArray[nVars-1]);

                // get solution for column player.
                final int nRows = A.getColumnDimension(); // solve in transpose
                log.debug("nRows=" + nRows);
                resultArray = new double[nRows];
                for (int j = 1; j <= nRows; j++) {
                                       // retrieve row value
                    if (minimize) {
                        resultArray[j-1] = GLPK.glp_get_row_dual(lp, j);
                    } else {
                        resultArray[j-1] = -GLPK.glp_get_row_dual(lp, j);
                    }
                }
                RealPointValuePair colSolution = new RealPointValuePair(resultArray,
                                                                        rowSolution.getValue());

                return new GameSolution(rowSolution, colSolution);
            } else {
                throw new RuntimeException("LP solver fails with return code "+ret);
            }
        } catch (GlpkException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (lp != null) {
                // Free memory
                GLPK.glp_delete_prob(lp);                
            }
        }
    }

}
