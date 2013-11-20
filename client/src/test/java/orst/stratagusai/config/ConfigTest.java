package orst.stratagusai.config;

import java.util.List;
import java.util.Map;
import orst.stratagusai.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * 
 */
public class ConfigTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ConfigTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ConfigTest.class );
    }

    /**
     *
     */
    public void testLoad() throws Exception {
        Config conf = Config.load("data/config-test.yaml");
        List<String> maps = conf.getMapPaths();
        assertEquals(1, maps.size());
        assertEquals("../../maps/one_way_in_one_way_out_PvC.smp", maps.get(0));
        ControllerConfig[] configs = conf.getAgentConfigs();
        assertEquals(1, configs.length);

        ControllerConfig aconf = configs[0];
        assertEquals("orst.stratagusai.HeuristicAgent", aconf.getControllerClassName());

        Map planner = (Map) aconf.getParam("planner");
        assertEquals("orst.stratagusai.fake.Planner", planner.get("className"));
        assertEquals("strategy.txt", planner.get("strategy"));

        String className = (String) aconf.getParam("tactical");
        assertEquals("orst.stratagusai.fake.TacticalManager", className);

        className = (String) aconf.getParam("production");
        assertEquals("orst.stratagusai.fake.ProductionManager", className);
    }
}
