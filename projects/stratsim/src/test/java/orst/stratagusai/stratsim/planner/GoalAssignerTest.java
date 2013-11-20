package orst.stratagusai.stratsim.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.persist.GameStateReader;

/**
 * 
 */
public class GoalAssignerTest
    extends TestCase
{
    private static final Logger log = Logger.getLogger(GoalAssignerTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GoalAssignerTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GoalAssignerTest.class );
    }

    public void testStrategicActions() throws Exception {
        GameState game = GameStateReader.load("data/combat-game.txt");
        GameMap map = game.getMap();

        StrategyTemplate strategy = StrategyTemplate.getRush(10,false);
        
        // create goal to attack enemy regions 7 and 8.
        List<StrategicGoal> goals = new ArrayList<StrategicGoal>();
        goals.add(strategy.getEnemyBaseGoal(map.getRegion(7)));
        goals.add(strategy.getEnemyBaseGoal(map.getRegion(8)));
        goals.add(strategy.getChokepointGoal(map.getRegion(4)));

        List<Region> bases = new ArrayList<Region>();
        
        int epochs = goals.size() + 1;
//        StrategicAction[][] a = GoalAssigner.getActions(epochs, groups, bases, goals);
//        assertEquals(epochs, a.length);
/*
        StringBuffer msg = new StringBuffer("\n");
        for (int t = 0; t < epochs; t++) {
            msg.append("epoch " + t + "\n");
            for (int i = 0; i < a[t].length; i++) {
                msg.append("  ");
                msg.append(a[t][i]);
                msg.append("\n");
            }
        }
        log.debug(msg.toString()); */
    }

    public void testStrategicActions2() throws Exception {
        // start with 0 combat groups and 2 bases.
        GameState game = GameStateReader.load("data/combat-game.txt");
        GameMap map = game.getMap();

        StrategyTemplate strategy = StrategyTemplate.getRush(10,false);
        List<UnitGroup> groups = new ArrayList<UnitGroup>();

        // create goal to attack enemy regions 7 and 8.
        List<StrategicGoal> goals = new ArrayList<StrategicGoal>();
        goals.add(strategy.getEnemyBaseGoal(map.getRegion(7)));
        goals.add(strategy.getEnemyBaseGoal(map.getRegion(8)));

        List<Region> bases = new ArrayList<Region>();
        bases.add(game.getMap().getRegion(1));
        bases.add(game.getMap().getRegion(2));

        int epochs = goals.size() + 1;
 /*       StrategicAction[][] a = GoalAssigner.getActions(epochs, groups, bases, goals);
        assertEquals(epochs, a.length);

        StringBuffer msg = new StringBuffer("\n");
        for (int t = 0; t < epochs; t++) {
            msg.append("epoch " + t + "\n");
            for (int i = 0; i < a[t].length; i++) {
                msg.append("  ");
                msg.append(a[t][i]);
                msg.append("\n");
            }
        }
        log.debug(msg.toString()); */
    }

    /**
     *
     */
    public void testMassAttack() throws Exception
    {
        // combat-game each player has two groups of six footmen
        GameState game = GameStateReader.load("data/combat-game.txt");
        GameMap map = game.getMap();

        StrategyTemplate strategy = StrategyTemplate.getRush(10,false);
        //StrategicState state = new StrategicState(game);

        // create goal to attack enemy regions 7 and 8.
        List<StrategicGoal> goals = new ArrayList<StrategicGoal>();
        goals.add(strategy.getEnemyBaseGoal(map.getRegion(7)));
        goals.add(strategy.getEnemyBaseGoal(map.getRegion(8)));

        List<Region> bases = new ArrayList<Region>();

        // massAttack=false.  Assign each group to a different target.
        //List<Assignment> as = new ArrayList<Assignment>(GoalAssigner.assign(0, state, groups, locations, bases, goals, false));
        //assertFalse(as.get(0).goal.getRegion() == as.get(1).goal.getRegion());

        // massAttack=true.  Can Assign each group to a same target.
        /*as = new ArrayList<Assignment>(GoalAssigner.assign(0, state, groups, locations, bases, goals, true));
        for (Assignment a : as) {
            log.debug(a);
        }
        assertTrue(as.get(0).goal.getRegion() == as.get(1).goal.getRegion()); */
    }

    private List<Region> getLocations(GameState s, List<UnitGroup> allyGroups) {
        // plan using groups with existing units.
        final int N = allyGroups.size();
        List<Region> locs = new ArrayList<Region>();
        for (int i = 0; i < N; i++) {
            UnitGroup g = allyGroups.get(i);
            assert !g.isEmpty() : "groups should have units to get group location.";
            locs.add(getMajorityRegion(s, g));
        }
        return locs;
    }

    private static Region getMajorityRegion(GameState s, UnitGroup ally) {
        return getMajorityRegionOfUnits(s, ally.getUnits());
    }

    private static Region getMajorityRegionOfUnits(GameState s, Set<Unit> units) {
        GameMap map = s.getMap();
        Map<Region,Integer> counts = new HashMap<Region,Integer>();
        for (Unit u : units) {
            Region r = map.getRegion(u);
            if (counts.containsKey(r)) {
                int c = counts.get(r);
                counts.put(r, c+1);
            } else {
                counts.put(r, 1);
            }
        }
        Region region = null;
        int c = 0;
        for (Map.Entry<Region,Integer> entry : counts.entrySet()) {
            if (entry.getValue() > c) {
                region = entry.getKey();
                c = entry.getValue();
            }
        }
        return region;
    }
}
