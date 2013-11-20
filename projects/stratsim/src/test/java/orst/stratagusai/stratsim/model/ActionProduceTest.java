package orst.stratagusai.stratsim.model;

import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratsim.analysis.BuildState;

/**
 * Unit test.
 */
public class ActionProduceTest
    extends TestCase
{
    private static final Logger log = Logger.getLogger(ActionProduceTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ActionProduceTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ActionProduceTest.class );
    }

    /**
     *
     */
    public void testProduce() throws Exception
    {
        // make peasant in region 1 produce a town hall.
        /*
        GameState game = TestGame.getOneWayInOneWayOut();
        game = GameStateAbstractor.getAbstractState(game);
        Region r = game.getMap().getRegion(1);
        BuildState s = getBuildState(0, game, r);
        assertEquals(1, s.getCount("unit-peasant"));
        GroupSim group = (GroupSim) game.getUnit(2);
        assertNotNull(group);
/*        assertEquals(0, group.getOwnerId());

        List<Argument> unitReqs = new ArrayList<Argument>();
        unitReqs.add(new Argument("unit-town-hall", 1));

        // (Player owner, Region region, UnitType targetType, int targetAmount)
        ActionProduce a = new ActionProduce();
        a.setUnitID(group.getUnitId());
        a.setTargets(s, unitReqs); */

    }

    private BuildState getBuildState(int playerId, GameState game, Region r) {
        BuildState state = new BuildState();

        Set<Unit> units = game.getUnits(r);
        for (Unit u : units) {
            if (u.getOwnerId() != playerId) {
                continue;
            }
            if (u instanceof GroupSim) {
                GroupSim g = (GroupSim) u;
                for (Map.Entry<String,Integer> entry : g.getUnitTypes().entrySet()) {
                    state.increment(entry.getKey(), entry.getValue());
                }
            } else {
                state.increment(u.getUnitTypeString());
            }
        }
        return state;
    }
}
