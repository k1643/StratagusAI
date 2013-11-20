package orst.stratagusai;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * 
 */
public class WargusUnitTypeTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public WargusUnitTypeTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( WargusUnitTypeTest.class );
    }

    /**
     *
     */
    public void testGetValue() throws Exception {
        WargusUnitType type = WargusUnitType.getType("unit-footman");
        assertNotNull(type);
        assertEquals(WargusUnitType.FOOTMAN, type);
    }
}
