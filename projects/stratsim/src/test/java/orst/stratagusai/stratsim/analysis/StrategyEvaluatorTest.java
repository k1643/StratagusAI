package orst.stratagusai.stratsim.analysis;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.GameStateReader;

/**
 *
 */
public class StrategyEvaluatorTest
    extends TestCase
{
    private static final Logger log =
            Logger.getLogger(StrategyEvaluatorTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyEvaluatorTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( StrategyEvaluatorTest.class );
    }


    public void testEvaluation() throws Exception {
        GameState state = GameStateReader.load("data/combat-game.txt");
        //StrategicPlanner planner = new StrategicPlanner();
/*        Strategy s0 = planner.makeStrategy(0, state);
        Strategy s1 = planner.makeStrategy(1, state); */

    }
}
