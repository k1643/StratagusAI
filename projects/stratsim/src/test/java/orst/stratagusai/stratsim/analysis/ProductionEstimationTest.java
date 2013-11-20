package orst.stratagusai.stratsim.analysis;

import orst.stratagusai.stratsim.planner.UnsatisfiableGoalException;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratsim.model.BuildRequirements;
import orst.stratagusai.stratsim.planner.UnitRequirement;

/**
 * Unit test for simple App.
 */
public class ProductionEstimationTest
    extends TestCase
{
    private static final Logger log =
            Logger.getLogger(ProductionEstimationTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ProductionEstimationTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ProductionEstimationTest.class );
    }

    /**
     * 
     */
    public void testRead() throws Exception {
        // get build requirements from build_requirements.yaml
        ProductionEstimation est = ProductionEstimation.getEstimator();
        assertNotNull(est);
        BuildRequirements req = est.getRequirements("unit-archer");
        assertNotNull(req);

        BuildState s = new BuildState();
        s.increment("unit-town-hall");  // have to start with peasant or town-hall

        log.debug("unit-peasant " + est.getTimeEstimate("unit-peasant", s));

    }

    public void testPlan() throws Exception {
        ProductionEstimation est = ProductionEstimation.getEstimator();
        BuildState state = new BuildState();
        state.increment("unit-town-hall");
        BuildState goal = new BuildState();
        goal.increment("unit-peasant");
        List<UnitRequirement> plan = est.getPlan(state, goal);
        assertEquals(1, plan.size());
        assertEquals("unit-peasant", plan.get(0).getType());
    }

    public void testLoopPlan() throws Exception {
        ProductionEstimation est = ProductionEstimation.getEstimator();
        BuildState state = new BuildState();
        state.increment("unit-peasant");
        BuildState goal = new BuildState();
        goal.increment("unit-town-hall");
        List<UnitRequirement> plan = est.getPlan(state, goal);
        assertEquals(1, plan.size());
        assertEquals("unit-town-hall", plan.get(0).getType());
    }

    public void testLongPlan() throws Exception {
        ProductionEstimation est = ProductionEstimation.getEstimator();
        BuildState state = new BuildState();
        state.increment("unit-peasant");
        BuildState goal = new BuildState();
        goal.increment("unit-knight");
        List<UnitRequirement> plan = est.getPlan(state, goal);

        assertEquals("unit-human-barracks", plan.get(0).getType());
        assertEquals("unit-human-blacksmith", plan.get(1).getType());
        assertEquals("unit-town-hall", plan.get(2).getType());
        assertEquals("unit-keep", plan.get(3).getType());
        assertEquals("unit-stables", plan.get(4).getType());
        assertEquals("unit-knight", plan.get(5).getType());
    }

    public void testInfeasibleGoal() throws Exception {
        ProductionEstimation est = ProductionEstimation.getEstimator();
        BuildState state = new BuildState();
        BuildState goal = new BuildState();
        goal.increment("unit-peasant");
        try {
            est.getPlan(state, goal);
            fail("should throw UnsatisfiableGoalException");
        } catch (UnsatisfiableGoalException e) {
            // expected.
        }
    }

    public void testAddUnits() throws Exception {
        // test adding units to existing type.
//  initial state [BuildState unit-peasant 1 unit-human-barracks 1 unit-town-hall 1 unit-elven-lumber-mill 1 unit-footman 6]
//  goal state [BuildState unit-peasant 1 unit-human-barracks 1 unit-town-hall 1 unit-elven-lumber-mill 1 unit-footman 12]

        ProductionEstimation est = ProductionEstimation.getEstimator();
        BuildState state = new BuildState();
        state.increment("unit-peasant");
        state.increment("unit-human-barracks");
        state.increment("unit-town-hall");
        state.increment("unit-elven-lumber-mill");
        state.increment("unit-footman", 6);
        BuildState goal = (BuildState) state.clone();
        goal.increment("unit-footman", 6);

        est.getPlan(state, goal);
    }

    public void testFootman() throws Exception {
        ProductionEstimation est = ProductionEstimation.getEstimator();
        BuildState state = new BuildState();
        state.increment(WargusUnitType.HUMAN_BARRACKS.getName());
        BuildState goal = new BuildState();
        goal.increment("unit-footman", 6);
        List<UnitRequirement> plan = est.getPlan(state, goal);

    }
}
