package orst.stratagusai.prodh;

import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import orst.stratagusai.ActionState;
import orst.stratagusai.Player;
import orst.stratagusai.TileType;
import orst.stratagusai.Unit;
import orst.stratagusai.UnitEvent;
import orst.stratagusai.UnitEventType;
import orst.stratagusai.UnitStatus;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.analysis.BuildState;
import orst.stratagusai.stratplan.analysis.UnitAnalysis;
import orst.stratagusai.stratplan.command.CommandTranslator;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.mgr.AbstractManager;
import orst.stratagusai.stratplan.mgr.TaskTerminationException;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Location;
import orst.stratagusai.stratplan.model.Rectangle;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.util.Randomizer;

/**
 * 
 *
 */
public class ProductionManager extends AbstractManager {
    private static Logger log = Logger.getLogger(ProductionManager.class);

    private static final Logger production_log = Logger.getLogger("production_event");

    protected static ProductionReqs reqs = ProductionReqs.getProductionReqs();

    static {
        String filename = "production_events.txt";
        if (log.isDebugEnabled()) {
            try {
                //production_log.setLevel(Level.INFO);
                production_log.setLevel(Level.OFF);
                Layout layout = new SimpleLayout();
                Appender append = new FileAppender(layout, filename, true);
                production_log.addAppender(append);
            } catch (IOException ex) {
                log.error("unable to open game event production file '"+filename+"'.");
            }
        } else {
            production_log.setLevel(Level.OFF);
        }

    }

    protected int defaultPeasants;

    protected Queue<Task> nextTasks = new LinkedList<Task>();

    protected Map<Task,UnitGroup> activeTasks = new LinkedHashMap<Task,UnitGroup>();

    /** units to produce */
    protected Map<Task,List<WargusUnitType>> taskGoals =
            new LinkedHashMap<Task,List<WargusUnitType>>();

    protected List<WargusUnitType> techGoals = new ArrayList<WargusUnitType>();

    protected static class CommandGoal {
        /** the group that the command is producing for */
        UnitGroup group;

        /** the command */
        UnitCommand cmd;

        /** the task that the command is producing for.  May be null for
         *  default tasks */
        Task      task;

        public CommandGoal() {}

        public CommandGoal(UnitGroup group) {
            assert group != null;
            this.group = group;
        }

        public CommandGoal(UnitGroup group, Task task) {
            assert group != null;
            assert task != null;
            this.group = group;
            this.task = task;
        }

        public UnitCommand getCommand() {
            return cmd;
        }

        public void setCommand(UnitCommand cmd) {
            this.cmd = cmd;
        }


    }
    
    /** keep track of units that have been assigned commands */
    protected Map<Unit,CommandGoal> activeUnits =
            new LinkedHashMap<Unit,CommandGoal>();

    // some intermediate production goals
    
    /** get the peasants harvesting */
    protected BuildState state1;
    
    /** get a barracks */
    protected BuildState state2;

    /** get a blacksmiths and a stable */
    protected BuildState state3;

    protected CommandTranslator tx = new CommandTranslator();

    public ProductionManager() {
        state1 = new BuildState();
        state1.increment(WargusUnitType.TOWN_HALL.getName(), 1);
        state1.increment(WargusUnitType.PEASANT.getName(), 3);

        state2 = (BuildState) state1.clone();
        state2.increment(WargusUnitType.HUMAN_BARRACKS.getName(), 1);

        state3 = (BuildState) state2.clone();
        state3.increment(WargusUnitType.BLACKSMITH.getName(), 1);
        state3.increment(WargusUnitType.STABLES.getName(), 1);
    }

    @Override
    public void beginEpisode(GameState state) {
        nextTasks.clear();
        activeTasks.clear();
        taskGoals.clear();
        activeUnits.clear();
    }
    
    @Override
    public void addTask(Task task) {
        nextTasks.add(task);
    }

    @Override
    public void terminateTask(Task task) throws TaskTerminationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void terminateTasks() {
        nextTasks.clear();
        activeTasks.clear();
        taskGoals.clear();
        activeUnits.clear();
    }


    @Override
    public Set<Task> getTasks() {
        Set<Task> ts = new LinkedHashSet<Task>(nextTasks);
        ts.addAll(activeTasks.keySet());
        return ts;
    }

    @Override
    public void nextActions(GameState state) {
        production_log.info("game cycle " + state.getCycle());
        
        BuildState buildstate = new BuildState();
        buildstate.countUnits(state, playerId);
              
        removeCompletedCommands(state);
        removeCompletedTasks(state);

        
        // what is this player's development state?
        Map<Integer,Unit> allies = state.getUnits(playerId);
        boolean peasantOnly = hasPeasantOnly(allies);
        boolean townhallOnly = hasTownhallOnly(allies);

        // set tech goals
        //
        Player player = state.getPlayer(playerId);
        assert player != null : "no player for id=" + playerId;
        if (peasantOnly) {
            techGoals.add(WargusUnitType.TOWN_HALL);
        } else if (townhallOnly) {
            techGoals.add(WargusUnitType.PEASANT);
        } /* else if (player.getSupply() - player.getDemand() < 5) {
            // it is hard to find a position for a farm that doesn't
            // block something important.  Need to do a spatial analysis to
            // position the farm.
            techGoals.add(WargusUnitType.FARM);
        } else if (!buildstate.meets(state2)) {
            techGoals.add(WargusUnitType.HUMAN_BARRACKS);
        } else if (!buildstate.meets(state3)) {
            techGoals.add(WargusUnitType.BLACKSMITH);
            techGoals.add(WargusUnitType.STABLES);
        }*/

        // remove tasks from nextTasks queue.
        Task t;
        while ((t = nextTasks.poll()) != null) {
            activateTask(t);
        }

        for (Task task : activeTasks.keySet()) {
            nextActions(state, task);
        }
    }

    /**
     * execute next actions for Task.
     */
    protected void nextActions(GameState state, Task task) {
            UnitGroup using = task.getUsingGroup();
            assert using != null : "null production group";
            if (using.isEmpty()) {
                // "no units to use for production in task " + task;
                return;
            }
            // if conditions met for goal, assign a unit to it.
            for (WargusUnitType type : taskGoals.get(task)) {
                production_log.info("condition met for " + type);
                Unit u = findProducer(using, type);
                if (u != null) {
                    production_log.info(u + " trains " + type);
                    trainUnit(u, state, task, type);
                }
            }

            // tech goals
            for (WargusUnitType goal : techGoals) {
               Unit u = findProducer(using, goal);
               if (u == null) {
                   continue;
               }
               if (UnitAnalysis.isBuilding(goal)) {
                  buildUnit(u, state, using, goal);
               } else if (UnitAnalysis.isPeasant(goal)) {
                  trainUnit(u, state, using, goal);
               } else {
                   throw new RuntimeException(goal + " not supported as tech goal.");
               }
            }
            
            // default tasks.
            Set<Unit> us = using.getUnits();
            for (Unit u : us) {
                if (activeUnits.containsKey(u) || Randomizer.nextFloat() > .3) {
                    continue;
                }
                if (WargusUnitType.PEASANT.getName().equals(u.getUnitTypeString())) {
                    harvestGold(u, state);
                } else if (defaultPeasants < 10 && WargusUnitType.TOWN_HALL.getName().equals(u.getUnitTypeString())) {
                    defaultPeasants++;
                    trainUnit(u, state, using, WargusUnitType.PEASANT);
                }
            }
        
        
    }

    /** train unit for task output group */
    protected void trainUnit(Unit trainer, GameState state, UnitGroup group, WargusUnitType type) {
        trainUnit(trainer, state, new CommandGoal(group), type);
    }

    /** train unit for task output group */
    protected void trainUnit(Unit trainer, GameState state, Task task, WargusUnitType type) {
        UnitGroup group = activeTasks.get(task);
        trainUnit(trainer, state, new CommandGoal(group, task), type);
    }

    protected void trainUnit(Unit trainer, GameState state, CommandGoal goal, WargusUnitType type) {
        assert !activeUnits.containsKey(trainer);
        UnitCommand cmd = tx.createComBuildingTrain(trainer.getUnitId(),
                             type.getName(),
                             trainer.getLocX(), trainer.getLocY());
        goal.setCommand(cmd);
        state.addCommand(cmd);
        activeUnits.put(trainer, goal);
        production_log.info("unit " + trainer.getUnitId() + " " +
                            trainer.getUnitTypeString() +
                            " trains " + type.getName() + " for task " + goal.task);
    }

    protected void buildUnit(Unit u, GameState state, UnitGroup group, WargusUnitType type) {
        buildUnit(u, state, new CommandGoal(group), type);
    }

    /** build unit for output group. */
    protected void buildUnit(Unit u, GameState state, Task task, WargusUnitType type) {
        UnitGroup group = activeTasks.get(task);
        buildUnit(u, state, new CommandGoal(group), type);
    }

    protected void buildUnit(Unit u, GameState state, CommandGoal goal, WargusUnitType type) {
        Region r = state.getMap().getRegion(u);
        Location loc = findBuildSite(type, state, r);
        if (loc == null) {
            log.warn("cannot find build site for " + type.getName());
            return;
        }
        UnitCommand cmd = tx.createComUnitBuild(u.getUnitId(),
                                                type.getName(),
                                                loc.getX(), loc.getY());
        goal.setCommand(cmd);
        state.addCommand(cmd);
        activeUnits.put(u, goal);
        production_log.info("unit " + u.getUnitId() + " builds " + type + " for " + goal.task);
    }

    protected void harvestGold(Unit u, GameState state) {
        Unit goldmine = getNearestGoldMine(state, u);
        if (goldmine != null) {
            UnitCommand cmd =
                    tx.createComUnitHarvestGold(u.getUnitId(),
                                                goldmine.getUnitId());
            CommandGoal goal = new CommandGoal();
            goal.setCommand(cmd);
            state.addCommand(cmd);
            activeUnits.put(u, goal);
            production_log.info("unit " + u.getUnitId() + " harvests gold.");
        }
    }


    private boolean hasPeasantOnly(Map<Integer, Unit> allies) {
        boolean hasPeasant = false;
        for (Unit u : allies.values()) {
            String type = u.getUnitTypeString();
            if ("unit-peasant".equals(type)) {
                hasPeasant = true;
            } else {
                return false;
            }
        }
        return hasPeasant;
    }

    private boolean hasTownhallOnly(Map<Integer, Unit> allies) {
        boolean hasTownhall = false;
        for (Unit u : allies.values()) {
            WargusUnitType type = WargusUnitType.getType(u.getUnitTypeString());
            if (type == WargusUnitType.TOWN_HALL) {
                hasTownhall = true;
            } else {
                return false;
            }
        }
        return hasTownhall;
    }

    private boolean isTaskDone(Task t, GameState state) {
        // a production task is done when the group it is supposed to build
        // exists and is complete.
        UnitGroup group = activeTasks.get(t);
        // does the group have the required units?
        // count actual units
        Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
        for (Unit u : group.getUnits()) {
            String type = u.getUnitTypeString();
            if (counts.containsKey(type)) {
                int i = counts.get(type);
                counts.put(type, i+1);
            } else {
                counts.put(type, 1);
            }
        }
        // compare to required units.
        UnitGroup spec = t.getTargetGroup();
        if (spec == null) {
            log.error("no output group specification for task " + t);
            throw new RuntimeException();
        }
        for (Argument req : spec.getUnitTypeReqs()) {
            if (!counts.containsKey(req.getName())) {
                return false;  // required units not in group.
            }
            int i = counts.get(req.getName());
            if (i < req.getValue()) {
                return false;  // required number of units not in group.
            }
        }

        return true;  // all required units in produced group found.
    }

    private void removeCompletedCommands(GameState state) {
        /*
        if (log.isDebugEnabled()) {
            final String NL = System.getProperty("line.separator");
            StringBuffer msg = new StringBuffer("Active units:"+NL);
            for (Unit u : activeUnits.keySet()) {
                msg.append("unit " + u.getUnitId() + " hashCode " + u.hashCode());
                msg.append(" doing ");
                Task task = activeUnits.get(u);
                msg.append(task);
                if (!activeTasks.containsKey(task)) {
                    msg.append(" but task no longer active.");
                } else {
                    UnitGroup group = activeTasks.get(task);
                    msg.append(" producing " + group);
                }
                msg.append(NL);
            }
            log.debug(msg.toString());
        } */

        // check to see if unit action changed (because of error executing action?)
        Iterator<Map.Entry<Unit,CommandGoal>> itr = activeUnits.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<Unit,CommandGoal> entry = itr.next();
            Unit u = entry.getKey();
            ActionState status = u.getActionState();
            //
            // if a peasant is standing but is supposed to be building, then
            // it is probably blocked, so we remove this command.
            if (WargusUnitType.PEASANT.getCode() == u.getType() &&
                !status.isDying() &&
                status.getStatus() == UnitStatus.STAND) {
                UnitCommand cmd = entry.getValue().getCommand();
                if (cmd.getType() == CommandType.UNIT_BUILD) {
                    // peasant is standing when supposed to be building.  Remove command.
                    itr.remove();
                }
            } else if (!status.isDying() &&
                !status.isBuildingOrTraining() &&
                !status.isHarvesting()) {

                // TODO: barracks have status STAND when I think they are training.
                // where is the bug?
                if (UnitAnalysis.isBuilding(u)) {
                    continue;
                }
                UnitCommand cmd = entry.getValue().getCommand();
                String msg = "unit " + u.getUnitId() + " " + u.getUnitTypeString() +
                        " should be ";
                if (cmd != null) {
                    msg += cmd.getType();
                } else {
                    msg += " should be building, training, or harvesting, ";
                }
                msg += " but is " + status.getStatus();
                log.warn(msg);
            }

        }

        // match completion event to action (command).
        Set<UnitEvent> events = state.getEvents();
        for (UnitEvent event : events) {
            // assumes unit only assigned one action.
            Unit u = state.getUnit(event.getUnitId());
            assert u != null;
            CommandGoal goal = activeUnits.get(u);
            if (goal == null) {
                continue; // unit completed some default action.
            }
            if (event.getType() == UnitEventType.BUILT ||
                event.getType() == UnitEventType.TRAINED) {
                Unit produced = state.getUnit(event.getArg());

                UnitGroup group = goal.group;
                group.addUnit(produced);

                WargusUnitType type = WargusUnitType.getType(produced.getUnitTypeString());
                if (goal.task != null) {
                    // remove produced unit from task goals.
                    List<WargusUnitType> g = taskGoals.get(goal.task);                 
                    g.remove(type);
                } else {
                    techGoals.remove(type);
                }
            } else if (event.getType() == UnitEventType.ERROR_BUILD) {
                if (event.getArg() == 0) {
                    log.error("unit " + event.getUnitId() + " unable to build because unable to reach site.");
                } else {
                    log.error("unit " + event.getUnitId() + " unable to build because can't build on site.");
                }
            } else if (event.getType() == UnitEventType.ERROR_TRAIN) {
                 log.error("unit " + event.getUnitId() + " unable to train.");
            }
            activeUnits.remove(u);
        }
    }

    private Unit getNearestGoldMine(GameState state, Unit ally) {
        Collection<Unit> us = state.getNeutralUnits().values();
        double min = Double.MAX_VALUE;
        Unit closest = null;
        for (Unit u : us) {
            String type = u.getUnitTypeString();
            if (WargusUnitType.GOLD_MINE.getName().equals(type)) {
                double d = Point.distanceSq(ally.getLocX(), ally.getLocY(), u.getLocX(), u.getLocY());
                if (d < min) {
                    min = d;
                    closest = u;
                }
            }
        }
        return closest;
    }

    private void removeCompletedTasks(GameState state) {
        // check for task completion.
        Iterator<Task> itr = activeTasks.keySet().iterator();
        while(itr.hasNext()) {
            Task t = itr.next();
            if (isTaskDone(t, state)) {
                log.debug("completed " + t);
                parent.notifyComplete(t);
                itr.remove();  // remove task from active tasks

                // stop all units that were working on task.
                Iterator<Map.Entry<Unit,CommandGoal>> unitItr =
                        activeUnits.entrySet().iterator();
                while (unitItr.hasNext()) {
                    Map.Entry<Unit,CommandGoal> entry = unitItr.next();
                    if (entry.getValue().task == t) {
                        // this unit was doing a command for this task.
                        // Stop it!
                        Unit u = entry.getKey();
                        if (!u.isDying() && !u.isDead()) {
                            UnitCommand cmd = tx.createComUnitStop(u.getUnitId());
                            state.addCommand(cmd);
                            log.debug("unit " + u.getUnitId() + " stops.");
                        }
                        unitItr.remove();
                    }
                }
            }
        }
    }

    private void activateTask(Task t) {
            UnitGroup outSpec = t.getTargetGroup();
            UnitGroup group = t.getPlan().getGroup(outSpec.getId());
            assert group != null : "no group in strategy for group-spec " + t.getTargetGroup().getId();
            activeTasks.put(t, group);
            List<WargusUnitType> g = new ArrayList<WargusUnitType>();
            for (Argument arg : outSpec.getUnitTypeReqs()) {
                WargusUnitType type = WargusUnitType.getType(arg.getName());
                assert type != null : "no type found for " + arg.getName();
                int n = arg.getValue();
                for (int i = 0; i < n; i++) {
                    g.add(type);
                }
            }
            // randomize production order.
            Randomizer.shuffle(g);
            taskGoals.put(t, g);
            production_log.info("active task " + t + " producing " + group);
    }

    /**
     * find producer in task's group that can produce a unit of the given type.
     * @param wargusUnitType
     * @return
     */
    private Unit findProducer(UnitGroup using, WargusUnitType type) {
        assert type != null : "type must be specified";
        ProductionReq req = reqs.getRequirements(type.getName());
        if (req == null) {
            throw new RuntimeException("no requirements specified for " + type);
        }
        String producerType = req.getProducer();

        for (Unit u : using.getUnits()) {
            if (!u.isDying() &&
                !activeUnits.containsKey(u) &&
                u.getUnitTypeString().equals(producerType)) {
                return u;
            }
        }
        return null;
    }

    /**
     * Find open site for a building of given type in Region.
     *
     * @param type
     * @param r
     * @return
     */
    private Location findBuildSite(WargusUnitType type, GameState state, Region r) {
        Rectangle site = new Rectangle();
        // TODO: this information could be in the Unit class.
        switch (type) {
            case FOOTMAN:
            case PEASANT:
            case KNIGHT:
            case ARCHER:
                site.setBounds(0, 0, 0, 0); // 1x1
                break;
            case GOLD_MINE:
                site.setBounds(0, 0, 2, 2); // 3x3
                break;
            case FARM:
                site.setBounds(0, 0, 1, 1); // 2x2
                break;
            case HUMAN_BARRACKS:
                site.setBounds(0, 0, 2, 2); // 3x3
                break;
            case STABLES:
                site.setBounds(0, 0, 2, 2); // 3x3
                break;
            case TOWN_HALL:
                site.setBounds(0, 0, 2, 2); // 4x4
                break;
            case LUMBER_MILL:
                site.setBounds(0, 0, 2, 2); // 3x3
                break;
            case BLACKSMITH:
                site.setBounds(0, 0, 2, 2); // 3x3
                break;
            default:
                throw new RuntimeException("unknown site size for " + type.getName());
        }
        // scan for free site.
        for (Rectangle rec : r.getRectangles()) {
            site.moveTo(rec.getMinX(), rec.getMinY());
            char[][] overlay = getRegionOverlay(state, r, rec);
            while (site.getMaxX() <= rec.getMaxX()) {
                while (site.getMaxY() <= rec.getMaxY()) {
                    if (siteClear(overlay, rec, site)) {
                      Location loc = new Location(site.getMinX(), site.getMinY());
                      return loc;
                    }
                    site.displace(0, 1);  // move one tile down.
                }
                site.moveTo(site.getMinX()+1,rec.getMinY());
            }
        }
        return null;
    }

    /** copy region grid and overlay tile markers for units in region */
    private char[][] getRegionOverlay(GameState state, Region region, Rectangle rec) {
        GameMap map = state.getMap();
        char[][] overlay = new char[map.getExtentY()][map.getExtentX()];
        for (int x = rec.getMinX(); x <= rec.getMaxX(); x++) {
            for (int y = rec.getMinY(); y <= rec.getMaxY(); y++) {
                overlay[y-rec.getMinY()][x-rec.getMinX()] = map.getCell(x, y);
            }
        }
        // overlay units in region.
        for (Unit u : state.getUnits(region)) {
            WargusUnitType type = WargusUnitType.getType(u.getUnitTypeString());
            if (type == null) {
                // probably type for image of dying unit.
                continue;
            }
            int extentX;
            int extentY;
            switch (type) {
                case FOOTMAN:
                case PEASANT:
                case KNIGHT:
                case ARCHER:
                    extentX = extentY = 1;
                    break;
                case GOLD_MINE:
                    extentX = extentY = 3;
                    break;
                case FARM:
                    extentX = extentY = 2;
                    break;
                case HUMAN_BARRACKS:
                    extentX = extentY = 3; // 3x3
                    break;
                case STABLES:
                    extentX = extentY = 3; // 3x3
                    break;
                case TOWN_HALL:
                    extentX = extentY = 4; // 4x4
                    break;
                case LUMBER_MILL:
                    extentX = extentY = 3; // 3x3
                    break;
                case BLACKSMITH:
                    extentX = extentY = 3; // 3x3
                    break;
                default:
                    throw new RuntimeException("unknown site size for " + type.getName());
            }
            for (int x = u.getLocX(); x < u.getLocX() + extentX; x++) {
                for (int y = u.getLocY(); y < u.getLocY() + extentY; y++) {
                    if (y-rec.getMinY() < 0 || x-rec.getMinX() < 0) {
                        log.error("invalid locations while calculating map overlay. unit " + u + " rectagle " + rec);
                        break;
                    }
                    overlay[y-rec.getMinY()][x-rec.getMinX()] = 'X';
                }
            }
        }
        return overlay;
    }

    private boolean siteClear(char[][] overlay, Rectangle submap, Rectangle site) {
        // site is in map coordinates.  overlay is a rectange of a region.
        for (int x = site.getMinX()-submap.getMinX(); x <= site.getMaxX(); x++) {
            for (int y = site.getMinY()-submap.getMinY(); y <= site.getMaxY(); y++) {
                if (overlay[y][x] != TileType.OTHER) {
                    return false;
                }
            }
        }
        /*
        String msg = "site clear at [" + site.getMinX() + "," + site.getMinY() + "]\n";
        for (int y = 0; y < overlay.length; y++) {
            for (int x = 0; x < overlay[y].length; x++) {
                msg += overlay[y][x];
            }
            msg += "\n";
        }
        log.debug(msg);
         */
        return true;
    }
}
