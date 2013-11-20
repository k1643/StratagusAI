package orst.stratagusai.stratplan;

import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.persist.StrategicPlanReader;

/**
 *
 * @author Brian
 */
public class StrategyTest
   extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( StrategyTest.class );
    }

    public void testRootTasks() throws Exception {
        StrategicPlan plan = StrategicPlanReader.read("data/strategy1.strat");
        Task root = plan.getStart();
        assertNotNull(root);
    }
}
