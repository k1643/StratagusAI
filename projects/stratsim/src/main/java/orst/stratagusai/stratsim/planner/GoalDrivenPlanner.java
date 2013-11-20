package orst.stratagusai.stratsim.planner;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import orst.stratagusai.TileType;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratplan.GroupType;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;
import orst.stratagusai.stratsim.model.BuildRequirements;

/**
 * Planner
 *
 */
public class GoalDrivenPlanner extends BasePlanner {
    private static final Logger log = Logger.getLogger(GoalDrivenPlanner.class);

    /** a task that produces or uses a group.  When the task is done,
     *  the group can be used in the next task.
     */
    protected static class GroupSource {
        Task task;
        UnitGroup group;

        public GroupSource(Task task, UnitGroup group) {
            this.task = task;
            this.group = group;
        }

        @Override
        public String toString() {
            return "[GroupSource " + task + " " + group + "]";
        }
    }

    
    protected int playerId = -1;
    protected int nextTaskId;

    protected StrategyTemplate template;

    protected List<StrategicGoal> goals = new ArrayList<StrategicGoal>();

    /** initial locations of groups */
    protected Map<UnitGroup,Region> initialRegions;

    protected Assignments assignments = new Assignments();

    protected ProductionEstimation est = ProductionEstimation.getEstimator();

    /** opponent name for logging only! */
    protected String opponent;

    public GoalDrivenPlanner() {
    }

    @Override
    public void configure(Map params) {
        opponent = (String) params.get("opponent");
        String name = (String) params.get("strategy");
        List<Number> strategy_paramStr = (List<Number>) params.get("strategy_params");
        if (name != null) {
            template = StrategyTemplate.getNamedTemplate(name);
        } else if (strategy_paramStr != null) {
            float[] strategy_params = new float[9];
            assert strategy_paramStr.size() == 9;
            for (int i = 0; i < 9; i++) {
                strategy_params[i] = strategy_paramStr.get(i).floatValue();
            }
            template = StrategyTemplate.getStrategy(strategy_params);
        } else {
            throw new RuntimeException("no strategy given in configuration.");
        }
    }

    public void configure(String name) {
        template = StrategyTemplate.getNamedTemplate(name);
    }

    public StrategyTemplate getTemplate() {
        return template;
    }

    public void setTemplate(StrategyTemplate template) {
        this.template = template;
    }

    private int getNextTaskId() {
        return ++nextTaskId;
    }

    public void init(int playerId) {
        this.playerId = playerId;
        this.nextTaskId = 0;
        goals.clear();
        assignments.clear();
    }

    protected void setInitialRegions(Map<UnitGroup,Region> initialRegions) {
        this.initialRegions = initialRegions;
    }

    public StrategicPlan makePlan(int playerId, StrategicState state) {
        log.debug("Make plan for player " + playerId + " using strategy " + template.getName());
        return makePlan(playerId,null, state);
    }

    public StrategicPlan replan(int playerId, StrategicPlan prevPlan, StrategicState state) {
        log.debug("Re-plan for player " + playerId);
        return makePlan(playerId,prevPlan, state);
    }

    private StrategicPlan makePlan(int playerId, StrategicPlan prevPlan, StrategicState state) {
        init(playerId);
        initialRegions = state.getInitialRegions();
        assert initialRegions != null;
        StrategicPlan plan = initialPlan(state); // initialize groups
        makeGoals(state);
        secureMap(plan, prevPlan, state);
        verifyPlan(plan);
        logTasks(plan);
        logStats();

        return plan;
    }

    /**
     * initialize and merge groups
     */
    protected StrategicPlan initialPlan(StrategicState state) {
        // UnitGroups were created by GroupAnalysis or are from previous plan.
        assert template != null : "strategy template not set.";

        StrategicPlan plan = new StrategicPlan(playerId, template.getName() + "_" + playerId);

        Set<UnitGroup> allyGroups = state.getGroups(playerId);
        for (UnitGroup g : allyGroups) {
            assert !g.isEmpty() : "cannot plan with empty group: " + g;
            assert state.getInitialRegion(g) != null : "no initial region for " + g;
        }
        
        Set<UnitGroup> prodGroups = new LinkedHashSet<UnitGroup>();
        Set<UnitGroup> combatGroups = new LinkedHashSet<UnitGroup>();
        for (UnitGroup g : allyGroups) {
            if (g.isProduction()) {
                prodGroups.add(g);
            } else if (g.isCombat()) {
                combatGroups.add(g);
            } else {
                log.error("group is not production or combat type. " + g);
                throw new RuntimeException("group is not production or combat type. " + g);
            }
        }
        // initialize production groups.
        for (UnitGroup g : prodGroups) {
            plan.addGroup(g);
            initGroupTask(plan, g);
        }
   
        // if combat group does not meet unit type requirements
        //   if combat group is near base then
        //     create a production task to complete the group.
        Set<Region> bases = state.getBases(playerId);
        Set<UnitGroup> to_init = new LinkedHashSet();
        for (UnitGroup group : combatGroups) {
            assert !(group.isEmpty()) : "Empty UnitGroup " + group;        
            if (!meetsUnitTypeReqs(group)) {
                Region r = state.getInitialRegion(group);
                if (bases.contains(r)) {
                    // add production task to finish group.
                    Task t = completeGroup(plan, group, r);
                    if (t == null) {
                        to_init.add(group);  // can't find production group to add units, so just initialize it.
                    } else {
                     //   plan.addGroup(group);
                    }
                } else {
                    to_init.add(group);
                }
            } else {
                to_init.add(group);
            }
        }

        // Groups that are not the output of a production task
        // are simply initialized.
        for (UnitGroup group : to_init) {
            plan.addGroup(group);
            initGroupTask(plan, group);
        }
        if (log.isDebugEnabled()) {
            Set<UnitGroup> gs = plan.getGroups();
            StringBuffer msg = new StringBuffer();
            if (gs.isEmpty()) {
                msg.append("No initial groups.");
                msg.append(NL);
            } else {
                msg.append("Inital Groups:");
                msg.append(NL);
                for (UnitGroup g : gs) {
                    msg.append(g.toString());
                    msg.append(" IDs: ");
                    for (Unit u : g.getUnits()) {
                        msg.append(u.getUnitId());
                        msg.append(" ");
                    }
                    msg.append(NL);
                }
            }
            log.debug(msg);
        }

        // verify that initial groups have locations.
        for (UnitGroup g : plan.getGroups()) {
            assert state.getInitialRegion(g) != null : "no initial region for " + g;
        }

        return plan;
    }

    /**
     * create goals for enemy bases, allied bases, and choke points.
     */
    public List<StrategicGoal> makeGoals(final StrategicState state) {
        assert template != null : "strategy template not set.";
        int opponentId = (playerId == 0) ? 1 : 0; // assuming two players.
        for (Region r : state.getBases(opponentId)) {
            StrategicGoal g = template.getEnemyBaseGoal(r);
            if (g.getPriority() > 0) {
                goals.add(g);
            }
        }
        for (Region r : state.getBases(playerId)) {
            StrategicGoal g = template.getAlliedBaseGoal(r);
            if (g.getPriority() > 0) {
                goals.add(g);
            }
        }
        
        for (Region r : state.getChokepoints()) {
            StrategicGoal g = template.getChokepointGoal(r);
            if (g.getPriority() > 0) {
                goals.add(g);
            }
        }

        if (goals.isEmpty()) {
            // no bases or chokepoints.  Secure regions with any opponent units.
            for (Region r :  state.getOccupied(opponentId)) {
                StrategicGoal g = template.getRegionGoal(r);
                goals.add(g);
            }
             assert !goals.isEmpty() : "No opponents for player " + playerId + ".  Game should be over.";
        } else {
             // Sort StrategicGoals by assignment priority.
             // Priority High 3-2-1 Low.  0 means ignore.
            Comparator cmp = new GoalComparator(playerId, state);
            Collections.sort(goals, cmp); // sort ascending
        }
        assert !goals.isEmpty() : "No goals on map for player " + playerId + ".  Game should be over.";
        
        if (log.isDebugEnabled()) {
            StringBuffer msg = new StringBuffer("Goals: ");
            for (StrategicGoal g : goals) {
                msg.append(g.toString());
                msg.append(" ");
            }
            log.debug(msg);
        }
        return goals;
    }

    private void secureMap(StrategicPlan plan, StrategicPlan prevPlan, StrategicState s) {

        // TODO: could replace this if combat UnitGroups held the regions they were assigned to.
        //Map<UnitGroup,Set<Region>> prevAssigns = null;
        //if (prevPlan != null) {
        //    prevAssigns = AssignmentExtractor.getCombatAssignments(s.getMap(), prevPlan);
        //}

        // first round of assignments.       
        for (StrategicGoal goal : goals) {
            Set<GroupSource> combatSrcs = getCombatSources(plan);
            GroupSource producer = assignGroup(s, plan,
                                               combatSrcs,
                                               goal);
            if (producer != null) {
                addCombatTask(plan, goal, producer.task, "secure", producer.group);
            } else {
                UnitGroup combat = defineGroup(plan, goal, s.getMap());
                Task production = produceGroup(plan, s.getMap(), combat, goal.getRegion());
                if (production == null) {
                    // no production group found.  this can happen if bases have
                    // been destroyed.
                    log.debug("no production task for combat group.");
                } else {
                    addCombatTask(plan, goal, production, "secure", combat);
                    // add to StrategicState, set initial region.
                    // combat group will be produced in the region of its production group.
                    s.addGroup(combat, initialRegions.get(production.getUsingGroup()));
                }
            }
        }

        if (template.isMassAttack()) {
            massAttack(plan); // patch plan to make mass attacks on enemy bases.
        }

        // if any combat groups are not assigned, we can assign them
        // to high priority goals
        Set<GroupSource> combatSrcs = getCombatSources(plan);
        for (GroupSource src : combatSrcs) {
            StrategicGoal goal = assignGoal(src);
            if (goal != null) {
                addCombatTask(plan, goal, src.task, "secure", src.group);
            }
        }

        // if any leaf tasks initialize production groups, we
        // can use these to produce new combat groups.
        for (Task task : plan.getLeafTasks()) {
            if ("init-group".equals(task.getType())) {
                assert task.isLeaf();
                UnitGroup g = task.getTargetGroup();
                if (g.isCombat()) {
                    StrategicGoal goal = assignGoal(new GroupSource(task, g));
                    if (goal != null) {
                        addCombatTask(plan, goal, task, "secure", g);
                    }
                } else if (g.isProduction()) {
                    // define combat group, production task, and combat task
                    StrategicGoal goal = assignGoal(new GroupSource(task, g));
                    UnitGroup combatGroup = defineGroup(plan, goal, s.getMap());
                    Task produce = new Task();
                    produce.setName("produce" + getNextTaskId());
                    produce.setType("produce");
                    produce.setComment("produce new group");
                    produce.setTargetGroup(combatGroup);
                    produce.setUsing(g.getId());
                    
                    addTrigger(plan, task, produce);
                    plan.addGroup(combatGroup);
                    // add to StrategicState, set initial location.
                    // combat group will be produced in region of production group.
                    s.addGroup(combatGroup, initialRegions.get(g));
                    
                    produce.setDuration(getEstimatedDuration(produce));
                    produce.setEstimatedCompletionTime(estimatedCompletionTime(produce));
                    if (goal != null) {
                        addCombatTask(plan, goal, produce, "secure", combatGroup);
                    }
                }
            }
        }
    }

    /**
     * Get sources of combat groups in given plan.  Sources are tasks
     * that initialize, produce, or free combat groups that are not
     * used by successors.
     * 
     * @param plan
     * @return
     */
    public Set<GroupSource> getCombatSources(StrategicPlan plan) {
        Set<GroupSource> sources = new LinkedHashSet<GroupSource>();
        for (Task t : plan.getTasks()) {
            UnitGroup combatGroup = null;
            if ("attack".equals(t.getType()) && t.getSuccessors().isEmpty()) {
                combatGroup = t.getUsingGroup();
            } else if (t.getTargetGroup() != null && t.getTargetGroup().isCombat()) {
                // if combat group is not used by a successor, then it is available
                UnitGroup g = t.getTargetGroup();
                boolean used = false;
                for (Task successor : t.getSuccessors()) {
                    if (successor.getUsingGroup() == g) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    combatGroup = g;
                }
            }

            if (combatGroup != null) {
                sources.add(new GroupSource(t, combatGroup));
            }
        }
        return sources;
    }

    /**
     * Get sources of production groups in given plan.  Sources are tasks
     * that initialize or free production groups that aren't used by later tasks.
     * @param plan
     * @return
     */
    public Set<Task> getProductionSources(StrategicPlan plan) {
        Set<Task> sources = new LinkedHashSet<Task>();
        for (Task t : plan.getTasks()) {
            UnitGroup using = t.getUsingGroup();
            if (t.isLeaf() && t.getTargetGroup() != null && t.getTargetGroup().isProduction()) {
                sources.add(t);
            } else if (using != null && using.isProduction()) {
                boolean usesProduction = false;
                for (Task successor : t.getSuccessors()) {
                    if (successor.getUsingGroup() == using) {
                        usesProduction = true;
                    }
                }
                if (!usesProduction) {
                    sources.add(t);
                }
            }
        }
        return sources;
    }
 
    /**
     * Assign suitable allied group to goal.
     */
    public GroupSource assignGroup(StrategicState s,
                                 StrategicPlan plan,
                                 Set<GroupSource> combatSources,
                                 StrategicGoal goal) {
        // assign most appropriate group to goal.
        GroupSource producer = null;
        double value = Double.NEGATIVE_INFINITY;
        for (GroupSource src : combatSources) {
            Region location;
            UnitGroup ally = src.group;
            if ("init-group".equals(src.task.getType())) {
                location = s.getInitialRegion(ally);
            } else if ("attack".equals(src.task.getType())) {
                // attack with ally -> attack ?         
                location = s.getMap().getRegion(src.task.getTargetRegionId());
            } else if ("produce".equals(src.task.getType())) {
                UnitGroup production = src.task.getUsingGroup();
                location = s.getInitialRegion(production);
            } else {
                throw new RuntimeException("unknown task type " + src.task.getType());
            }
            if (location == null) {
                throw new RuntimeException("unknown location for " + src);
            }
            Set<Region> prevTargets = null;
            //if (prevAssigns != null) {
            //    prevTargets = prevAssigns.get(ally);
            //}

            // high value is more compatible
            double v = getCompatibility(s, src, location, prevTargets, goal);
            if (v > value) {
                producer = src;
                value = v;
            }
        }
        if (producer != null) {           
            log.debug("assign group " + producer.group.getId() + " produced by " + producer.task + " to goal " + goal);
        }
        return producer;
    }

    /**
     * Higher value is more compatible.
     */
    private double getCompatibility(StrategicState state, GroupSource src, Region location, Set<Region> prevTargetRegions, StrategicGoal goal) {
        Region targetRegion = goal.getRegion();
        UnitGroup ally = src.group;

        assert ally.isCombat();
        // get weighted, normalized compatibility factors.
        GameMap map = state.getMap();
        double t = getTimeFactor(template.getTimeToTarget(), src, map, location, targetRegion);
        double s = getEnemyDamageFactor(template.getEnemyDamage(), state, targetRegion);
        double r = getDamageRatio(template.getDamageRatio(), state, targetRegion, ally);
        double a = 0; // getInertiaFactor(template.getGoalInertia(), targetRegion, prevTargetRegions);

        // high value is more compatible
        return t + s + r + a;
    }

    /** convert plan into a mass attack plan */
    public void massAttack(StrategicPlan plan) {
        // get all enemy base goals
        List<StrategicGoal> enemyBaseGoals = new ArrayList<StrategicGoal>();
        for (StrategicGoal goal : goals) {
            if (goal.getType() == GoalType.SECURE_ENEMY_BASE) {
                enemyBaseGoals.add(goal);
            }
        }
        // get all secure enemy base tasks.
        List<Task> enemyBaseTasks = new ArrayList<Task>();
        for (Task t : plan.getTasks()) {
            for (StrategicGoal goal : enemyBaseGoals) {
                if (t.getTargetRegionId() == goal.getRegion().getId()) {
                    enemyBaseTasks.add(t);
                }
            }
        }

        // change secure enemy base tasks into "attack" enemy base task,
        // and create sequence of attack tasks for all enemy bases.
        for (Task t : enemyBaseTasks) {
            UnitGroup combat = t.getUsingGroup();
            Task curr = t;
            for (int i = 0; i < enemyBaseGoals.size(); i++) {
                StrategicGoal goal = enemyBaseGoals.get(i);
                int rid = goal.getRegion().getId();
                if (i == 0) {
                    curr.setType("attack");
                    curr.setTargetRegionId(rid);
                    curr.setDuration(getEstimatedDuration(curr));
                    curr.setEstimatedCompletionTime(estimatedCompletionTime(curr));
                } else {
                    Task next = new Task();
                    next.setComment("Attack enemy base in region " + rid);
                    next.setName("attack" + getNextTaskId());
                    next.setType("attack");
                    next.setTargetRegionId(rid);

                    next.setUsing(combat.getId());
                    addTrigger(plan, curr, next);

                    next.setDuration(getEstimatedDuration(next));
                    next.setEstimatedCompletionTime(estimatedCompletionTime(next));

                    curr = next;
                }
            }
        }
    }

    private double getTimeFactor(float w, GroupSource src, GameMap map, Region location, Region targetRegion) {
        if (w == 0) {
            return 0;
        }
        // normalize time to target to [0,1].
        // Zero time to target returns 1. Parameter set so that
        // 90% is achieved at about 30,000 cycles.  Time after the nexe
        // 30,000 cycles is not so important.
        double t = estimatedCompletionTime(src.task) + getTimeToTarget(map, src.group, location, targetRegion);
        t = 1 - 2/(1 + Math.exp(-t/10000));

        return w*t;
    }

    private int getTimeToTarget(GameMap map, UnitGroup group, Region location, Region targetRegion) {
        // tiles/cycle = Speed/5 * 1 tile/32 subtiles
        float dist = map.getDistance(location, targetRegion);
        if (dist == 0) {
            return 0;
        } else {
            int speed = Integer.MAX_VALUE;
            for (Unit u : group.getUnits()) {
                speed = Math.min(speed, u.getMaxSpeed());
            }
            if (speed == 0) {
                return Integer.MAX_VALUE;
            }
            return Math.round(dist * (5/speed) * 32);
        }
    }

    public List<UnitGroup> getAlliedCombatGroups(StrategicPlan plan) {
        List<UnitGroup> allies = new ArrayList<UnitGroup>(plan.getGroups());
        Iterator<UnitGroup> itr = allies.iterator();
        while (itr.hasNext()) {
            UnitGroup g = itr.next();
            if (!g.isCombat()) {
                itr.remove();
            }
        }
        return allies;
    }

    private double getProximityFactor(float w, GameMap map, Region r1, Region r2) {
        if (w == 0) {
            return 0;
        }
        if (r1 == null) {
            return 0;
        }
        final int MAX_DIST = 5 * Math.max(map.getExtentX(), map.getExtentY());
        List<Passage> path = map.getShortestPath(r1, r2);
        int dist = 0;
        for (Passage passage : path) {
            dist += passage.getExtent();
        }
        if (dist > MAX_DIST) {
            log.warn("distance " + dist + " greater than five times map extent.");
            return 0; // max distance
        } else {
            return w*(1 - dist/(float)MAX_DIST); // distance 0 gives proximity 1.
        }
    }

    /**
     * Amount of damage enemy in region can cause.  High value gives
     * high ally-target compatibility.
     *
     * w*[0,1] where 0 is maximum damage, w*1 is minimal damage.
     * 
     * @param w weight
     */
    private double getEnemyDamageFactor(float w, StrategicState ss, Region r) {
        if (w == 0) {
            return 0;
        }
        GameState s = ss.getGameState();
        Set<Unit> units = s.getEnemyUnits(playerId, r);
        final float MAX_DMG = 20 * (6 + 3); // Use damage from 20 footmen as max.
        int dmg = 0;
        for (Unit u : units) {
            dmg += u.getBasicDamage();
            dmg += u.getPiercingDamage();
        }
        if (dmg > MAX_DMG) {
            return 0; // max strength
        }
        return w*(1-dmg/MAX_DMG);
    }

    /**
     *   1/2 [(ally-enemy)/total + 1]  scale to [0,1].
     *
     *    High value gives high ally-target compatibility.
     */
    private double getDamageRatio(float w, StrategicState s, Region r, UnitGroup ally) {
        if (w == 0) {
            return 0;
        }
        int allyDamage = getTotalDamage(ally);
        if (allyDamage == 0) {
            return 0;  // new groups will have zero hitpoints
                       // TODO: count specified units if group is at production base?
        }
        int enemyDamage = 0;
        int opponentId = playerId == 0 ? 1 : 0;
        Set<UnitGroup> gs = s.getGroups(opponentId, r);
        for (UnitGroup g : gs) {
            enemyDamage += getTotalDamage(g);
        }
        return w * (.5 * ((allyDamage - enemyDamage) / (enemyDamage + allyDamage) + 1));
    }

    private int getTotalDamage(UnitGroup g) {
        return g.getRepresentative().getBasicDamage() + g.getRepresentative().getPiercingDamage();
    }
    
    private double getInertiaFactor(float w, Region targetRegion, Set<Region> prevTargetRegions) {
        if (w == 0) {
            return 0;
        }
        int a = prevTargetRegions == null ? 0 :
                        (prevTargetRegions.contains(targetRegion) ? 1 : 0);

        return w*a;
    }


    /** return highest priority goal compatible with group */
    private StrategicGoal assignGoal(GroupSource src) {
        // Group can be combat or production group
        // don't assign more than one group to a chokepoint.
        for (StrategicGoal goal : goals) {
            if (!goal.hasChokepoint() || !assignments.isAssigned(goal)) {
                return goal;
            }
        }
        return null;
    }

    private void initGroupTask(StrategicPlan plan, UnitGroup group) {
        Task task = new Task();
        task.setName("init-group" + group.getId());
        task.setType("init-group");
        task.setTargetGroup(group);

        Task root = plan.getStart();
        plan.addTrigger(root, task);
    }

    /**
     *
     */
    /*
    private Task addCombatTask(StrategicPlan plan, GameMap map, StrategicGoal goal, String combatType, UnitGroup combatGroup) {
        // don't have a production task for combat group yet.  Get production
        // group.
        Task producer = produceGroup(plan, map, combatGroup, goal.getRegion());
        if (producer == null) {
            // no production group found.  this can happen if bases have
            // been destroyed.
            log.debug("no production task for combat group.");
            return null;
        }
        //  return combat task.
        return addCombatTask(plan, goal, producer, combatType, combatGroup);
    } */

    /**
     * create task for goal.
     * 
     * @param plan
     * @param goal
     */
    private Task addCombatTask(StrategicPlan plan, StrategicGoal goal, Task producer, String combatType, UnitGroup combatGroup) {
        assert combatGroup != null : "null group.";
        assert combatGroup.isCombat();
        assert producer != null;
        
        int rid = goal.getRegion().getId();
        Task combatTask = new Task();
        String comment = null;
        if (goal.isEnemyBase()) {
            comment = "Control enemy base in region " + rid;
        } else if (goal.isAlliedBase()) {
            comment = "Control allied base in region " + rid;
        } else if (goal.hasChokepoint()) {
            comment = "Control chokepoint in region " + rid;
        }
        combatTask.setComment(comment);
        combatTask.setName(combatType + getNextTaskId());
        combatTask.setType(combatType);
        combatTask.setTargetRegionId(rid);

        combatTask.setUsing(combatGroup.getId());
        if (plan.getGroup(combatGroup.getId()) == null) {
            plan.addGroup(combatGroup);
        }
        addTrigger(plan, producer, combatTask);
        if ("attack".equals(combatType)) {
            combatTask.setDuration(getEstimatedDuration(combatTask));
            combatTask.setEstimatedCompletionTime(estimatedCompletionTime(combatTask));
        }

        assignments.addAssignment(combatGroup, goal);
        
        return combatTask;
    }

    /**
     * Define a group to fulfill a goal.
     * @param goal
     * @return
     */
    private UnitGroup defineGroup(StrategicPlan plan, StrategicGoal goal, GameMap map) {
        // TODO: next group ID from plan or PlayerGroups?
        UnitGroup group = new UnitGroup(plan.getNextGroupId(), playerId);
        group.setType(GroupType.GROUP_COMBAT);  // StrategicGoals are combat goals.
        //group.setGoal(goal);

        // Group Sizes used by Wargus AI script:
        // 5: 3 footmen, 2 archers
	// 5: 3 footmen, 1 archers, 1 catapult
	// 7: 1 footmen, 2 archers, 1 catapult 2, cavalry 1 mage
	// 15: 1 footmen, 2 archers, 1 catapult 6, cavalry 5 mages
        int n = template.getForce(goal.getType());
        if (goal.getType() == GoalType.SECURE_CHOKEPOINT) {
            // limit size of chokepoint force to prevent blocking allies from passing.
            int spanmax = getTraversableSpan(map, goal.getRegion()) - 2;
            if (spanmax < n && spanmax > 2) {
                n = spanmax;
            }
        }
        assert n > 0 : "cannot specify combat group with no required units.";
        if (n >= 5) {
            group.addUnitTypeReq(new Argument(WargusUnitType.ARCHER.getName(),2));
            group.addUnitTypeReq(new Argument(WargusUnitType.FOOTMAN.getName(), n-2));
        } else {
            final String unitType = WargusUnitType.FOOTMAN.getName();
            group.addUnitTypeReq(new Argument(unitType, n));
        }

        return group;
    }

    private Task completeGroup(StrategicPlan plan, UnitGroup group, Region region) {
        // find a production group in the group's region that can add remaining
        // required units
        GroupSource source = findProductionGroupToCompleteGroup(plan, region, group);
        if (source == null) {
            return null;
        }
        if (source.task.getSuccessors().size() >= 2) {
            // task already has a production successor
            log.debug("Already a production successor for production task" + source.task);
            return null;
        }
        UnitGroup using = source.group;

        Task produce = new Task();
        produce.setName("produce" + getNextTaskId());
        produce.setType("produce");
        produce.setComment("add remaining required units");
        produce.setTargetGroup(group);  // group to produce.
        produce.setUsing(using.getId());
        addTrigger(plan, source.task, produce);
        if (plan.getGroup(group.getId()) == null) {
            plan.addGroup(group);
        }
        produce.setDuration(getEstimatedDuration(produce));
        produce.setEstimatedCompletionTime(estimatedCompletionTime(produce));

        return produce;
    }

   private Task produceGroup(StrategicPlan plan, GameMap map, UnitGroup group, Region goalRegion) {
        assert goalRegion != null : "region is null";
        // find production group in region
        GroupSource source = findNearestProductionGroup(plan, map, goalRegion, group);
        if (source == null) {
            return null;
        }
        if (source.task.getSuccessors().size() >= 2) {
            // task already has a production successor
            log.debug("Already a production successor for production task" + source.task);
            return null;
        }
        UnitGroup using = source.group;

        Task produce = new Task();
        produce.setName("produce" + getNextTaskId());
        produce.setType("produce");
        produce.setComment("produce new group");
        produce.setTargetGroup(group);  // group to produce.

        produce.setUsing(using.getId());
        addTrigger(plan, source.task, produce);

        plan.addGroup(group);
        produce.setDuration(getEstimatedDuration(produce));
        produce.setEstimatedCompletionTime(estimatedCompletionTime(produce));

        return produce;
    }

    /**
     * find nearest production group to region and its source Task.
     * Production group must be able to produce given group.
     */
    private GroupSource findNearestProductionGroup(StrategicPlan plan, GameMap map, Region region, UnitGroup group) {
        assert region != null : "region is null";
        GroupSource source = null;
        float time = Float.POSITIVE_INFINITY;
        Set<Task> productionSrcs = getProductionSources(plan);
        for (Task task : productionSrcs) {
            UnitGroup g = task.getTargetGroup();
            if (!g.isProduction()) {
                g = task.getUsingGroup();
            }
            if (g != null && canProduce(g, group)) {
                int maxSpeed = 10; // FOOTMAN speed
                // see ActionMove.getDisplacement for tiles/cycle formula.
                double tilesPerCycle = maxSpeed/5.0 * 1/32.0;
                //float t = l.getAvailable();
                float t = task.getEstimatedCompletionTime();
                t += map.getDistance(region, initialRegions.get(g)) / tilesPerCycle;
                if (t < time) {
                    source = new GroupSource(task, g);
                    time = t;
                }
            }
        }
        if (source == null) {
            log.warn("no production group found near region " + region.getId());
        }
        return source;
    }

    /** can production group produce the required group? */
    boolean canProduce(UnitGroup production, UnitGroup required) {
        if (production.isEmpty()) {
            return false;
        }
        List<Argument> args = required.getUnitTypeReqs();
        Set<String> types = new LinkedHashSet<String>();
        for (int i = 0; i < args.size(); i++) {
            types.add(args.get(i).getName());
        }
        return canProduce(production, types);
    }

    /** can production group complete the required group? */
    boolean canComplete(UnitGroup production, UnitGroup required) {
        if (production.isEmpty()) {
            return false;
        }
        List<Argument> args = required.getUnitTypeReqs();
        Set<String> reqTypes = new LinkedHashSet<String>();
        for (int i = 0; i < args.size(); i++) {
            reqTypes.add(args.get(i).getName());
        }
        Set<String> existing = required.getRepresentative().getUnitTypes().keySet();
        for (String type : existing) {
            reqTypes.remove(type);
        }

        return canProduce(production, reqTypes);
    }

    /**
     * can production group produce the required units?
     *
     *  TODO: production group must contain immediate requirements until we
     *  have the real production manager that can create buildings.
     * 
     */
    boolean canProduce(UnitGroup production, Set<String> types) {
        if (production.isEmpty()) {
            return false;
        }
        Map<String,Integer> producerTypes = production.getRepresentative().getUnitTypes();
        for (String type : types) {
            BuildRequirements reqs = est.getRequirements(type);
            String producerType = reqs.getProducer();
            if (!producerTypes.containsKey(producerType)) {
                return false;
            }
        }
        return true;
    }


    /**
     * find production group in region and its source Task.
     *
     * @return
     */
    private GroupSource findProductionGroupToCompleteGroup(StrategicPlan plan, Region region, UnitGroup required) {
        GroupSource source = null;
        Set<Task> productionSrcs = getProductionSources(plan);
        for (Task task: productionSrcs) {
            UnitGroup used = task.getUsingGroup();
            UnitGroup produced = task.getTargetGroup();
            if (used != null &&
                used.isProduction() &&
                initialRegions.get(used) == region &&
                canComplete(used, required)) {
                source = new GroupSource(task, used);
                break;
            } else if (produced != null &&
                       produced.isProduction() &&
                       initialRegions.get(produced) == region &&
                       canComplete(produced, required)) {
                // init-group task may produce a production group.
                source = new GroupSource(task, produced);
                break;
            }
        }
        return source;
    }

    /** get the minimum traversable width or height thru the region center. */
    private int getTraversableSpan(GameMap map, Region r) {
        int width = 0;
        int height = 0;
        int cx = r.getX();
        int cy = r.getY();
        for (int i = 0; r.contains(cx, cy+i) && map.getCell(cx, cy+i) == TileType.OTHER; i++) {
            height++;
        }
        for (int i = 1; r.contains(cx, cy-i) && map.getCell(cx, cy-i) == TileType.OTHER; i++) {
            height++;
        }
        for (int i = 0; r.contains(cx+i, cy) && map.getCell(cx+i, cy) == TileType.OTHER; i++) {
            width++;
        }
        for (int i = 1; r.contains(cx-i, cy) && map.getCell(cx-i, cy) == TileType.OTHER; i++) {
            width++;
        }

        return Math.min(width, height);
    }

    /**                 */
    private int getEstimatedDuration(Task task) {
        if ("produce".equals(task.getType())) {
            int time = 0;
            UnitGroup g = task.getTargetGroup();
            assert g != null : "target group not found for " + task;
            for (Argument arg : g.getUnitTypeReqs()) {
                time += est.getTime(arg.getName()) * arg.getValue();
            }
            return time;
        } else if ("attack".equals(task.getType())) {
            return 6000;
        }
        throw new RuntimeException("estimates only available for production and attack tasks.");
    }

    /**                 */
    private int estimatedCompletionTime(Task task) {
        Set<Task> predecessors = task.getPredecessors();
        int max = 0;
        for (Task pred : predecessors) {
            int time = estimatedCompletionTime(pred);
            if (time > max) {
                max = time;
            }
        }
        return max + task.getDuration();
    }

    private boolean meetsUnitTypeReqs(UnitGroup group) {
        // TODO: move this funtion to UnitGroup?
        List<Argument> reqs = group.getUnitTypeReqs();
        Map<String, Integer> counts = group.getRepresentative().getUnitTypes();
        for (Argument req : reqs) {
            if (!counts.containsKey(req.getName())) {
                return false;
            }
            if (counts.get(req.getName()) < req.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void addTrigger(StrategicPlan plan, Task pred, Task task) {
        plan.addTrigger(pred, task);

        // verify that pred uses or produces the group used by task.
        UnitGroup predUsing = pred.getUsingGroup();
        UnitGroup predProduces = pred.getTargetGroup();

        UnitGroup using = task.getUsingGroup();
        if (!(predUsing == using || predProduces == using)) {
            String msg = "Attempt to link " + pred + " -> " + task;
            throw new RuntimeException(msg);
        }

        // verify that we haven't add more than two successors to predecessor task
        if (pred.getSuccessors().size() > 2) {
                try {
                    StringWriter out = new StringWriter();
                    StrategicPlanWriter.write(plan, out);
                    log.debug(out.toString());
                } catch (IOException e) {
                    log.error("could not write plan " + plan);
                }
                logPlan(plan);
                throw new RuntimeException("added more than two successors to task " + pred);
        }
    }

    private void verifyPlan(StrategicPlan plan) {
        //  Check for some errors that have been seen before.
        //
        // 1. Check that groups that are produced are used.
        // 2. Check that we don't both initialize and produce a group.
        // 3. Attack tasks have no more than one successor
        // 4. Check that a task has no more than two successors.

        // 1.
        // if group is produced, then it must be used by one of its
        // successors.  We check that the predecessor group is used after
        // at least one successor is added.
        Set<Task> tasks = plan.getTasks();
        Set<UnitGroup> produced = new LinkedHashSet<UnitGroup>();
        Set<UnitGroup> used = new LinkedHashSet<UnitGroup>();
        for (Task task : tasks) {
            if ("produce".equals(task.getType()) ||
                "init-group".equals(task.getType())) {
                UnitGroup g = task.getTargetGroup();
                produced.add(g);                
            }
            if (task.getUsingGroup() != null) {
                used.add(task.getUsingGroup());
            }
        }
        produced.removeAll(used);
        if (!produced.isEmpty()) {
            logPlan(plan);
            log.debug("Did not use groups");
            for (UnitGroup g : produced) {
                log.debug(g);
            }
            throw new RuntimeException("Not all produced groups are used in plan " + plan);
        }


        // 2.
        produced.clear();
        for (Task task : tasks) {
            if ("produce".equals(task.getType()) ||
                "init-group".equals(task.getType())) {
                UnitGroup g = task.getTargetGroup();
                if (produced.contains(g)) {
                    logPlan(plan);
                    throw new RuntimeException("Cannot initialize and produce a group. Group ID:" + g.getId());
                } else {
                    produced.add(g);
                }
            }
        }
        
        // 3,4.
        for (Task task : tasks) {
            if ("attack".equals(task.getType()) && task.getSuccessors().size() > 1) {
                logPlan(plan);
                throw new RuntimeException("added more than one successor to task " + task);
            } else if (!StrategicPlan.NOOP_TYPE.equals(task.getType()) && task.getSuccessors().size() > 2) {
                logPlan(plan);
                throw new RuntimeException("added more than two successors to task " + task);
            }
        }

        // 5.
        for (UnitGroup g : plan.getGroups()) {
            if (GroupType.GROUP_BUILDING.equals(g.getType())) {
                assert !g.isEmpty(): "empty group " + g + " in plan " + plan;
            }
        }

    }

    private void logPlan(StrategicPlan plan) {
        StringWriter out = new StringWriter();
        out.write(NL);
        try {
            StrategicPlanWriter.write(plan, out);
            log.debug(out.toString());
        } catch (IOException ex) {
            log.error(ex);
        }
    }

    private void logTasks(StrategicPlan plan) {
        int max = 0;
        StringWriter out = new StringWriter();
        out.write("Player "+playerId+" leaf tasks:");
        for(Task t : plan.getLeafTasks()) {
            out.write(NL);
            out.write(t.toString());
            int time = estimatedCompletionTime(t);
            if (time > max) {
                max = time;
            }
        }
        out.write(NL);
        out.write("estimated plan completion in " + max + " cycles.");
        out.write(NL);
        log.debug(out.toString());
    }

    /**
     * log stats each planning event.
     */
    private void logStats() {
        if (stats == null) {
            return;
        }
        stats.setValue("player ID", playerId);
        stats.setValue("player",    template.getName());
        stats.setValue("strategy",  template.getName());
        stats.setValue("opponent",  opponent);
    }
}
