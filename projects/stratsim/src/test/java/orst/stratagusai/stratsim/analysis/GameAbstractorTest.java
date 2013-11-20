package orst.stratagusai.stratsim.analysis;

import java.util.ArrayList;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratsim.TestGame;

/**
 * Unit test for simple App.
 */
public class GameAbstractorTest
    extends TestCase
{
    private static final Logger log =
            Logger.getLogger(GameAbstractorTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GameAbstractorTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GameAbstractorTest.class );
    }

    /**
     * 
     */
    public void testGrouping() throws Exception {
        GameState state = TestGame.getOneWayInOneWayOut();

        // add some footmen for each player.
        List<Unit> us = new ArrayList<Unit>();
        WargusUnitPrototypes ufactory = new WargusUnitPrototypes();
        int nextId = 2;
        for (int i = 0; i < 6; i++) {
            int ownerId = i < 3 ? 0 : 1;
            Unit u = ufactory.createUnit("unit-footman", ++nextId, ownerId);
            if (i < 3) {
                u.setLoc(5, 15);
            } else {
                u.setLoc(5, 45);
            }
            us.add(u);
        }
        state.setUnits(us);

        /*
        state = GameStateAbstractor.getAbstractState(state);

        Map<Integer,Unit> allies = state.getUnits(0);
        Map<Integer,Unit> enemies = state.getUnits(1);
        List<Unit> aUnits = state.getUnits();

        // footmen absracted into UnitGroups.
        assertEquals(1, allies.size());
        assertEquals(1, enemies.size());
        assertEquals(2, aUnits.size());
        */

    }
}
