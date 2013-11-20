package orst.stratagusai.stratsim.model;

import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.mgr.Manager;
import orst.stratagusai.stratplan.mgr.StrategyManager;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratsim.mgrs.ProductionManager;
import orst.stratagusai.stratsim.mgrs.StrategyManagerSim;
import orst.stratagusai.stratsim.mgrs.TacticalManager;

/**
 * Wrap the game simulator in a plan interpreter.  This class has a role
 * similar to StrategyController.
 *
 * @author Brian
 */
public class SimController {
    private static final Logger log = Logger.getLogger(SimController.class);
    private static final Logger simlog = Logger.getLogger("game_sim_event");

    /**
     * in stratagus.h:
     * #define CYCLES_PER_SECOND  30  // 1/30s 0.33ms
     *
     * 20000/30  is about 11 minutes of game time.
     */
    public static final int MAX_CYCLES = 80000;

    /**
     * A 50 cycle update works well to differentiate unit type movement rates.
     * see ActionMove.getDisplacement()
     *
     */
    public static final int CYCLE_INCREMENT = 50;

    protected StrategicState state;

    protected Simulator simulator;

    /** strategy managers for player 0 and player 1. */
    protected StrategyManager[] smgr = new StrategyManager[2];

    public SimController() {}

    public SimController(SimState state) {
        simulator = new Simulator(state);
        this.state = state;
        makeStategyManager(0);
        makeStategyManager(1);
    }

    public StrategicState getState() {
        return state;
    }

    private void makeStategyManager(int playerId) {
        StrategyManager mgr = new StrategyManagerSim();
        mgr.setPlayerId(playerId);
        Manager tacman = new TacticalManager(playerId);
        mgr.addManager(tacman, "attack");
        mgr.addManager(tacman, "secure");
        Manager prodman = new ProductionManager(playerId);
        mgr.addManager(prodman, "produce");
        smgr[playerId] = mgr;
    }

    /**
     * Set plan.
     *
     */
    public void setPlan(int playerId, StrategicPlan plan) {
        smgr[playerId].setPlan(plan);
    }

    public StrategicPlan[] getPlans() {
        StrategicPlan[] plans = new StrategicPlan[smgr.length];
        plans[0] = smgr[0].getPlan();
        plans[1] = smgr[1].getPlan();
        return plans;
    }

    public int getCycle() {
        return state.getCycle();
    }

    public Simulator getSimulator() {
        return simulator;
    }

    /**
     * simulate to end of game.
     */
    public void simulate() {

        simulate(MAX_CYCLES);
        if (simlog.isInfoEnabled()) {
            String msg = "scores";
            int[] scores = getScores();
            for (int i = 0; i < scores.length; i++) {
                msg += " " + scores[i];
            }
            msg += logUnits();
            simlog.info(state.getCycle() + "\tEnd simulation. " + msg);
        }
    }

    /**
     * simulate given number of cycles.
     */
    public void simulate(int cycles) {
        GameState game = state.getGameState();
        if (game.getCycle() == 0) {
            if (simlog.isInfoEnabled()) {
                String msg = logUnits();

                simlog.info("0\tBegin simulation. " +
                               smgr[0].getPlan().getName() + " vs. " +
                               smgr[1].getPlan().getName() +
                               msg);
            }
            for (StrategyManager m : smgr) {
                m.beginEpisode(game);
            }
        } else {
            simlog.info(String.format("%d\tContinue simulation. %s vs. %s.",
                   game.getCycle(),
                   smgr[0].getPlan().getName(),
                   smgr[1].getPlan().getName()));
        }
        int start = game.getCycle();
        int end = start + cycles;
        while (!simulator.isTerminal() && game.getCycle() < end) {
            // Managers set actions (UnitCommands) into GameState
            for (StrategyManager m : smgr) {
                m.nextActions(game);
            }
            game.getEvents().clear();
            Set<UnitCommand> cmds = game.getCommandQueue();
            simulator.execute(cmds);
            game.setCycle(game.getCycle()+CYCLE_INCREMENT);
        }
        if (simulator.isTerminal()) {
            for (StrategyManager m : smgr) {
                m.endEpisode(game);
            }
            int[] scores = getScores();
            String msg = "Game over! Scores " + scores[0] + ", " + scores[1];
            log.debug(msg);
            simlog.info(game.getCycle() + "\t" + msg);
        }
    }

    /**
     * Prepare for planning by
     * - removing dead units
     * - removing commands (actions)
     * - updating groups.
     */
    public void update() {
        state.getGameState().removeDeadUnits();
        simulator.clearCommands(); // clear simulator active commands, and
                                   //  GameState command queue.
        state.updateGroups();
    }

    public int[] getScores() {
        return simulator.getScores();
    }

    public boolean isTerminal() {
        return simulator.isTerminal();
    }

    public String logUnits() {
        StringBuffer buf = new StringBuffer("\n");
        for (Unit u : state.getUnits()) {
            buf.append("  ");
            buf.append(u.toString());
            buf.append("\n");
        }
        return buf.toString();
    }
}
