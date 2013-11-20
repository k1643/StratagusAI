package orst.stratagusai.stratsim.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;

/**
 *
 * @author Brian
 */
public class Simulator {
    private static final Logger log = Logger.getLogger(Simulator.class);
    private static final Logger simlog = Logger.getLogger("game_sim_event");
    
    protected SimState state;

    protected GameEvaluator evaluator = new CombatEvaluator();

    /** commands in progress.  Indexed by Unit Id. */
    protected Map<Integer,Action> activeCmds = new LinkedHashMap<Integer,Action>();

    protected ProductionEstimation estimator = ProductionEstimation.getEstimator();

    protected WargusUnitPrototypes prototypes = new WargusUnitPrototypes();

    protected List<AttributeDifference> changes = new ArrayList();

    static {
        // configure game event log.
        String filename = "game_sim_events.txt";
        try {
            simlog.setLevel(Level.OFF); // turn on programmatically in main() function
            Layout layout = new SimpleLayout();
            Appender append = new FileAppender(layout, filename, true);
            simlog.addAppender(append);
        } catch (IOException ex) {
            log.error("unable to open game event log file '"+filename+"'.");
        }
        
    }

    public Simulator() {}

    public Simulator(SimState state) {
        setState(state);
    }

    public void setState(SimState state) {
        this.state = state;
        evaluator.setGame(state.getGameState());
    }

    public StrategicState getState() {
        return state;
    }

    public GameState getGameState() {
        return state.getGameState();
    }

    public int getCycle() {
        return state.getCycle();
    }
    
    /** Is the current state terminal? */
    public boolean isTerminal() {
        return evaluator.isGameOver();
    }

    public Unit getPrototype(String unitType) {
        return prototypes.getPrototype(unitType);
    }

    public int getNextUnitId() {
        return state.getNextUnitId();
    }

    public void clearCommands() {
        activeCmds.clear();
        state.getGameState().getCommandQueue().clear();
    }

    public int[] getScores() {
        return evaluator.getScores();
    }

    /**
     * advance new and active commands.
     */
    public void execute(Set<UnitCommand> commands) {
        // do active commands
        Iterator<Integer> itr = activeCmds.keySet().iterator();
        while (itr.hasNext()) {
            Action action = activeCmds.get(itr.next());
            if (action.isTerminated()) {
                simlog.info(state.getCycle() + "\tterminated " + action);
                itr.remove();
            } else if (action.exec(this) == ActionStatus.COMPLETE) {
                simlog.info(state.getCycle() + "\tcompleted " + action);
                itr.remove();
            }
        }
        // do new commands.  Do attacks first, then moves so that a group
        // newly moving into a region won't be attacked before it can attack.
        List<UnitCommand> cmds = new ArrayList<UnitCommand>(commands);
        List<UnitCommand> nonAttacks = new ArrayList<UnitCommand>();
        for (UnitCommand cmd : cmds) {
            if (cmd.getType() == CommandType.UNIT_ATTACK) {
                execNewCommand((Action) cmd);
            } else {
                nonAttacks.add(cmd);
            }
        }
        for (UnitCommand cmd : nonAttacks) {
            execNewCommand((Action) cmd);
        }
        // do default action.
        // do these last so that units have a chance to respond to attacks in
        // the same cycle.
        List<Unit> units = new ArrayList<Unit>(state.getUnits());
        for (Unit u : units) {
            execDefault(u);
        }
        applyChanges();
    }

    private void execNewCommand(Action action) {
        GameState game = state.getGameState();
        if (action.isTerminated()) {
            simlog.info(state.getCycle() + "\tterminated " + action);
            game.removeCommand(action);
        } else if (!activeCmds.containsKey(action.getUnitID())) {
            game.removeCommand(action);
            simlog.info(state.getCycle() + "\tbegin action " + action);
            if (action.exec(this) != ActionStatus.COMPLETE) {
                activeCmds.put(action.getUnitID(), action);
            } else {
                simlog.info(state.getCycle() + "\tcompleted " + action);
            }
        }
    }

    /**
     * Actions change the state by adding an attribute difference.  Changes
     * are applied at the end of advance();
     *
     * @param diff
     */
    public void addChange(AttributeDifference diff) {
        changes.add(diff);
    }

    protected void applyChanges() {
        for (AttributeDifference diff : changes) {
            applyDifference(diff);
        }
        changes.clear();
    }

    protected void applyDifference(AttributeDifference diff) {
        Unit u = state.getUnit(diff.getId());
        String name = diff.getName();
        if ("hitPoints".equals(name)) {
            u.setHitPoints(u.getHitPoints() + diff.getDifference());
            int cycle = getState().getCycle();
            simlog.info(cycle + "\tunit " + u.getOwnerId() + ":" + u.getUnitId() + " HP=" + u.getHitPoints());
            if (u.isDead()) {                
                simlog.info(cycle + "\tdead unit owner=" + u.getOwnerId() +
                                         " id=" + u.getUnitId() +
                                         " type=" + u.getUnitTypeString() + ".");
            }
        } else if ("LocX".equals(name)) {
            u.setLocX(u.getLocX() + diff.getDifference());
        } else if ("LocY".equals(name)) {
            u.setLocY(u.getLocY() + diff.getDifference());
        } else {
            throw new RuntimeException("unimplemented attribute difference: " + name);
        }      
    }

    /**
     * Execute any default actions for the given unit.
     * @param u
     */
    private void execDefault(Unit u) {
        defaultDefense(u);
        defaultGrowth(u);
    }

    private void defaultDefense(Unit u) {
        // if the unit is not attacking, it may need to defend against
        // attackers in addition to doing its assigned actions.
        /*
        if (u.getOwnerId() == GameState.NEUTRAL_PLAYER ||
            u.isDead()) {
            return;
        }
        
        if (u.getBasicDamage() == 0) {
            // can't attack.
            return;
        }
        Action a = activeCmds.get(u.getUnitId());
        if (a == null || a.getType() != CommandType.UNIT_ATTACK) {
            Region r = state.getMap().getRegion(u);

            // TODO: code from ActionAttack.  Refactor into an AttackRule
            // or SelectTarget class.
            Set<Unit> enemies = state.getEnemyUnits(u.getOwnerId(), r);
            Iterator<Unit> itr = enemies.iterator();
            while (itr.hasNext()) {
                Unit enemy = itr.next();
                if (enemy.isDead()) {
                    itr.remove();
                }
            }
            final int M = enemies.size();
            for (Unit enemy : enemies) {
                String msg = u.getUnitId() + " attacks " + enemy.getUnitId();
                event_log.info(state.getCycle() + "\t" + msg);

                int damage = Math.round(Attack.getDamage( u, enemy)/(float)M);
                addChange(new AttributeDifference(enemy.getUnitId(),
                                              "hitPoints",
                                              -damage));

            }
        } */
    }

    private void defaultGrowth(Unit u) {
        /*
        if (!(u instanceof UnitGroup) ||
            !UnitAnalysis.isBuilding(u) ||
            isActive(u) ||
            u.isAttacking()) {
            return;
        }
        UnitGroup producer = (UnitGroup) u;
     
        // assume townhall, barracks, blacksmith, stables as maximum
        // see ActionProduce for productionThresholds.
        final int LIMIT = 1200+800+775+500;
        if (producer.getHitPoints() >= LIMIT) {
            return;
        }
        // increase hitpoints by 0.7hp every cycle.
        // 0.7 is close to the hitpoints/(production time) for the
        // production units townhall, barracks, elven lumber mill, and
        // blacksmith needed to produce low-level combat units.
        //
        int hp = (int) Math.max(1, Math.round(.7 * CYCLE_INCREMENT));
        addChange(new AttributeDifference(producer.getUnitId(),
                                          "hitPoints",
                                          hp));
         
         */
    }

    /**
     * true if given unit is currently executing a Command
     *
     */
    /*
    private boolean isActive(Unit u) {
        Action a = activeCmds.get(u.getUnitId());
        return a != null && a.getStatus().isActive();
    } */
}
