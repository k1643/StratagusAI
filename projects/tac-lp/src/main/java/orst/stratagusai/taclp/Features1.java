package orst.stratagusai.taclp;

import java.util.List;
import orst.stratagusai.Unit;

/**
 *
 * @author Brian
 */
public class Features1 extends Features {

    /** is ally i attacking enemy j? */
    protected int[][] a;

    /** is enemy j moving away from ally i? */
    protected int[][] m;

    /** enemy j can attack ally i.
     *  TODO: change to enemy can attack group
     *  int[] t;
     */
    int[][] t;

    public static Features1 extract(List<Unit> allies, List<Unit> enemies) {
        Features1 f = new Features1();
        f.K = getUnitClasses(enemies);
        f.p = getProximities(allies, enemies);
        f.a = getAttacking(allies, enemies);
        f.m = getMotion(allies, enemies);
        f.t = getEnemyCanAttack(allies, enemies);
        f.nAllies = allies.size();
        f.nEnemies = enemies.size();
        return f;
    }

    /**
     * get value of each ally-target assignment.  The LP objective function
     * is Vx.
     *
     * @param allies
     * @param enemies
     * @return
     */
    public double[][] getValues(double[] params) {
        final int N = getNumAllies();
        final int M = getNumEnemies();
        final int nK = getNumUnitClasses();
        double[][] V = new double[N][M];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                V[i][j] = params[0] * p[i][j] +
                          params[1] * a[i][j] +
                          params[2] * m[i][j] +
                          params[3] * t[i][j];
                for (int k = 0; k < nK; k++) {
                    V[i][j] += params[4+k]*K[k][j];
                }
            }
        }
        return V;
    }

    public double getPartialDeriv(int param, double[][] x) {
        switch(param) {
            case 0:
                return sum(p,x);
            case 1:
                return sum(a,x);
            case 2:
                return sum(m,x);
            case 3:
                return sum(t,x);
            case 4:
                return sum(K[0],x);
            case 5:
                return sum(K[1],x);
            case 6:
                return sum(K[2],x);
            case 7:
                return sum(K[3],x);
            case 8:
                return sum(K[4],x);
            default:
                throw new RuntimeException("parameter index out of range:" + param);
        }
    }
}
