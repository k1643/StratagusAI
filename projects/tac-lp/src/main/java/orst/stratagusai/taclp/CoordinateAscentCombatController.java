package orst.stratagusai.taclp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.GameProxy;
import orst.stratagusai.Controller;
import orst.stratagusai.config.ControllerConfig;
import orst.stratagusai.config.Config;
import orst.stratagusai.config.ParamList;

/**
 * agent sends attackers to nearest enemy and continues battle to death.
 *
 * Learn parameters by coordinate ascent.
 *
 * @author kingbria
 */
public class CoordinateAscentCombatController implements Controller {

    private static final Logger log = Logger.getLogger(CoordinateAscentCombatController.class);

    /** number of features per ally-enemy pair: p,t,K.
     K is 5 unit classes per enemy. */
    private static final int k = 2+5;
    
    /** the player this agent plays for */
    private int playerId;

    /** best average reward found during evaluation cycle. */
    private double bestAvgReward = Double.NEGATIVE_INFINITY;

    /** refresh at each game update */
    private List<Unit> enemyUnits;
    private List<Unit> allyUnits;

    private int maxAllyHP;
    private int maxEnemyHP;

    /** p,t,K0,...K4
     *
     * parameters used in TargetAssigner.getValues().
     */
    private RealVector params =
            new ArrayRealVector(new double[] {.01,  // proximity
                                              .01,  // enemy can attack some ally
                                              .01,  // is peasant
                                              .01,  // is combat
                                              .01,  // is combat bldg.
                                              .01,  // is production bldg.
                                              .01   // is support
                                           });

    private static final int EPISODES_PER_PARAM_VALUE = 10;

    private static final float PARAM_STEP = .1F;
    
    /** the parameter currently being trained. */
    private int paramTrained = -1;

    /** paramter value giving highest reward sum */
    private double bestParam;

    /** sum of rewards for current training param value */
    //private double paramRewardSum;

    private double bestRewardSum;

    private Features2 features;

    private int prevAlliedHP = -1;
    private int prevEnemyHP = -1;

    /** previous action index for each unit */
    //private Map<Integer, Integer> previousAction;
    /** episode counter */
   // private int episode;
    private boolean training;

    private boolean done;

    /** sum of rewards during a cycle. */
    private double reward_sum;

    private int evaluation_episode;

    /** number of training episodes */
    private int training_episode;

    public CoordinateAscentCombatController() {
    }

    public CoordinateAscentCombatController(int playerId) {
        this.playerId = playerId;
    }

    public void configure(ControllerConfig conf) {

    }

    public ControllerConfig getConfig() {
        ControllerConfig conf = new ControllerConfig();
        conf.setControllerClassName(CoordinateAscentCombatController.class.getName());
        return conf;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public boolean isTraining() {
        return training;
    }

    public boolean isDone() {
        return done;
    }

    public void beginSession(GameProxy game) {
        done = false;
    }

    public void beginCycle(GameProxy game, boolean training) {  
        this.training = training;
        reward_sum = 0;
        bestRewardSum = Float.NEGATIVE_INFINITY;
        if (training) {
            training_episode = 0;
            paramTrained++;  // train next parameter
            if (paramTrained >= params.getDimension()) {
                throw new RuntimeException("more training cycles than parameters.");
            }
            params.setEntry(paramTrained, -.2);
            bestParam = Double.NaN;
        } else {
            evaluation_episode = 0;
        }
    }

    public void beginEpisode(GameProxy game) {
        prevAlliedHP = -1;
        prevEnemyHP = -1;

        allyUnits = new ArrayList<Unit>(game.getUnits(playerId).values());
        enemyUnits = new ArrayList<Unit>(game.getEnemyUnits(playerId).values());
        maxAllyHP = getHP(allyUnits);
        maxEnemyHP = getHP(enemyUnits);

        if (training) {
            training_episode++;
        } else {
            evaluation_episode++;
        }
    }

    public void nextActions(GameProxy game) {
        log.debug("nextActions(). cycle=" + game.getCurrentCycle());
        allyUnits = new ArrayList<Unit>(game.getUnits(playerId).values());
        enemyUnits = new ArrayList<Unit>(game.getEnemyUnits(playerId).values());
        removeDeadOrDying(allyUnits);
        removeDeadOrDying(enemyUnits);
        
        assignTargets(game);

        prevAlliedHP = getHP(allyUnits);
        prevEnemyHP = getHP(enemyUnits);
    }

    protected void removeDeadOrDying(List<Unit> units) {
        Iterator<Unit> itr = units.iterator();
        while (itr.hasNext()) {
            Unit u = itr.next();
            if (u.isDead() || u.isDying()) {
                itr.remove();
            }
        }
    }

    protected void assignTargets(GameProxy game) {
        List<Unit> allies = new ArrayList<Unit>(allyUnits);
        List<Unit> enemies = new ArrayList<Unit>(enemyUnits);
        final int N = allies.size();
        final int M = enemies.size();
        features = Features2.extract(allies, enemies);
        LPSolution soln = TargetAssigner.solve(params.getData(), features, allies, enemies);
        double[] vector = soln.getVector();
        double[][] actions = new double[N][M];
        for (int n = 0; n < N; n++) {    // ally
            for (int m = 0; m < M; m++) {   // enemy
                int j = n * M + m;
                actions[n][m] = vector[j];
            }
        }

        for (int n = 0; n < N; n++) {    // ally
            for (int m = 0; m < M; m++) {   // enemy
                if (actions[n][m] > .99) {
                    Unit ally = allies.get(n);
                    Unit enemy = enemies.get(m);
                    game.myUnitCommandAttack(ally.getUnitId(), enemy.getUnitId());
                    break;
                }
            }
        }
    }


    public double getReward(GameProxy game) {
        if (prevAlliedHP == -1) {
            throw new RuntimeException("cannot get reward until after first action.");
        }

        int allyHP = getHP(allyUnits);
        int enemyHP = getHP(enemyUnits);

        if (enemyHP < allyHP) {  // we win!
            return allyHP/(float)maxAllyHP;  // how much do I have left?
        } else if (allyHP < enemyHP) {
            return -enemyHP/(float)maxEnemyHP;  // how much does enemy have left?
        }
        return 0;
    }

    public void endEpisode(GameProxy game) {
        log.debug("endEpisode()");
        reward_sum += getReward(game);
        if (training) {
            if (training_episode > 0 && training_episode % EPISODES_PER_PARAM_VALUE == 0) {
                if (reward_sum > bestRewardSum) {
                    bestRewardSum = reward_sum;
                    bestParam = params.getEntry(paramTrained);
                }
                if (params.getEntry(paramTrained) >= 1.0) {
                    // signal to game runner that training on this parameter is
                    // done.
                    training = false;
                } else {
                    params.setEntry(paramTrained,
                                    params.getEntry(paramTrained) + PARAM_STEP);
                }
                reward_sum = 0;
            }
            log.debug("params="+params);
        }
    }

    public void endCycle(GameProxy game, boolean training) {
        log.debug("endCycle(training=" + training + ")");
        if (training) {
            // end training cycle.
            params.setEntry(paramTrained, bestParam);
            if (paramTrained >= params.getDimension()-1) {
                done = true;  // tell runner we are done with training.
            }
        } else {
            // end of evaluation cycle, so record results.
            //
            if (evaluation_episode == 0) {
                throw new RuntimeException("Unable to calculate average reward: evaluation counter = 0.");
            }
            double avg = reward_sum / (float) evaluation_episode;
            if (avg > bestAvgReward) {
                bestAvgReward = avg;
    //            bestParams = new ArrayRealVector(policy.getParams());
            }

            int nAlive = 0;
            Map<Integer, Unit> allies = game.getUnits(playerId);
            for (Unit u : allies.values()) {
                if (!u.isDead()) {
                    nAlive++;
                }
            }

            try {
                PrintWriter eval = new PrintWriter(new FileWriter("evaluation.dat", true));
                eval.println(avg + "," + nAlive);
                eval.flush();
                eval.close();
            } catch (IOException e) {
                log.error("unable to write evaluation log", e);
            }
        }
    }

    public void endSession(GameProxy game) {
        ControllerConfig conf = getConfig();

        // save policy parameters
        if (params != null) {
            List<ParamList> pl = new ArrayList<ParamList>();
            ParamList l = new ParamList();
            l.setName("policy_params");
            String[] paramStr = new String[params.getDimension()];
            for (int i = 0; i < params.getDimension(); i++) {
                paramStr[i] = String.valueOf(params.getEntry(i));
            }
            l.setValues(paramStr);
            pl.add(l);
            // this assumes policy_params are the only element of the
            // Configuration parmLists.
            conf.setParamLists(pl);
        }

        try {
            Config.dump(conf, "policy_gradient_agent.yaml");
        } catch (IOException ex) {
            log.error(ex);
        }
    }

    private int getHP(List<Unit> units) {
        int hp = 0;
        for (Unit u : units) {
            hp += u.getHitPoints();
        }
        return hp;
    }


    protected void logParams(GameProxy state) {
        final String filename = "lpstdq-combat-params.csv";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            out.write(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                               state.getCurrentCycle(),
                               params.getEntry(0),
                               params.getEntry(1),
                               params.getEntry(2),
                               params.getEntry(3),
                               params.getEntry(4),
                               params.getEntry(5),
                               params.getEntry(6)
                               ));
            out.close();
        } catch (IOException ex) {
            log.error("unable to write " + filename, ex);
        }
    }

    RealVector getParams() {
        return params;
    }

    public void getStatus(Map<String,String> status) {
        status.put("training episode", String.valueOf(training_episode));
        status.put("param trained", String.valueOf(paramTrained));
        status.put("best param value", String.format("%.2f", bestParam));
        status.put("best reward sum", String.format("%.2f",bestRewardSum));
        status.put("reward sum", String.valueOf(reward_sum));
    }
}
