package orst.stratagusai.taclp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.Direction;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.util.Spatial;

/**
 *
 * @author Brian
 */
public abstract class Features {
    private static final Logger log = Logger.getLogger(Features.class);
    
    protected static Map<String, UnitClass> unitClasses;
    
    /** distance cutoff */
    protected final static float MAX_DIST = 21F;

    static {
        // taxonomy of unit types.
        unitClasses = new LinkedHashMap<String, UnitClass>();
        unitClasses.put(WargusUnitType.FOOTMAN.getName(),  UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.PEASANT.getName(),  UnitClass.PEASANT);
        unitClasses.put(WargusUnitType.BALLISTA.getName(), UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.KNIGHT.getName(),   UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.ARCHER.getName(),   UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.MAGE.getName(),     UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.PALADIN.getName(),  UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.GRYPHON_RIDER.getName(),  UnitClass.COMBAT);
        unitClasses.put(WargusUnitType.FARM.getName(),     UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.HUMAN_BARRACKS.getName(), UnitClass.PRODUCTION_BLDG);
        unitClasses.put(WargusUnitType.CHURCH.getName(),   UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.WATCH_TOWER.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.STABLES.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.INVENTOR.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.GRYPHON_AVIARY.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.LUMBER_MILL.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.FOUNDRY.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.TOWN_HALL.getName(), UnitClass.PRODUCTION_BLDG);
        unitClasses.put(WargusUnitType.MAGE_TOWER.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.BLACKSMITH.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.KEEP.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.CASTLE.getName(), UnitClass.SUPPORT_BLDG);
        unitClasses.put(WargusUnitType.GUARD_TOWER.getName(), UnitClass.COMBAT_BLDG);
        unitClasses.put(WargusUnitType.CANNON_TOWER.getName(), UnitClass.COMBAT_BLDG);
         
    }

    /** unit class indicator */
    protected int[][] K;
    /** proximity p[i][j] proximity of ally i to enemy j */
    protected double[][] p;

    protected int nAllies;
    protected int nEnemies;


    /**
     * get value of each ally-target assignment.  The LP objective function
     * is Vx.
     *
     * @param allies
     * @param enemies
     * @return
     */
    public abstract double[][] getValues(double[] params);

    protected static int[][] getUnitClasses( List<Unit> enemies) {
        int[][] K = new int[unitClasses.size()][enemies.size()];
        for (int i = 0; i < enemies.size(); i++) {
            Unit u = enemies.get(i);
            UnitClass k = unitClasses.get(u.getUnitTypeString());
            if (k == null) {
                throw new RuntimeException("Unknown unit class for unit type " + u.getUnitTypeString());
            }
            K[k.ordinal()][i] = 1;
        }
        return K;
    }

    /** proxity in range 0..1 */
    protected static double[][] getProximities(List<Unit> allies, List<Unit> enemies) {
        double[][] p = new double[allies.size()][enemies.size()];
        for (int i = 0; i < allies.size(); i++) {
            for (int j = 0; j < enemies.size(); j++) {
                Unit ally = allies.get(i);
                Unit enemy = enemies.get(j);
                double dist = Spatial.distance(ally.getLocX(), ally.getLocY(),
                        enemy.getLocX(), enemy.getLocY());

                if (dist > MAX_DIST) {
                    p[i][j] = 0;
                } else {
                    p[i][j] = 1 - (dist/(float)MAX_DIST);
                }
            }
        }
        return p;
    }

    /**
     * Indicates if ally is already attacking enemy.
     *
     * a_i,j = | 1 if ally i is currently attacking target j
     *         | 0 otherwise
     */
    protected static int[][] getAttacking(List<Unit> allies, List<Unit> enemies) {
        int[][] a = new int[allies.size()][enemies.size()];
        for (int i = 0; i < allies.size(); i++) {
            for (int j = 0; j < enemies.size(); j++) {
                Unit ally = allies.get(i);
                Unit enemy = enemies.get(j);
                if (ally.getCurrentTarget() == enemy.getUnitId()) {
                    a[i][j] = 1;
                } else {
                    a[i][j] = 0;
                }
            }
        }
        return a;
    }

    /**
     * Indicates if target is moving away from ally or not.
     *
     * m_i,j = | 0   if target j is moving away from attacker
     *         | 1   otherwise
     */
    protected static int[][] getMotion(List<Unit> allies, List<Unit> enemies) {
        int[][] m = new int[allies.size()][enemies.size()];
        for (int i = 0; i < allies.size(); i++) {
            for (int j = 0; j < enemies.size(); j++) {
                Unit ally = allies.get(i);
                Unit enemy = enemies.get(j);
                m[i][j] = isMovingAway(ally, enemy) ? 0 : 1;
            }
        }
        return m;
    }

    /** Is the enemy moving away from the ally? */
    public static boolean isMovingAway(Unit ally, Unit enemy) {
        Direction toEnemy = Spatial.getDirection(ally.getLocX(), ally.getLocY(),
                enemy.getLocX(), enemy.getLocY());
        Direction enemyDir = enemy.getDirection();
        int d = toEnemy.ordinal() - enemyDir.ordinal();
        return Math.abs(d) > 5 || Math.abs(d) < 3;
    }

    protected static int[][] getEnemyCanAttack(List<Unit> allies, List<Unit> enemies) {
        int[][] t = new int[allies.size()][enemies.size()];
        for (int i = 0; i < allies.size(); i++) {
            for (int j = 0; j < enemies.size(); j++) {
                Unit ally = allies.get(i);
                Unit enemy = enemies.get(j);
                if (enemy.getBasicDamage() > 0) {
                    double dist = Spatial.distance(ally.getLocX(), ally.getLocY(),
                            enemy.getLocX(), enemy.getLocY());

                    if (dist <= enemy.getMaxAttackRange()) {
                        t[i][j] = 1;
                    }
                }
            }
        }
        return t;
    }

    public abstract double getPartialDeriv(int param, double[][] x);

    /** feature of enemy j */
    protected double sum(double[] f, double[][] x) {
        double s = 0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                s += f[j] * x[i][j];
            }
        }
        return s;
    }

    protected double sum(int[] f, double[][] x) {
        double s = 0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                s += f[j] * x[i][j];
            }
        }
        return s;
    }

    protected double sum(double[][] f, double[][] x) {
        double s = 0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                s += f[i][j] * x[i][j];
            }
        }
        return s;
    }

    protected double sum(int[][] f, double[][] x) {
        double s = 0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                s += f[i][j] * x[i][j];
            }
        }
        return s;
    }

    public int getNumAllies() {
        return nAllies;
    }

    public int getNumEnemies() {
        return nEnemies;
    }

    public int getNumUnitClasses() {
        return UnitClass.values().length;
    }
}
