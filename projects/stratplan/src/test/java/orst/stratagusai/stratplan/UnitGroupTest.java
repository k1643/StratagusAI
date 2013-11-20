package orst.stratagusai.stratplan;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.persist.StrategicPlanReader;

/**
 *
 * @author Brian
 */
public class UnitGroupTest
   extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public UnitGroupTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( UnitGroupTest.class );
    }

    public void testBlank() throws Exception {}
    
    /*
    public void testClone() throws Exception {
        UnitGroup g = new UnitGroup(0,0);
        WargusUnitPrototypes protos = new WargusUnitPrototypes();
        Unit[] us = {
            protos.createUnit(WargusUnitType.ARCHER.getName(), 0, 0),
            protos.createUnit(WargusUnitType.BALLISTA.getName(), 1, 0),
        };
        Argument[] args  = {
            new Argument(WargusUnitType.FOOTMAN.getName(), 1),
            new Argument(WargusUnitType.FOOTMAN.getName(), 1)
        };
        for (Unit u : us) {
            g.addUnit(u);
        }
        for (Argument a : args) {
            g.addUnitTypeReq(a);
        }

        // clone group and test that collection order is preserved
        UnitGroup g1 = (UnitGroup) g.clone();
        int i = 0;
        for (Unit u : g1.getUnits()) {
            assertEquals(u.getUnitId(), us[i].getUnitId());
            assertFalse(u == us[i]); // make sure it was cloned.
            i++;
        }
        i = 0;
        for (Argument a : g1.getUnitTypeReqs()) {
            assertEquals(a.getName(), args[i].getName());
            assertEquals(a.getValue(), args[i].getValue());
            assertFalse(a == args[i]); // make sure it was cloned.
            i++;
        }
    } */
}
