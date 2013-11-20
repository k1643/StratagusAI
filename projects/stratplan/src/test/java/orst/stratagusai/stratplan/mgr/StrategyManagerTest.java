package orst.stratagusai.stratplan.mgr;

import orst.stratagusai.stratplan.model.GameState;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.persist.StrategicPlanReader;

/**
 * Unit tests for StrategyManager.
 */
public class StrategyManagerTest
        extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyManagerTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(StrategyManagerTest.class);
    }

    // Tests
    public void testStrategy() throws Exception {
        // test strategy construction
        StrategicPlan strategy = StrategicPlanReader.read("data/strategy.strat");
        assertNotNull(strategy);
        assertEquals("s1", strategy.getName());
        Set<Task> tasks = strategy.getTasks();
        assertEquals(3, tasks.size());
    }

    public void testPropagation() throws Exception {
        StrategicPlan strategy = StrategicPlanReader.read("data/strategy.strat");
        assertNotNull(strategy);

        System.out.println("\n"+strategy.toString()+"\n");

        StrategyManager stratman = new StrategyManager();
        stratman.setPlayerId(0);
        DummyManager prodMan = new DummyManager();
        DummyManager tactMan = new DummyManager();

        stratman.setPlan(strategy);
        stratman.addManager(prodMan, "produce");
        stratman.addManager(tactMan, "attack");
        stratman.addManager(tactMan, "secure");

        Map<String,Task> taskMap = new HashMap<String,Task>();
        for (Task task : strategy.getTasks()) {
            taskMap.put(task.getName(), task);
        }

        stratman.nextActions(null);

        assertTrue(prodMan.getTasks().contains(taskMap.get("produce1")));
        assertTrue(tactMan.getTasks().contains(taskMap.get("defend1")));
        assertTrue(taskMap.get("produce1").isActive());
        assertFalse(taskMap.get("produce1").isComplete());
        assertFalse(taskMap.get("attack1").isActive());
        assertFalse(taskMap.get("attack1").isComplete());
        assertTrue(taskMap.get("defend1").isActive());
        assertFalse(taskMap.get("defend1").isComplete());

        // in DummyManager a task takes 2 cycles to complete.
        stratman.nextActions(null);
        stratman.nextActions(null);

        assertTrue(prodMan.getTasks().isEmpty());
        assertTrue(tactMan.getTasks().contains(taskMap.get("attack1")));
        assertFalse(taskMap.get("produce1").isActive());
        assertTrue(taskMap.get("produce1").isComplete());
        assertTrue(taskMap.get("attack1").isActive());
        assertFalse(taskMap.get("attack1").isComplete());
        assertFalse(taskMap.get("defend1").isActive());
        assertTrue(taskMap.get("defend1").isComplete());

        stratman.nextActions(null);
        stratman.nextActions(null);

        assertTrue(prodMan.getTasks().isEmpty());
        assertTrue(tactMan.getTasks().isEmpty());
        assertFalse(taskMap.get("produce1").isActive());
        assertTrue(taskMap.get("produce1").isComplete());
        assertFalse(taskMap.get("attack1").isActive());
        assertTrue(taskMap.get("attack1").isComplete());
        assertFalse(taskMap.get("defend1").isActive());
        assertTrue(taskMap.get("defend1").isComplete());
    }
    
    // Dummy manager
    private class DummyManager extends AbstractManager {
        protected Queue<Task> taskQueue = new LinkedList<Task>();

        protected Map<Task,Integer> cycle = new HashMap<Task,Integer>();

        public DummyManager() {}

        // complete current task
        public void complete() {
            Task currentTask = taskQueue.peek();
            assertNotNull(currentTask);

            parent.notifyComplete(currentTask);
            taskQueue.remove();
        }

        public Set<Task> getTasks() {
            return new LinkedHashSet(taskQueue);
        }

        public void addTask(Task task) {
            taskQueue.add(task);
            cycle.put(task,0);
        }

        public void terminateTask(Task task) throws TaskTerminationException {
            if (!taskQueue.remove(task)) {
                throw new TaskTerminationException("could not terminate");
            }
        }

        public void terminateTasks() {
            taskQueue.clear();
        }

        public void nextActions(GameState state) {
            if (taskQueue.isEmpty()) {
                return;
            }
            Task currentTask = taskQueue.peek();
            // give each task 2 cycles to complete
            int t = cycle.get(currentTask) + 1;
            cycle.put(currentTask, t);
            if (("produce".equals(currentTask.getType()) ||
                "attack".equals(currentTask.getType())) &&
                t % 2 == 0) {
                complete();
            }
        }
    }
}
