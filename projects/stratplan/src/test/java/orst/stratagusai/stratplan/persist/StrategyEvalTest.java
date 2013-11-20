package orst.stratagusai.stratplan.persist;

import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.PlanEval;
import orst.stratagusai.stratplan.PlanLexer;
import orst.stratagusai.stratplan.PlanParser;
import orst.stratagusai.stratplan.Task;

/**
 * 
 */
public class StrategyEvalTest
        extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyEvalTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(StrategyEvalTest.class);
    }


    /**  */
    public void testTopLevel() throws Exception {
        ANTLRFileStream input = new ANTLRFileStream("data/strategy1.strat");
        PlanParser parser = makeParser(input);

        PlanParser.plan_return r = parser.plan();

        CommonTree t = (CommonTree) r.getTree();

        // create a tree node stream from the tree.
        CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
        PlanEval walker = new PlanEval(nodes);
        StrategicPlan strategy = walker.plan();
    }

    public void testTopLevel2() throws Exception {
        String strategyStr =
                "(:plan s1 :player 0 \n" +
                "  (:task task1 () \n" +
                "   :type produce \n" +
                "  ) \n" +
                ")"
        ;
        ANTLRStringStream input = new ANTLRStringStream(strategyStr);
        PlanParser parser = makeParser(input);

        PlanParser.plan_return r = parser.plan();
        assertFalse(parser.failed());

        CommonTree t = (CommonTree) r.getTree();
        CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
        PlanEval walker = new PlanEval(nodes);
        StrategicPlan s = walker.plan();
        assertNotNull(s);
        assertEquals("s1", s.getName());
        Set<Task> tasks = s.getTasks();
        assertEquals(1, tasks.size());
        Task task = tasks.iterator().next();
        assertEquals("task1", task.getName());
        
    }

    public void testRoundTrip() throws Exception {
        ANTLRFileStream input = new ANTLRFileStream("data/strategy1.strat");
        PlanParser parser = makeParser(input);

        PlanParser.plan_return r = parser.plan();

        CommonTree t = (CommonTree) r.getTree();

        // create a tree node stream from the tree.
        CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
        PlanEval walker = new PlanEval(nodes);
        StrategicPlan strategy = walker.plan();

        StrategicPlanWriter.write(strategy, "data/strategy1.strat.out");

    }

    public void testDuplicateTaskName() throws Exception {
        ANTLRFileStream input = new ANTLRFileStream("data/dup_task_name_test.strat");
        PlanParser parser = makeParser(input);

        PlanParser.plan_return r = parser.plan();

        CommonTree t = (CommonTree) r.getTree();

        // create a tree node stream from the tree.
        CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
        PlanEval walker = new PlanEval(nodes);

        try {
            StrategicPlan strategy = walker.plan();
            fail("duplicate name not detected");
        } catch(Exception e) {
            // OK.  duplicate name detected and exception thrown.
        }
    }
    
    private PlanParser makeParser(CharStream input) {
        PlanLexer lex = new PlanLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lex);
        PlanParser parser = new PlanParser(tokens);
        return parser;
    }
}
