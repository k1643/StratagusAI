package orst.stratagusai.taclp;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.GlpkException;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_prob;
import org.gnu.glpk.glp_smcp;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitType;

/**
 *
 * @author Brian
 */
public class TargetAssigner {

    private static final Logger log = Logger.getLogger(TargetAssigner.class);

    /** maximum number of attackers to assign to enemy Unit by type */
    protected static Map<String, Integer> maxAssign;

    /** by default assign 4 units to attack */
    protected static final int DEFAULT_ASSIGN = 4;

    static {
        // set the maximum number of attackers to assign to an enemy.
        // the number should be related to the target perimeter length and
        // to the target's hitpoints.
        maxAssign = new LinkedHashMap<String, Integer>();

        maxAssign.put(WargusUnitType.FOOTMAN.getName(), 3);
        maxAssign.put(WargusUnitType.PEASANT.getName(), 2);
        maxAssign.put(WargusUnitType.BALLISTA.getName(), 3);
        maxAssign.put(WargusUnitType.KNIGHT.getName(), 3);
        maxAssign.put(WargusUnitType.ARCHER.getName(), 3);
        maxAssign.put(WargusUnitType.MAGE.getName(), 3);
        maxAssign.put(WargusUnitType.PALADIN.getName(), 3);
        maxAssign.put(WargusUnitType.FARM.getName(), 3);
        maxAssign.put(WargusUnitType.HUMAN_BARRACKS.getName(), 6);
        maxAssign.put(WargusUnitType.CHURCH.getName(), 6);
        maxAssign.put(WargusUnitType.WATCH_TOWER.getName(), 6);
        maxAssign.put(WargusUnitType.STABLES.getName(), 6);
        maxAssign.put(WargusUnitType.INVENTOR.getName(), 6); // 3x3
        maxAssign.put(WargusUnitType.GRYPHON_AVIARY.getName(), 6);
        maxAssign.put(WargusUnitType.LUMBER_MILL.getName(), 6);
        maxAssign.put(WargusUnitType.FOUNDRY.getName(), 6);
        maxAssign.put(WargusUnitType.TOWN_HALL.getName(), 8);
    }

    public static Set<Assignment> assign(double[] params, Features f, List<Unit> allies, List<Unit> enemies) {
        if (allies.isEmpty() || enemies.isEmpty()) {
            return new LinkedHashSet<Assignment>();
        }
        LPSolution soln = solve(params, f, allies, enemies);
        return toAssignment(soln, allies, enemies);
    }


    public static LPSolution solve(double[] params, Features f, List<Unit> allies, List<Unit> enemies) {
        if (allies.isEmpty() || enemies.isEmpty()) {
            return null;
        }
        int[] c = getMaxAssign(enemies);  // max allies to assign to each enemy.
        double[][] V = f.getValues(params); // value of each ally-target assignment
        return solve(V, c, allies.size(), enemies.size());
    }


    private static int[] getMaxAssign(List<Unit> enemies) {
        final int M = enemies.size();
        int[] max = new int[M];
        for (int j = 0; j < M; j++) {
            String type = enemies.get(j).getUnitTypeString();
            if (!maxAssign.containsKey(type)) {
                max[j] = DEFAULT_ASSIGN;
            } else {
                max[j] = maxAssign.get(type);
            }
        }
        return max;
    }

    /**
     * solve target assignment.
     *
     * @param  V value of assignemnt i,j
     * @param  c capacity: how many allies can be assigned to targets
     * @param  N number of allies
     * @param  M number of enemies
     */
    public static LPSolution solve(double[][] V,
                                        int[] c, 
                                        final int N,
                                        final int M) {
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

        // maximize value: sum {i in ALLIES, j in TARGETS} v[j]*(1 - gamma*d[i,j]/D)*x[i,j];
        // subject to assignX {i in ALLIES}: 0 <= sum {j in TARGETS} x[i,j] <= 1;
        // subject to maxAssign {j in TARGETS}: sum{i in ALLIES} x[i,j] <= C[j];

        glp_prob lp = null;
        try {
            // Create problem
            lp = GLPK.glp_create_prob();
            //if (!log.isDebugEnabled()) {
                GLPK.glp_term_out(GLPK.GLP_OFF); // turn off terminal output.
            //}

            // Define columns (variables).  There is a attack
            // assignment for each ally enemy pair.
            final int nVars = N * M;
            GLPK.glp_add_cols(lp, nVars);
            for (int i = 1; i <= nVars; i++) {
                GLPK.glp_set_col_name(lp, i, "x" + i);
                GLPK.glp_set_col_kind(lp, i, GLPKConstants.GLP_CV);
                GLPK.glp_set_col_bnds(lp, i, GLPKConstants.GLP_LO, 0, 0);
            }


            // setup two sets of constraints.  Let the assignment probabilities be x.
            // sum_i x[i,j] <= c[j] allies assigned to enemy don't exceed limit c[j] for each enemy j
            // sum_j x[i,j] <= 1  assignements sum to 1 for each ally i
            //

            // allies assigned to enemy don't exceed limit c[j] for each enemy j
            GLPK.glp_add_rows(lp, M);
            for (int j = 0; j < M; j++) {
                GLPK.glp_set_row_name(lp, j + 1, "c" + j);
                GLPK.glp_set_row_bnds(lp, j + 1, GLPKConstants.GLP_DB, 0, c[j]);

                // TODO: do we need new arrays on each pass?
                SWIGTYPE_p_int indices = GLPK.new_intArray(N + 1);  // why the extra length?  without it, crashes when setting coefficient of last variable.
                SWIGTYPE_p_double coeffs = GLPK.new_doubleArray(N + 1);

                for (int i = 0; i < N; i++) {
                    int k = i * M + j + 1;
                    GLPK.intArray_setitem(indices, i + 1, k);   // set x index.
                    GLPK.doubleArray_setitem(coeffs, i + 1, 1); // coefficient value is 1.
                }
                GLPK.glp_set_mat_row(lp, j + 1, N, indices, coeffs);  // set a row of coefficent values in the constraint matrix (A)
            }

            // probabilities sum to 1.
            int row = M + 1;
            GLPK.glp_add_rows(lp, N);
            for (int i = 0; i < N; i++) {
                GLPK.glp_set_row_name(lp, row, "probabilities" + i);
                GLPK.glp_set_row_bnds(lp, row, GLPKConstants.GLP_DB, 0, 1);
                // include all x variables in probability constraint
                SWIGTYPE_p_int indices = GLPK.new_intArray(M + 1);
                SWIGTYPE_p_double coeffs = GLPK.new_doubleArray(nVars + 1);
                for (int j = 0; j < M; j++) {
                    int k = i * M + j + 1;
                    GLPK.intArray_setitem(indices, j + 1, k);
                    GLPK.doubleArray_setitem(coeffs, j + 1, 1);
                }
                GLPK.glp_set_mat_row(lp, row++, M, indices, coeffs);
            }

            // Define objective
            GLPK.glp_set_obj_name(lp, "z");
            GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MAX);
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M; j++) {
                    int k = i * M + j + 1;
                    GLPK.glp_set_obj_coef(lp, k, V[i][j]);
                }
            }

            // Solve model
            glp_smcp parm = new glp_smcp(); // algorithm control parameters.
            parm.setMsg_lev(GLPK.GLP_MSG_ERR); // error message only
            GLPK.glp_init_smcp(parm);
            int ret = GLPK.glp_simplex(lp, parm);

            // Retrieve solution
            if (ret == 0) {
                // for debugging: write solution
                // GLPK.glp_write_lp(lp, null, "results/linear_prog_" + System.currentTimeMillis() + ".txt");
                double val = GLPK.glp_get_obj_val(lp);
                // System.out.println("objective value " + val);

                // get solution for row player.
                double[] x = new double[nVars];
                for (int k = 0; k < nVars; k++) {
                    // retrieve column primal value
                    x[k] = GLPK.glp_get_col_prim(lp, k+1);
                }
                return new LPSolution(val, x);

            } else {
                throw new RuntimeException("LP solver fails with return code " + ret);
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

    public static Set<Assignment> toAssignment(LPSolution soln, List<Unit> allies, List<Unit> enemies) {
        final int N = allies.size();
        final int M = enemies.size();
        double[] result = soln.x;
        Set<Assignment> assign = new LinkedHashSet<Assignment>();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                int k = i * M + j;
                if (result[k] > .99) { // allow for error.
                    assign.add(new Assignment(allies.get(i),
                            enemies.get(j)));
                }
            }
        }
        return assign;
    }
}
