package orst.stratagusai.stratsim.analysis;

import java.text.DecimalFormat;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.log4j.Logger;

/**
 * 
 */
public class GameMatrixSolverTest
    extends TestCase
{
    private static final Logger log =
            Logger.getLogger(GameMatrixSolverTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GameMatrixSolverTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GameMatrixSolverTest.class );
    }

    /**
     * 
     */
    public void testRPS() throws Exception {
        // rock,paper,scissors matrix.
        //
        // A = [ 0 -1 1;
        //       1 0 -1;
        //      -1 1 0];
        //
        RealMatrix A = new Array2DRowRealMatrix(
                new double[][] {
                    { 0, -1,  1},
                    { 1,  0, -1},
                    {-1,  1,  0}
        });
        GameSolution soln = GameMatrixSolver.solveGame(A);   // maximize   
        RealPointValuePair rowSoln = soln.getRowSolution();

        double [] x = rowSoln.getPoint();
       /* DecimalFormat formatter = new DecimalFormat("###,###.###");
        System.out.println("game value is " + formatter.format(solution.getValue()));
        System.out.print("action probabilities are:");
        for (int i = 0; i < x.length; i++) {
            System.out.print(" ");
            System.out.print(formatter.format(x[i]));
        } */

        assertEquals(0, rowSoln.getValue(), Double.MIN_VALUE);
        assertEquals(.3333, x[0], .00007);
        assertEquals(.3333, x[1], .00007);
        assertEquals(.3333, x[2], .00007);

        RealPointValuePair colSoln = soln.getColSolution();
        double [] y = colSoln.getPoint();

        assertEquals(0, colSoln.getValue(), Double.MIN_VALUE);
        assertEquals(.3333, y[0], .00007);
        assertEquals(.3333, y[1], .00007);
        assertEquals(.3333, y[2], .00007);
    }

    public void testRPSNegative() throws Exception {
        // rock,paper,scissors matrix.
        // move all values -1 to test maximin negative value
        //
        RealMatrix A = new Array2DRowRealMatrix(
                new double[][] {
                    { -1, -2,  0},
                    {  0, -1, -2},
                    { -2,  0, -1}
        });
        GameSolution soln = GameMatrixSolver.solveGame(A);   // maximize
        RealPointValuePair rowSoln = soln.getRowSolution();

        double [] x = rowSoln.getPoint();
       /* DecimalFormat formatter = new DecimalFormat("###,###.###");
        System.out.println("game value is " + formatter.format(solution.getValue()));
        System.out.print("action probabilities are:");
        for (int i = 0; i < x.length; i++) {
            System.out.print(" ");
            System.out.print(formatter.format(x[i]));
        } */

        assertEquals(-1, rowSoln.getValue(), Double.MIN_VALUE);
        assertEquals(.3333, x[0], .00007);
        assertEquals(.3333, x[1], .00007);
        assertEquals(.3333, x[2], .00007);

        RealPointValuePair colSoln = soln.getColSolution();
        double [] y = colSoln.getPoint();

        assertEquals(-1, colSoln.getValue(), Double.MIN_VALUE);
        assertEquals(.3333, y[0], .00007);
        assertEquals(.3333, y[1], .00007);
        assertEquals(.3333, y[2], .00007);
    }

    public void test21() throws Exception {
        // test Dynamic Noncooperative Game Theory example 21
        //
        RealMatrix A = new Array2DRowRealMatrix(
                new double[][] {
                    { 1, 3,  0},
                    { 6, 2,  7}
        });

        GameSolution soln = GameMatrixSolver.solveGame(A, true);   // minimizer
        RealPointValuePair rowSoln = soln.getRowSolution();
        double [] x = rowSoln.getPoint();
        assertEquals(8/3.0, rowSoln.getValue(), Double.MIN_VALUE);
        assertEquals(.6666, x[0], .00007);
        assertEquals(.3333, x[1], .00007);

        RealPointValuePair colSoln = soln.getColSolution();
        double [] y = colSoln.getPoint();
        assertEquals(3, y.length);
        assertEquals(.1666,      y[0], .0005);
        assertEquals(.8333, y[1], .0005);
        assertEquals(0, y[2], .0005);
    }

    public void testControlMatrix() throws Exception {
        // test example of values of generated strategies.
        RealMatrix A = new Array2DRowRealMatrix(
                new double[][] {
                    {0,         0,      -360,         0,      -360,      -360,      -360},
                    {0,         0,      -360,         0,      -360,      -360,      -360},
                    {360,       360,         0,       360,         0,         0,         0},
                    {0,         0,      -360,         0,      -360,      -360,     -360},
                    {360,       360,         0,       360,         0,         0,         0},
                    {360,       360,         0,       360,         0,         0,         0},
                    {360,       360,         0,       360,         0,         0,         0}
        });
        GameSolution soln = GameMatrixSolver.solveGame(A);   // maximize
        RealPointValuePair solution = soln.getRowSolution();
    }
}
