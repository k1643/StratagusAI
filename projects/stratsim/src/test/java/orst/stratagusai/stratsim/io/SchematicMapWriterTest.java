package orst.stratagusai.stratsim.io;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.GameStateReader;
import orst.stratagusai.stratsim.TestGame;


/**
 * Unit test for simple App.
 */
public class SchematicMapWriterTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SchematicMapWriterTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( SchematicMapWriterTest.class );
    }

    /**
     * 
     */
    public void testWrite() throws Exception {
        
        GameState s = TestGame.getOneWayInOneWayOut();
        SchematicMapWriter.write(s, "data/schematic_map.txt");
    }
}
