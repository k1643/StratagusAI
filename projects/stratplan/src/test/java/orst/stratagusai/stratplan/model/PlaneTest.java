package orst.stratagusai.stratplan.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import orst.stratagusai.stratplan.persist.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.Unit;

/**
 * 
 */
public class PlaneTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public PlaneTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( PlaneTest.class );
    }

    /**
     * 
     */
    public void testInRange() throws Exception {
        // create units around a central unit.
        // get units in range.
        Plane plane = new Plane(20,20);
        final int x = 10;
        final int y = 10;
        int[][] locations = {
            {x+2,y}, // in range
            {x,  y+3},
            {x+1,y},
            {x+5,y}, // out of range
            {x,  y+5}
        };

        int id = 0;
        Unit ctr = new Unit(id++);
        ctr.setLoc(x, y);
        plane.add(ctr);
        for (int i = 0; i < locations.length; i++) {
            Unit unit = new Unit(id++);
            unit.setLoc(locations[i][0], locations[i][1]);
            plane.add(unit);
        }

        Set<Unit> us = plane.getInRange(ctr, 4);
        Map<Integer,Unit> units = new HashMap<Integer,Unit>();
        for (Unit u : us) {
            units.put(u.getUnitId(), u);
        }
        assertNotNull(units.get(1));
        assertNotNull(units.get(2));
        assertNotNull(units.get(3));
        assertNull(units.get(4));
        assertNull(units.get(5));
    }
}
