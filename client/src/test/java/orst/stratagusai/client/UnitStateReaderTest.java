package orst.stratagusai.client;

import java.util.Map;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.GameProxy;
import orst.stratagusai.Unit;
import orst.stratagusai.UnitStateReader;

/**
 * 
 */
public class UnitStateReaderTest
        extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public UnitStateReaderTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(UnitStateReaderTest.class);
    }


    /**  */
    public void testUpdate() throws Exception {

        GameProxy proxy = new GameProxy();

        // unit_state has units 0,1,2 for player 0
        UnitStateReader.read(proxy, "data/unit_state.txt");

        Map<Integer,Unit> units = proxy.getUnits();

        Unit u = units.get(0);
        assertNotNull(u);
        assertEquals(0, u.getUnitId());
        assertNotNull(units.get(1));
        assertNotNull(units.get(2));
    }

    public void testUpdateDead() throws Exception {

        GameProxy proxy = new GameProxy();
        Unit u = new Unit(4);
        u.setHitPoints(60);
        proxy.addUnit(0, u);

        // unit_state has units 0,1,2 for player 0
        UnitStateReader.read(proxy, "data/unit_state.txt");

        Map<Integer,Unit> units = proxy.getUnits();

        // 4 should be marked dead, because it is not updated when reading
        // unit_state.txt.
        //
        u = units.get(4);
        assertNotNull(u);
        assertTrue(u.isDead());
    }
}
