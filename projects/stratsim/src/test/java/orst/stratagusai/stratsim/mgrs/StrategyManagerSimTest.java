package orst.stratagusai.stratsim.mgrs;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;

/**
 * Unit tests for StrategyManager.
 */
public class StrategyManagerSimTest
        extends TestCase {
        private static final Logger log = Logger.getLogger(StrategyManagerSimTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyManagerSimTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(StrategyManagerSimTest.class);
    }
    /*
    public void testNextActions() throws Exception {
        GameState state = TestGame.getOneWayInOneWayOut();
        StrategicPlan s0 = StrategicPlanReader.read("data/strategy.strat");
        assertNotNull(s0);

        UnitAbstractor ua = new UnitAbstractor();
        state = ua.getAbstractState(state,
                                    s0.getGroupSpecs(),
                                    new HashSet<UnitGroup>());
        List<Unit> units = state.getUnits();
        // one GroupSim created for player 0's peasant.
        assertEquals(1, units.size());

        UnitPool pool = new UnitPool();
        StrategyManager stratman = new StrategyManagerSim();
        ProductionManager prodMan = new ProductionManager(0);
        TacticalManager tactMan = new TacticalManager(0);

        stratman.setPlayerId(0);
        stratman.setPlan(s0);
        stratman.addManager(prodMan, "produce");
        stratman.addManager(tactMan, "attack");
        stratman.addManager(tactMan, "secure");
        stratman.setUnitPool(pool);

        stratman.nextActions(state);
    } */

    
    public void testConcurrency() throws Exception {
    }
    /*
        // map has town halls in regions 1,3, and 4.
        // town-hall id=
        //
        //
        
        String stratString =
          "(:plan concurrency " +
          "  (:group-spec 1 unit-town-hall 1 :initial-units (1)) " +
          "  (:group-spec 2 unit-town-hall 1 :initial-units (2)) " +
          "  (:group-spec 3 unit-town-hall 1 :initial-units (3)) " +
          "  (:group-spec 4 unit-peasant 1)" +
          "  (:group-spec 5 unit-peasant 1)" +
          "  (:group-spec 6 unit-peasant 1)" +

          "  (:task init1 ((:group 1)) :type init-group " +
          "     :end (:trigger (start concurrentProduce1))" +
          "  )" +
          "  (:task init2 ((:group 2)) :type init-group " +
          "     :end (:trigger (start concurrentProduce2))" +
          "  )" +
          "  (:task init3 ((:group 3)) :type init-group " +
          "     :end (:trigger (start concurrentProduce3))" +
          "  )" +
          "  (:task concurrentProduce1 ((:group 4)) " +
          "    :type produce" +
          "    :using 1) " +
          "  (:task concurrentProduce2 ((:group 5)) " +
          "    :type produce" +
          "    :using 2) " +
          "  (:task concurrentProduce3 ((:group 6)) " +
          "    :type produce" +
          "    :using 3) " +
          ")";
        StringReader rd = new StringReader(stratString);
        StrategicPlan s0 = StrategicPlanReader.read(rd);
        StrategicPlan s1 = new StrategicPlan("s1");

        GameState state = GameStateReader.load("data/one-way-3-producers.txt");
        UnitAbstractor ua = new UnitAbstractor();
        state = ua.getAbstractState(state,
                                    s0.getGroupSpecs(),
                                    s1.getGroupSpecs());

        UnitPool pool = new UnitPool();
        StrategyManager stratman = new StrategyManagerSim();
        ProductionManager prodMan = new ProductionManager(0);

        stratman.setPlayerId(0);
        stratman.setPlan(s0);
        stratman.addManager(prodMan, "produce");
        stratman.setUnitPool(pool);

        stratman.nextActions(state);

     //   assertEquals(3, prodMan.getActiveTasks().size());
         
    } */
}
