package orst.stratagusai.stratsim.planner;

import java.util.LinkedHashMap;
import java.util.Map;
import orst.stratagusai.stratplan.model.Region;

/**
 * Parameters to use when generating a StrategicPlan.
 * 
 * @author Brian
 */
public class StrategyTemplate implements Cloneable {

    /** predefined named templates */
    protected static Map<String,StrategyTemplate> predefined;

    /** possible goal priority orders. */
    public static final GoalType[][]  priorityOrders = {
        // high, medium, low priority.
        {GoalType.SECURE_ALLIED_BASE, GoalType.SECURE_CHOKEPOINT, GoalType.SECURE_ENEMY_BASE}, // 0: defensive
        {GoalType.SECURE_ALLIED_BASE, GoalType.SECURE_ENEMY_BASE},                             // 1: defend-attack
        {GoalType.SECURE_ENEMY_BASE, GoalType.SECURE_ALLIED_BASE, },                           // 2: attack-defend
        {GoalType.SECURE_CHOKEPOINT, GoalType.SECURE_ALLIED_BASE, GoalType.SECURE_ENEMY_BASE}, // 3: chokepoint
        {GoalType.SECURE_ENEMY_BASE, GoalType.SECURE_CHOKEPOINT, GoalType.SECURE_ALLIED_BASE}, // 4: offensive
        {GoalType.SECURE_ENEMY_BASE}                                                           // 5: offensive only
    };

    protected static final int DEFENSIVE_PRIORITIES = 0;
    protected static final int DEFEND_ATTACK_PRIORITIES = 1;
    protected static final int ATTACK_DEFEND_PRIORITIES = 2;
    protected static final int CHOKEPOINT_PRIORITIES = 3;
    protected static final int RUSH_PRIORITIES = 4;
    protected static final int OFFENSIVE_ONLY_PRIORITIES = 5;
    
    protected static Parameter[] parameterSpecs = {
        // integer parameters
        // name, min, max, isIntegral
        new Parameter("base force", 1, 8, true),
        new Parameter("enemy base force", 1, 8, true),
        new Parameter("chokepoint force", 1, 5, true),
        new Parameter("goal order", 0, priorityOrders.length-1, true),
        new Parameter("mass attack", 0, 1, true),
        // real parameters
        new Parameter("time to target",  0, 1, false),
        new Parameter("enemy damage", -1, 1, false),
        new Parameter("damage rato",   -1, 1, false),
        new Parameter("goal inertia",  0, 0, false)
    };

    public static Parameter[] getParameterSpecs() {
        return parameterSpecs;
    }

    static {
        // make predefined templates available by name
        StrategyTemplate[] templates = {
            StrategyTemplate.getBalanced(7, false),
            StrategyTemplate.getBalanced(7, true),
            StrategyTemplate.getBalanced(9, false),
            StrategyTemplate.getBalanced(9, true),
            StrategyTemplate.getBalanced(10, false),
            StrategyTemplate.getBalanced(10, true),
            StrategyTemplate.getDefensive(7, false),
            StrategyTemplate.getDefensive(7, true),
            StrategyTemplate.getDefensive(10, false),
            StrategyTemplate.getDefensive(10, true),
            StrategyTemplate.getRush(7, false),
            StrategyTemplate.getRush(7, true),
            StrategyTemplate.getRush(9, false),
            StrategyTemplate.getRush(10, false),
            StrategyTemplate.getRush(10, true),
            StrategyTemplate.getRush(12, false),
            StrategyTemplate.getRush(12, true),
            StrategyTemplate.getOffensiveOnly(5, false),
            StrategyTemplate.getOffensiveOnly(5, true),
            StrategyTemplate.getOffensiveOnly(7, false),
            StrategyTemplate.getOffensiveOnly(7, true),
            StrategyTemplate.getOffensiveOnly(10, false),
            StrategyTemplate.getOffensiveOnly(10, true),
            StrategyTemplate.getOffensiveOnly(12, false),
            StrategyTemplate.getOffensiveOnly(12, true),
            StrategyTemplate.getSecureChokepoint(6),
            StrategyTemplate.getSecureChokepoint(8),
            StrategyTemplate.getSecureChokepoint(10),
            StrategyTemplate.getAttackDefend(5, false),
            StrategyTemplate.getAttackDefend(5, true),
            StrategyTemplate.getAttackDefend(6, false),
            StrategyTemplate.getAttackDefend(6, true),
            StrategyTemplate.getAttackDefend(7, false),
            StrategyTemplate.getAttackDefend(7, true),
            StrategyTemplate.getAttackDefend(9, false),
            StrategyTemplate.getAttackDefend(9, true),
            StrategyTemplate.getDefendAttack(5, false),
            StrategyTemplate.getDefendAttack(5, true),
            StrategyTemplate.getDefendAttack(6, false),
            StrategyTemplate.getDefendAttack(6, true),
            StrategyTemplate.getDefendAttack(7, false),
            StrategyTemplate.getDefendAttack(7, true),
            StrategyTemplate.getDefendAttack(9, false),
            StrategyTemplate.getDefendAttack(9, true),
            // Counter Strategy to dfnd-atk_7 found by FindCounterStrategy.
            // [StrategyTemplate unnamed 6 3 2 4 0 0.99 0.53 0.38 0.48]
            StrategyTemplate.getStrategy("synth-0", 6, 3, 2, 4, false, .99F, .53F, 0, 0),
            // Counter strategy to rush_9 found by FindCounterStrategy.
            StrategyTemplate.getStrategy("synth-1",8,8,1,0,true,0.50F,0.00F,-0.70F,0.00F)
        };
        predefined = new LinkedHashMap<String,StrategyTemplate>();
        for (StrategyTemplate s : templates) {
            predefined.put(s.getName(), s);
        }
    }

    /** template name */
    protected String name = "unnamed";

    /** number of combat units to use for each goal type.
      */
    protected int baseForce;
    protected int enemyBaseForce;
    protected int chokepointForce;

    /** priority order of goal types in this strategy */
    protected int priorityOrderIndex;

    /** can groups attack the same target, or should planner prefer to assign
     *  each to its own target? */
    protected boolean massAttack;

    // real parameters
    protected float timeToTarget = 1;
    protected float enemyDamage = 0;
    protected float damageRatio = 1;
    protected float goalInertia = 0;


    public static StrategyTemplate getNamedTemplate(String name) {
        StrategyTemplate s = predefined.get(name);
        if (s == null && name.startsWith("strategy")) {
            // split name into parameters.
            String[] parts = name.split("_");
            float[] params = new float[9];
            for (int i = 0; i < 9; i++) {
               params[i] = Float.parseFloat(parts[i+1]);
            }
            s = StrategyTemplate.getStrategy(params);
        }
        if (s == null) {
            throw new RuntimeException("Unknown strategy template '" + name + "'");
        }
        return s;
    }

    public static StrategyTemplate getRush(int attackThreshold, boolean massAttack) {
        final int t = attackThreshold;
        String name = "rush_" + t;
        if (massAttack) {
            name += "_mass";
        }
        return getStrategy(name,
                   (int) Math.round(t * .75),                         // base
                   t,                         // enemy base
                   (int) Math.round(t * .4),                         // chokepoint
                   // chokepoint, base, enemy
                   RUSH_PRIORITIES,
                   massAttack
                   );
    }

    public static StrategyTemplate getBalanced(int groupSize, boolean massAttack) {
        final int n = groupSize;
        String name = "balanced_" + n;
        if (massAttack) {
            name += "_mass";
        }
        return getStrategy(name, n, n, n, RUSH_PRIORITIES, massAttack);
    }

    public static StrategyTemplate getDefensive(int groupSize, boolean massAttack) {
        final int n = groupSize;
        String name = "defensive_" + n;
        if (massAttack) {
            name += "_mass";
        }
        return getStrategy(name,
                           n,                           // allied base
                           (int) Math.round(n * .9),    // enemy base
                           (int) Math.round(n * .9),    // chokepoint
                           DEFENSIVE_PRIORITIES,
                           massAttack
                           );
    }

     public static StrategyTemplate getOffensiveOnly(int groupSize, boolean massAttack) {
        final int n = groupSize;
        String name = "offensive_" + n;
        if (massAttack) {
            name += "_mass";
        }
        return getStrategy(name,
                           0,    // allied base
                           n,    // enemy base
                           0,    // chokepoint
                           OFFENSIVE_ONLY_PRIORITIES,
                           massAttack
                           );
    }

    /**
     * Sometimes a group attacking the enemy base gets stuck in a cul-de-sac
     * when the chokepoint is blocked (path plannng to avoid the blocking units?).
     * we need a strategy that prioritizes securing the chokepoint.
     * 
     * @param groupSize
     * @return
     */
    public static StrategyTemplate getSecureChokepoint(int groupSize) {
        final int n = groupSize;
        String name = "chokepoint_" + n;

        return getStrategy(name,
                           n,                         // allied base
                           n,                         // enemy base
                           n,                         // chokepoint
                           // chokepoint, base, enemy
                           CHOKEPOINT_PRIORITIES,
                           false
                           );
    }

    public static StrategyTemplate getAttackDefend(int groupSize, boolean massAttack) {
        final int n = groupSize;
        String name = "atk-dfnd_" + n;
        if (massAttack) {
            name += "_mass";
        }
        return getStrategy(name,
                           n,    // allied base
                           n,    // enemy base
                           0,    // chokepoint
                           ATTACK_DEFEND_PRIORITIES,
                           massAttack
                           );
    }

    public static StrategyTemplate getDefendAttack(int groupSize, boolean massAttack) {
        final int n = groupSize;
        String name = "dfnd-atk_" + n;
        if (massAttack) {
            name += "_mass";
        }
        return getStrategy(name,
                           n,    // allied base
                           n,    // enemy base
                           0,    // chokepoint
                           DEFEND_ATTACK_PRIORITIES,
                           massAttack
                           );
    }

    public static StrategyTemplate getStrategy(int baseGoalForce,
                                               int enemyBaseGoalForce,
                                               int chokepointGoalForce,
                                               int priorities,
                                               boolean massAttack) {
        String name = String.format("s_%d_%d_%d_%d_%d", baseGoalForce, enemyBaseGoalForce, chokepointGoalForce,priorities,massAttack?1:0);
        return getStrategy(name, baseGoalForce, enemyBaseGoalForce, chokepointGoalForce, priorities, massAttack);
    }
    
    public static StrategyTemplate getStrategy(String name,
                                               int baseGoalForce,
                                               int enemyBaseGoalForce,
                                               int chokepointGoalForce,
                                               int prioritiesIndex,
                                               boolean massAttack) {
        StrategyTemplate p = new StrategyTemplate(name);
        p.setForce(GoalType.SECURE_ALLIED_BASE, baseGoalForce);
        p.setForce(GoalType.SECURE_ENEMY_BASE,  enemyBaseGoalForce);
        p.setForce(GoalType.SECURE_CHOKEPOINT,  chokepointGoalForce);
        p.setPriorities(prioritiesIndex);
        p.massAttack = massAttack;
        return p;
    }

    public static StrategyTemplate getStrategy(String name,
                                               int baseGoalForce,
                                               int enemyBaseGoalForce,
                                               int chokepointGoalForce,
                                               int prioritiesIndex,
                                               boolean massAttack,
                                               float timeToTarget,
                                               float enemyDamage,
                                               float damageRatio,
                                               float goalInertia) {
        StrategyTemplate p = new StrategyTemplate(name);
        p.setForce(GoalType.SECURE_ALLIED_BASE, baseGoalForce);
        p.setForce(GoalType.SECURE_ENEMY_BASE,  enemyBaseGoalForce);
        p.setForce(GoalType.SECURE_CHOKEPOINT,  chokepointGoalForce);
        p.setPriorities(prioritiesIndex);
        p.massAttack = massAttack;
        p.timeToTarget = timeToTarget;
        p.enemyDamage = enemyDamage;
        p.damageRatio = damageRatio;
        p.goalInertia = goalInertia;
        return p;
    }

    public static StrategyTemplate getStrategy(float[] p) {
        String name = String.format("s_%.0f_%.0f_%.0f_%.0f_%.0f_%.2f_%.2f_%.2f_%.2f",
            p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8]);
        StrategyTemplate s = new StrategyTemplate(name);
        s.setForce(GoalType.SECURE_ALLIED_BASE, (int) p[0]);
        s.setForce(GoalType.SECURE_ENEMY_BASE,  (int) p[1]);
        s.setForce(GoalType.SECURE_CHOKEPOINT,  (int) p[2]);
        s.setPriorities((int) p[3]);
        s.massAttack = p[4] == 1 ? true : false;
        s.timeToTarget = p[5];
        s.enemyDamage = p[6];
        s.damageRatio = p[7];
        s.goalInertia = p[8];
        return s;
    }

    public StrategyTemplate() {
    }


    public StrategyTemplate(String name) {
        this.name = name;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Strategies are equal if their parameters are equal (ignore name).
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StrategyTemplate other = (StrategyTemplate) obj;
        if (this.baseForce != other.baseForce) {
            return false;
        }
        if (this.enemyBaseForce != other.enemyBaseForce) {
            return false;
        }
        if (this.chokepointForce != other.chokepointForce) {
            return false;
        }
        if (this.priorityOrderIndex != other.priorityOrderIndex) {
            return false;
        }
        if (this.massAttack != other.massAttack) {
            return false;
        }
        if (this.timeToTarget != other.timeToTarget) {
            return false;
        }
        if (this.enemyDamage != other.enemyDamage) {
            return false;
        }
        if (this.damageRatio != other.damageRatio) {
            return false;
        }
        if (this.goalInertia != other.goalInertia) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + this.baseForce;
        hash = 11 * hash + this.enemyBaseForce;
        hash = 11 * hash + this.chokepointForce;
        hash = 11 * hash + this.priorityOrderIndex;
        hash = 11 * hash + (this.massAttack ? 1 : 0);
        hash = 11 * hash + Float.floatToIntBits(this.timeToTarget);
        hash = 11 * hash + Float.floatToIntBits(this.enemyDamage);
        hash = 11 * hash + Float.floatToIntBits(this.damageRatio);
        hash = 11 * hash + Float.floatToIntBits(this.goalInertia);
        return hash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setForce(GoalType type, int number) {
        switch (type) {
            case SECURE_ENEMY_BASE:
                enemyBaseForce = number;
                break;
            case SECURE_ALLIED_BASE:
                baseForce = number;
                break;
            case SECURE_CHOKEPOINT:
                chokepointForce = number;
                break;
            default:
                throw new RuntimeException("Unknown goal type " + type);
        }
    }

    public int getForce(GoalType type) {
        switch (type) {
            case SECURE_ENEMY_BASE:
                return enemyBaseForce;
            case SECURE_ALLIED_BASE:
                return baseForce;
            case SECURE_CHOKEPOINT:
                return chokepointForce;
            case SECURE_REGION:
                return enemyBaseForce;
            default:
                throw new RuntimeException("Unknown goal type " + type);
        }
    }

    public int getPriority(GoalType goal) {
        GoalType[] order = priorityOrders[priorityOrderIndex];
        int index = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == goal) {
                index = i;
            }
        }
        if (index == -1) {
            return 0;
        }
        return order.length - index;
    }

    /**
     * 
     */
    private void setPriorities(int index) {
        priorityOrderIndex = index;
    }

    public boolean isMassAttack() {
        return massAttack;
    }


    public float getEnemyDamage() {
        return enemyDamage;
    }

    public void setEnemyDamage(float enemyDamage) {
        this.enemyDamage = enemyDamage;
    }

    public float getDamageRatio() {
        return damageRatio;
    }

    public void setDamageRatio(float forceRato) {
        this.damageRatio = forceRato;
    }

    public float getGoalInertia() {
        return goalInertia;
    }

    public void setGoalInertia(float goalInertia) {
        this.goalInertia = goalInertia;
    }

    public float getTimeToTarget() {
        return timeToTarget;
    }

    public void setTimeToTarget(float timeToTarget) {
        this.timeToTarget = timeToTarget;
    }

    public void setParameter(Parameter param, float value) {
        /*
        new Parameter("base force", 1, 10, true),
        new Parameter("enemy base force", 1, 10, true),
        new Parameter("chokepoint force", 1, 5, true),
        new Parameter("goal order", 0, priorityOrders.length-1, true),
        new Parameter("mass attack", 0, 1, true),
        // real parameters
        new Parameter("time to target",  0, 2, false),
        new Parameter("enemy damage", -.1F, 2, false),
        new Parameter("damage rato",   -.1F, 2, false),
        new Parameter("goal inertia", -.1F, 2, false)
         */
        if (param.isIntegral() && Math.floor(value) != value) {
            throw new RuntimeException("non-integer value " + value + " set in integer parameter " + name);
        }
        assert param.getMin() <= value && value <= param.getMax();
        
        if (parameterSpecs[0] == param) {
            setForce(GoalType.SECURE_ALLIED_BASE, (int) value);
        } else if (parameterSpecs[1] == param) {
            setForce(GoalType.SECURE_ENEMY_BASE, (int) value);
        } else if (parameterSpecs[2] == param) {
            setForce(GoalType.SECURE_CHOKEPOINT, (int) value);
        } else if (parameterSpecs[3] == param) {
            priorityOrderIndex = (int) value;
            setPriorities(priorityOrderIndex);
        } else if (parameterSpecs[4] == param) {
            massAttack = value == 1;
        } else if (parameterSpecs[5] == param) {
            timeToTarget = value;
        } else if (parameterSpecs[6] == param) {
            enemyDamage = value;
        } else if (parameterSpecs[7] == param) {
            damageRatio = value;
        } else if (parameterSpecs[8] == param) {
            goalInertia = value;
        } else {
            throw new RuntimeException("unknown parameter " + name);
        }
    }

    /*
    public float getParameter(Parameter param) {
        if (parameterSpecs[0] == param) {
            return baseForce;
        } else if (parameterSpecs[1] == param) {
            return enemyBaseForce;
        } else if (parameterSpecs[2] == param) {
            return chokepointForce;
        } else if (parameterSpecs[3] == param) {
            return priorityOrderIndex;
        } else if (parameterSpecs[4] == param) {
            return massAttack ? 1 : 0;
        } else if (parameterSpecs[5] == param) {
            return timeToTarget;
        } else if (parameterSpecs[6] == param) {
            return enemyDamage;
        } else if (parameterSpecs[7] == param) {
            return damageRatio;
        } else if (parameterSpecs[8] == param) {
            return goalInertia;
        } else {
            throw new RuntimeException("unknown parameter " + name);
        }
    } */

   public StrategicGoal getEnemyBaseGoal(Region region) {
       assert region != null;
        GoalType type = GoalType.SECURE_ENEMY_BASE;
        StrategicGoal g = new StrategicGoal(
            type,
            getPriority(type),
            region);
        return g;
    }

    public StrategicGoal getChokepointGoal(Region region) {
        assert region != null;
        GoalType type = GoalType.SECURE_CHOKEPOINT;
        StrategicGoal g = new StrategicGoal(
            type,
            getPriority(type),
            region);
        return g;
    }

    public StrategicGoal getAlliedBaseGoal(Region region) {
        assert region != null;
        GoalType type = GoalType.SECURE_ALLIED_BASE;
        StrategicGoal g = new StrategicGoal(
            type,
            getPriority(type),
            region);
        return g;
    }

    public StrategicGoal getRegionGoal(Region region) {
        assert region != null;
        GoalType type = GoalType.SECURE_REGION;
        StrategicGoal g = new StrategicGoal(
            type,
            1,          // LOW
            region);
        return g;
    }

    @Override
    public String toString() {
        return String.format("[StrategyTemplate %s %d %d %d %d %d %.2f %.2f %.2f %.2f]",
                name,
                baseForce,
                enemyBaseForce,
                chokepointForce,
                priorityOrderIndex,
                massAttack ? 1 : 0,
                timeToTarget,
                enemyDamage,
                damageRatio,
                goalInertia );
    }

    public String asConstructor(String name) {
        return String.format(
            "StrategyTemplate.getStrategy(\"%s\",%d,%d,%d,%d,%s,%.2fF,%.2fF,%.2fF,%.2fF)",
                name,
                baseForce,
                enemyBaseForce,
                chokepointForce,
                priorityOrderIndex,
                massAttack ? "true" : "false",
                timeToTarget,
                enemyDamage,
                damageRatio,
                goalInertia );
    }
}
