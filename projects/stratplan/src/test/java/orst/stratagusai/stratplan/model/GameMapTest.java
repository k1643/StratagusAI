package orst.stratagusai.stratplan.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import orst.stratagusai.stratplan.persist.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class GameMapTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GameMapTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GameMapTest.class );
    }

    /**
     * 
     */
    public void testRead() throws Exception {
        GameMap map = getOneWayMap();

        Region r1 = map.getRegion(1);
        Region r7 = map.getRegion(7);
        assertNotNull(r1);
        assertNotNull(r7);
        assertNotNull(r1.getNode());
        assertNotNull(r7.getNode());
        List<Passage> path = map.getShortestPath(r1, r7);
        assertEquals(8, path.size());

        int[] regions = {1, 2, 2, 3, 3, 8, 8, 7};

        // verify edges of path
        for (int i = 0; i < regions.length; i++) {
            Passage p = path.get(i);
            assertEquals(regions[i], p.getRegionNode().getId());
        }
    }

    public GameMap getOneWayMap() throws IOException {
         // load from resource file.
        InputStream is =
                ClassLoader.getSystemResourceAsStream("one-way-map.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        GameMap m = GameMapReader.load(br);
        return m;
    }
}
