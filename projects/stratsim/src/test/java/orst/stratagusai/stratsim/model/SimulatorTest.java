package orst.stratagusai.stratsim.model;

import java.util.HashSet;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.command.UnitCommand;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratsim.TestGame;

/**
 * 
 */
public class SimulatorTest
    extends TestCase
{
    private static final Logger log = Logger.getLogger(SimulatorTest.class);
    
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SimulatorTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( SimulatorTest.class );
    }

    public void testMotion() throws Exception {
        GameState game = TestGame.getOneWayInOneWayOut();

        Unit peasant = game.getUnit(1);
        assertNotNull(peasant);

        Region r = game.getMap().getRegion(7);

        // peasant 1 attacks region 7
        ActionMove a = new ActionMove(peasant,r);
        Set<UnitCommand> cmds = new HashSet<UnitCommand>();
        cmds.add(a);

        PlayerGroups[] gs = GroupAnalysis.defineGroups(game);
        SimState state = new SimState(gs, game);
        Simulator sim = new Simulator(state);
        for (int i = 0; i < 20; i++) {
            sim.execute(cmds);
            log.debug("Unit at " + state.getMap().getRegion(peasant));
            cmds.clear();
        }

    }
}
