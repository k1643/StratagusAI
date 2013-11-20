package orst.stratagusai.stratplan.persist;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.Port;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.Trigger;

/**
 * 
 */
public class StrategyReaderTest
        extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyReaderTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(StrategyReaderTest.class);
    }


    /**  */
    public void testTriggers() throws Exception {

        StrategicPlan s = StrategicPlanReader.read("data/strategy1.strat");

        assertNotNull(s);
        assertEquals("s1", s.getName());
        Set<Task> tasks = s.getTasks();
        assertEquals(3, tasks.size());

        Map<String,Task> taskMap = new HashMap<String,Task>();
        for (Task task : tasks) {
            taskMap.put(task.getName(), task);
        }
        assertNotNull(taskMap.get("produce1"));
        assertNotNull(taskMap.get("defend1"));
        assertNotNull(taskMap.get("attack1"));

        // test trigger contruction
        Task task1 = taskMap.get("produce1");
        Port sPort = task1.getStartPort();
        Port ePort = task1.getEndPort();
        Set<Trigger> st = sPort.getOutgoingTriggers();
        assertEquals(1, st.size());
        Trigger tr = st.iterator().next();
        Port targetPort = s.getDestination(tr);
        assertNotNull(targetPort);
        assertEquals(taskMap.get("defend1").getStartPort(), s.getDestination(tr));
    }

    public void testArguments() throws Exception {
        StrategicPlan s = StrategicPlanReader.read("data/strategy1.strat");

        assertNotNull(s);
        Set<Task> tasks = s.getTasks();
        assertEquals(3, tasks.size());

        Map<String,Task> taskMap = new HashMap<String,Task>();
        for (Task task : tasks) {
            taskMap.put(task.getName(), task);
        }
        Task produce1 = taskMap.get("produce1");
        Task defend1 = taskMap.get("defend1");
        Task attack1 = taskMap.get("attack1");

        assertEquals(1, produce1.getTargetGroup().getId());

        assertEquals(1, defend1.getTargetGroup().getId());
        int regionId = defend1.getTargetRegionId();
        assertEquals(1, regionId);

        assertEquals(1, attack1.getTargetGroup().getId());
        regionId = attack1.getTargetRegionId();
        assertEquals(2, regionId);
         
         
    }

    public void testGroups() throws Exception {
        String s = "(:plan test :player 0 " +
                    " (:group-spec 1 :type group-combat unit-footman 3)" +
                    " (:task attack1 ((:region 2))" +
                    "  :type attack" +
                    "  :using 1" +
                    "  )" +
                    ")";
        Reader in = new StringReader(s);
        StrategicPlan plan = StrategicPlanReader.read(in);

        Task task = null;
        for (Task t : plan.getTasks()) {
            if ("attack1".equals(t.getName())) {
                task = t;
            }
        }
        assertNotNull(task);

        // test getting group from task
        UnitGroup g = task.getUsingGroup();
        assertEquals(1, g.getId());
         
        // test getting group from strategy
        PlayerGroups groups = plan.getPlayerGroups();
        assertEquals(1, groups.size());
        UnitGroup spec = plan.getGroup(1);
        assertNotNull(spec);
    }
    /*
    public void testInitialUnits() throws Exception {
        String s =
            "(:plan prod0" +
            "(:group-spec 1 unit-town-hall 1 :initial-units (1 2 3))" +
            "(:task init-group0-1 ((:group 1))" +
            "  :type init-group" +
            "))";
        Reader in = new StringReader(s);
        StrategicPlan strat = StrategicPlanReader.read(in);
        UnitGroup spec = strat.getGroup(1);
        assertNotNull(spec);
        Set<Integer> ids = spec.getInitialUnitIds();
        assertEquals(3, ids.size());
        assertTrue(ids.contains(1));
        assertTrue(ids.contains(2));
        assertTrue(ids.contains(3));
    } */
}
