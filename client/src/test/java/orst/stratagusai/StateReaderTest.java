package orst.stratagusai;

//import java.util.Map;
import java.util.Map;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class StateReaderTest
    extends TestCase
{
//    /**
//     * Create the test case
//     *
//     * @param testName name of the test case
//     */
    public StateReaderTest( String testName )
    {
        super( testName );
    }
//
//    /**
//     * @return the suite of tests being tested
//     */
    public static Test suite()
    {
        return new TestSuite( StateReaderTest.class );
    }

    /**
     *
     */
    public void testParseUnitState() throws Exception {
        // Send LISPGET GAMEINFO
        String response = "#s(gameinfo player-id 0 width 32 length 32)";
        GameProxy game = new GameProxy();
        game.parseGameInfo(response);  // set player-id

        // Send LISPGET STATE
        response =
           "((" +
           "( 0 . #s(unit player-id 0 type 23 loc (6 4) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 1 . #s(unit player-id 0 type 23 loc (5 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 2 . #s(unit player-id 0 type 23 loc (5 5) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 3 . #s(unit player-id 0 type 23 loc (3 2) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 4 . #s(unit player-id 0 type 23 loc (3 4) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 5 . #s(unit player-id 1 type 23 loc (11 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 6 . #s(unit player-id 1 type 23 loc (12 5) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 7 . #s(unit player-id 1 type 23 loc (15 1) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 8 . #s(unit player-id 1 type 23 loc (15 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 9 . #s(unit player-id 1 type 23 loc (15 5) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ())))"+
           " (:player :id 0 :gold 2000 :oil 2000 :supply 1000 :demand 1000 :wood 1000))";

        game.parseUnitStates(response);

        Map<Integer,Unit> allies = game.getUnits(0);
        Map<Integer,Unit> enemies = game.getEnemyUnits(0);

        assertEquals(5, allies.size());
        assertEquals(5, enemies.size());
    }

    /**
     * In stratagus 2.2.4 we know units are dead when we don't get
     * updates for them.
     */
    public void testUpdateDead() throws Exception {
        // Send LISPGET GAMEINFO
        String response = "#s(gameinfo player-id 0 width 32 length 32)";
        GameProxy game = new GameProxy();
        game.parseGameInfo(response);  // set player-id

        // Send LISPGET STATE
        response =
           "((" +
           "( 0 . #s(unit player-id 0 type 23 loc (6 4) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 1 . #s(unit player-id 0 type 23 loc (5 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 2 . #s(unit player-id 1 type 23 loc (11 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 3 . #s(unit player-id 1 type 23 loc (12 5) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ())))" +
           " (:player :id 0 :gold 2000 :oil 2000 :supply 1000 :demand 1000 :wood 1000))";
        game.parseUnitStates(response);

        Map<Integer,Unit> allies = game.getUnits(0);
        Map<Integer,Unit> enemies = game.getEnemyUnits(0);

        assertEquals(2, allies.size());
        assertEquals(2, enemies.size());

        // Send LISPGET STATE
        response =
           "((" +
           "( 0 . #s(unit player-id 0 type 23 loc (6 4) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 1 . #s(unit player-id 0 type 23 loc (5 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 2 . #s(unit player-id 1 type 23 loc (11 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ())))" +
           "  (:player :id 0 :gold 2000 :oil 2000 :supply 1000 :demand 1000 :wood 1000))";
        game.parseUnitStates(response);

        // unit three not updated, so it should be dead.
        Unit unit3 = enemies.get(3);
        assertNotNull(unit3);
        assertTrue(unit3.isDead());
    }

    public void testEventParsing() throws Exception {

        GameProxy game = new GameProxy();
        
        // Send LISPGET STATE
        String response =
           "((" +
           "( 0 . #s(unit player-id 0 type 23 loc (6 4) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 1 . #s(unit player-id 0 type 23 loc (5 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 2 . #s(unit player-id 1 type 23 loc (11 3) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ()))" +
           "( 3 . #s(unit player-id 1 type 23 loc (12 5) hp 60 r-amt 0 kills 0 armor 0 dmg 0 piercing-dmg 0 status 1 status-args ())))" +
           " (:player :id 0 :gold 2000 :oil 2000 :supply 1000 :demand 1000 :wood 1000)" +
           " (:events (died 4))" +
           ")";
        game.parseUnitStates(response);
        UnitEvent evt = game.getEvents().get(0);
        assertEquals(UnitEventType.DIED, evt.getType());
        assertEquals(4, evt.getUnitId());
    }
}
