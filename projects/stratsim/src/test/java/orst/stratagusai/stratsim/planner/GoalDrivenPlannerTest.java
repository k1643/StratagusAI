package orst.stratagusai.stratsim.planner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.persist.GameStateReader;
import orst.stratagusai.stratplan.persist.StrategicPlanReader;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;
import orst.stratagusai.stratsim.TestGame;

/**
 * 
 */
public class GoalDrivenPlannerTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GoalDrivenPlannerTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GoalDrivenPlannerTest.class );
    }

    /**
     *
     */
    public void testPlanWithExistingCombatGroups() throws Exception
    {
        GameState game = GameStateReader.load("data/combat-game.txt");
        Map<String,String> config = new HashMap<String,String>();
        config.put("strategy", "rush_10");
        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.configure(config);
        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        StrategicState s = new StrategicState(gs, game);  // no simulation needed.
        // TODO: uncomment this when chokepoint goals re-enabled.
        /*
        StrategicPlan plan = planner.makePlan(0, s);

        StrategicPlanWriter.write(plan, "data/goal_formation_existing_combat.txt");
        
         */
    }


    public void testGoalAssigner() throws Exception
    {
        // game has one peasant for each player.  Each player will have
        // four goals: an allied base, 2 chokepoints, and an enemy base.
        GameState game = TestGame.getOneWayInOneWayOut();
        Map<String,String> config = new HashMap<String,String>();
        config.put("strategy", "rush_10");
        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.configure(config);
        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        assertEquals(1,gs[0].size());
        assertEquals(1,gs[1].size());
        StrategicState s = new StrategicState(gs, game);  // no simulation needed.
        StrategicPlan plan = planner.makePlan(0, s);

        StrategicPlanWriter.write(plan, "data/footmans_rush_10_0.txt");
    }

    public void testGoalAssigner2() throws Exception
    {
        GameState game = TestGame.getOneWayInOneWayOut();
        Map<String,String> config = new HashMap<String,String>();
        config.put("strategy", "balanced_10");
        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.configure(config);
        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        StrategicState s = new StrategicState(gs, game);  // no simulation needed.        
        StrategicPlan plan = planner.makePlan(0, s);

        StrategicPlanWriter.write(plan, "data/balanced_10_1.txt");
    }

    public void testGoalSorter() throws Exception {
        GameState game = TestGame.getOneWayInOneWayOut();
        // add production buildings for player 1 (enemy).
        Region r7 = game.getMap().getRegion(7);
        WargusUnitPrototypes protos = new WargusUnitPrototypes();
        int nextId = 8;
        int enemyId = 1;
        Unit u = protos.createUnit(WargusUnitType.HUMAN_BARRACKS.getName(), nextId++, enemyId);
        u.setHitPoints(u.getMaxHP());
        u.setLoc(r7.getX(), r7.getY());
        game.addUnit(u);
        u = protos.createUnit(WargusUnitType.HUMAN_BARRACKS.getName(), nextId++, enemyId);
        u.setHitPoints(u.getMaxHP());
        u.setLoc(r7.getX(), r7.getY());
        game.addUnit(u);
        Region r8 = game.getMap().getRegion(8);
        u = protos.createUnit(WargusUnitType.HUMAN_BARRACKS.getName(), nextId++, enemyId);
        u.setHitPoints(u.getMaxHP());
        u.setLoc(r8.getX(), r8.getY());
        game.addUnit(u);
        
        // make planner
        Map<String,String> config = new HashMap<String,String>();
        config.put("strategy", "rush_7");
        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.init(0); // player 0
        planner.configure(config);
        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        StrategicState state = new StrategicState(gs, game);  
        List<StrategicGoal> goals = planner.makeGoals(state);
        for (StrategicGoal g : goals) {
            System.out.println("  " + g);
        }
        // enemy bases should be first.  Weakest base (region 8) should come before stronger base.
        StrategicGoal goal = goals.get(0);
        assertEquals(3, goal.getPriority());
        assertEquals(GoalType.SECURE_ENEMY_BASE, goal.getType());
        assertEquals(8, goal.getRegion().getId());

        goal = goals.get(1);
        assertEquals(3, goal.getPriority());
        assertEquals(GoalType.SECURE_ENEMY_BASE, goal.getType());
        assertEquals(7, goal.getRegion().getId());
    }

    public void testState1313689910526_initial() throws Exception {
        // reproduce planning error java.lang.RuntimeException: Not all produced groups are used.
        InputStream is =
                ClassLoader.getSystemResourceAsStream("game_state_1313689910526.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        GameState game = GameStateReader.load(br);

        Map<String,String> config = new HashMap<String,String>();
        config.put("strategy", "chokepoint_8");
        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.configure(config);

        // previous plan
        // Initial Groups:
        // 10:51:50,550 [GoalDrivenPlanner.java:239] : Initial Groups:
        // [UnitGroup 1 :type group-building (:required unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1) (:actual unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)]
        //      IDs: 6 7 8 9 10 11 12 13 14 15 16
        // [UnitGroup 2 :type group-building (:required unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1) (:actual unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)] I
        //      IDs: 17 19 18 21 20 23 22 25 24 27 26
        // [UnitGroup 9 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-footman 2)]
        //      IDs: 113 103
        // [UnitGroup 10 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-archer 2 unit-footman 2)]
        //      IDs: 108 110 112 57
        // [UnitGroup 8 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-archer 1 unit-footman 4)]
        //      IDs: 67 78 51 86 101
        //  [UnitGroup 7 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-archer 2 unit-footman 6)]
        //      IDs: 75 64 68 55 76 100 104 106
        StringReader in = new StringReader("(:plan secure_chokepoint_8_0 :player 0 " +
  "(:group-spec 1 :type group-building unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)" +
  "(:group-spec 2 :type group-building unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)" +
  "(:group-spec 9 :type group-combat unit-archer 2 unit-footman 6)" +
  "(:group-spec 10 :type group-combat unit-archer 2 unit-footman 6)" +
  "(:group-spec 8 :type group-combat unit-archer 2 unit-footman 6)" +
  "(:group-spec 7 :type group-combat unit-archer 2 unit-footman 6))");


        final int playerId = 0;
        StrategicPlan prevPlan = StrategicPlanReader.read(in);
        assertEquals(playerId, prevPlan.getGroup(1).getOwnerId());
        addUnits(game, playerId,prevPlan.getGroup(1), 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        addUnits(game, playerId,prevPlan.getGroup(2), 17, 19, 18, 21, 20, 23, 22, 25, 24, 27, 26);
        addUnits(game, playerId,prevPlan.getGroup(9), 113, 103);
        addUnits(game, playerId,prevPlan.getGroup(10), 108, 110, 112, 57);
        addUnits(game, playerId,prevPlan.getGroup(8), 67, 78, 51, 86, 101);
        addUnits(game, playerId,prevPlan.getGroup(7), 75, 64, 68, 55, 76, 100, 104, 106);

        PlayerGroups[] gs = {
            prevPlan.getPlayerGroups(),
            GroupAnalysis.defineGroups(1, game)
        };
        
        StrategicState s = new StrategicState(gs, game);
        // first steps of planner.replan()
        planner.init(playerId);
        planner.setInitialRegions(s.getInitialRegions());
        StrategicPlan plan = planner.initialPlan(s);

        // verify that there is an initialization or production task for each
        // initial group.
        Set<UnitGroup> allyGroups = s.getGroups(playerId);
        assertEquals(6, allyGroups.size());
        for (Task task : plan.getTasks()) {
            if (StrategicPlan.INIT_GROUP.equals(task.getType()) ||
                "produce".equals(task.getType())) {
                assertTrue(allyGroups.remove(task.getTargetGroup()));
            }
        }
        assertTrue(allyGroups.isEmpty());

        StringWriter out = new StringWriter();
        StrategicPlanWriter.write(plan, out);
        System.out.println(out.toString());
    }

    public void testState1313689910526() throws Exception {
        InputStream is =
                ClassLoader.getSystemResourceAsStream("game_state_1313689910526.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        GameState game = GameStateReader.load(br);

        Map<String,String> config = new HashMap<String,String>();
        config.put("strategy", "chokepoint_8");
        GoalDrivenPlanner planner = new GoalDrivenPlanner();
        planner.init(0); // player 0
        planner.configure(config);

        // previous plan
        // Initial Groups:
        // 10:51:50,550 [GoalDrivenPlanner.java:239] : Initial Groups:
        // [UnitGroup 1 :type group-building (:required unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1) (:actual unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)]
        //      IDs: 6 7 8 9 10 11 12 13 14 15 16
        // [UnitGroup 2 :type group-building (:required unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1) (:actual unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)] I
        //      IDs: 17 19 18 21 20 23 22 25 24 27 26
        // [UnitGroup 9 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-footman 2)]
        //      IDs: 113 103
        // [UnitGroup 10 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-archer 2 unit-footman 2)]
        //      IDs: 108 110 112 57
        // [UnitGroup 8 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-archer 1 unit-footman 4)]
        //      IDs: 67 78 51 86 101
        //  [UnitGroup 7 :type group-combat (:required unit-archer 2 unit-footman 6) (:actual unit-archer 2 unit-footman 6)]
        //      IDs: 75 64 68 55 76 100 104 106
        StringReader in = new StringReader("(:plan secure_chokepoint_8_0 :player 0 " +
  "(:group-spec 1 :type group-building unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)" + 
  "(:group-spec 2 :type group-building unit-peasant 1 unit-elven-lumber-mill 1 unit-town-hall 1 unit-farm 7 unit-human-barracks 1)" + 
  "(:group-spec 9 :type group-combat unit-archer 2 unit-footman 6)" + 
  "(:group-spec 10 :type group-combat unit-archer 2 unit-footman 6)" + 
  "(:group-spec 8 :type group-combat unit-archer 2 unit-footman 6)" + 
  "(:group-spec 7 :type group-combat unit-archer 2 unit-footman 6))");


        StrategicPlan prevPlan = StrategicPlanReader.read(in);
        addUnits(game, 0,prevPlan.getGroup(1), 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        addUnits(game, 0,prevPlan.getGroup(2), 17, 19, 18, 21, 20, 23, 22, 25, 24, 27, 26);
        addUnits(game, 0,prevPlan.getGroup(9), 113, 103);
        addUnits(game, 0,prevPlan.getGroup(10), 108, 110, 112, 57);
        addUnits(game, 0,prevPlan.getGroup(8), 67, 78, 51, 86, 101);
        addUnits(game, 0,prevPlan.getGroup(7), 75, 64, 68, 55, 76, 100, 104, 106);

        PlayerGroups[] gs = {
            prevPlan.getPlayerGroups(),
            GroupAnalysis.defineGroups(1, game)
        };
        StrategicState s = new StrategicState(gs, game);
        planner.replan(0, prevPlan, s);
    }

    private void addUnits(GameState s, int playerId, UnitGroup g, int...ids) {
        for (int i = 0; i < ids.length; i++) {
            Unit u = s.getUnit(ids[i]);
            assertNotNull(u);
            assertEquals(playerId, u.getOwnerId());
            g.addUnit(u);
        }
    }
}
