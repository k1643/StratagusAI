package orst.stratagusai.stratplan.persist;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.Region;

/**
 * 
 * Unit test for simple App.
 */
public class GameMapReaderTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GameMapReaderTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GameMapReaderTest.class );
    }

    /**
     * 
     */
    public void testRead() throws Exception {
        InputStream is =
                ClassLoader.getSystemResourceAsStream("one-way-map.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        GameMap map = GameMapReader.load(br);
        assertNotNull(map);
        Set<Region> rs = map.getRegions();
        assertEquals(6, rs.size());
    }
}
