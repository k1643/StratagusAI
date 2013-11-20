package orst.stratagusai.stratsim.planner;

import java.util.LinkedHashMap;
import java.util.Map;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;
import orst.stratagusai.stratsim.TestGame;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratsim.model.SimState;

/**
 * 
 */
public class SwitchingPlannerTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SwitchingPlannerTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( SwitchingPlannerTest.class );
    }

    /**
     *
     */
    public void testOneWayGame() throws Exception
    {
        GameState game = TestGame.getOneWayInOneWayOutWithBases();
        SwitchingPlanner planner = new SwitchingPlanner();
        Map params = new LinkedHashMap();
        params.put("strategy_set", "atk-dfnd");
        params.put("simReplan", true);
        params.put("choice_method", "Nash");
        params.put("player", "Nash");
        params.put("opponent", "test");
        planner.configure(params);

        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        StrategicState s = new StrategicState(gs, game);  // different UnitGroup 3 objects in plan and state.     
        StrategicPlan strat = planner.makePlan(1, s);
        StrategicPlanWriter.write(strat, "data/simulation_strategy.txt");

    }


}
