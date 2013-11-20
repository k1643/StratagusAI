package orst.stratagusai.taclp;

import java.util.List;
import orst.stratagusai.Unit;
import orst.stratagusai.util.Spatial;

/**
 *
 * @author Brian
 */
public class Features2 extends Features {

    /** enemy j can attack any ally.
     */
    protected int[] t;

    public static Features2 extract(List<Unit> allies, List<Unit> enemies) {
        Features2 f = new Features2();
        f.K = getUnitClasses(enemies);
        f.p = getProximities(allies, enemies);
        f.t = getEnemyCanAttackGroup(allies, enemies);
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
        assert params.length == 2 + nK;
        double[][] V = new double[N][M];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                V[i][j] = params[0] * p[i][j] +
                          params[1] * t[j];
                for (int k = 0; k < nK; k++) {
                    V[i][j] += params[2+k]*K[k][j];
                }
            }
        }
        return V;
    }

    protected static int[] getEnemyCanAttackGroup(List<Unit> allies, List<Unit> enemies) {
        int[] t = new int[enemies.size()];
        for (int j = 0; j < enemies.size(); j++) {
            for (int i = 0; i < allies.size(); i++) {
                Unit ally = allies.get(i);
                Unit enemy = enemies.get(j);
                if (enemy.getBasicDamage() > 0) {
                    double dist = Spatial.distance(ally.getLocX(), ally.getLocY(),
                            enemy.getLocX(), enemy.getLocY());

                    if (dist <= enemy.getMaxAttackRange()) {
                        t[j] = 1;
                        break;
                    }
                }
            }
        }
        return t;
    }

    public double getPartialDeriv(int param, double[][] x) {
        switch(param) {
            case 0:
                return sum(p,x);
            case 1:
                return sum(t,x);
            case 2:
                return sum(K[0],x);
            case 3:
                return sum(K[1],x);
            case 4:
                return sum(K[2],x);
            case 5:
                return sum(K[3],x);
            case 6:
                return sum(K[4],x);
            default:
                throw new RuntimeException("parameter index out of range:" + param);
        }
    }

    /**
     *
     * @param i feature
     * @param n ally
     * @param m enemy
     * @return
     */
    public double getValue(int i, int n, int m) {
        switch(i) {
            case 0:
                return p[n][m];
            case 1:
                return t[m];
            case 2:
                return K[0][m];
            case 3:
                return K[1][m];
            case 4:
                return K[2][m];
            case 5:
                return K[3][m];
            case 6:
                return K[4][m];
            default:
                throw new RuntimeException("feature index out of range:" + i);
        }
    }


}
