package orst.stratagusai.stratplan.persist;

import java.io.StringReader;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.Region;

/**
 * 
 * Unit test for simple App.
 */
public class GameStateReaderTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GameStateReaderTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GameStateReaderTest.class );
    }

    /**
     * 
     */
    public void testChokepoint() throws Exception {
        GameState game1 = GameStateReader.load("data/game1.txt");
        GameMap map1 = game1.getMap();

        // find the chokepoint.  Region 3 is coded as a chokepoint.
        Region r3 = map1.getRegion(3);
        assertTrue(r3.isChokepoint());

        Region r1 = map1.getRegion(1);
        assertFalse(r1.isChokepoint());
    }

    public void testExternalMap() throws Exception {
        GameState game1 = GameStateReader.load("data/game_with_external_map.txt");
        GameMap map1 = game1.getMap();

        // find the chokepoint.  Region 3 is coded as a chokepoint.
        Region r3 = map1.getRegion(3);
        assertTrue(r3.isChokepoint());

        Region r1 = map1.getRegion(1);
        assertFalse(r1.isChokepoint());
    }

    public void testStateActions() throws Exception {
        String stateStr =
            "(:GameState :cycle 0"+
            "(:player :id 0 :gold 40000 :oil 40000 :wood 40000 :supply 58 :demand 2)"+
            "(:GameMap :resource 'one-way-map.txt')"+
            "(:actions (UNIT_MOVE :unitId 1 :x 10 :y 10)) " +
            "(:units"+
            "   (:Unit :unitId 1 :ownerId 0 :RAmount 40000 :HitPoints 25500"+
            "         :LocX 2 :LocY 2"+
            "         :Armor 20 :Damage 3 :PiercingDmg 0"+
            "         :Status 3 :StatusArg1 -2"+
            "         :StatusArg2 0 :Type 11 :UnitTypeString unit-gold-mine)"+
            "))";
        StringReader in = new StringReader(stateStr);
        GameState state = GameStateReader.load(in);

        CommandType type = CommandType.valueOf("UNIT_MOVE");
        System.out.println("type=" + type);
    }
}
