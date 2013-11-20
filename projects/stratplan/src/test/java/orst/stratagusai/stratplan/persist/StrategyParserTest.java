package orst.stratagusai.stratplan.persist;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CommonTokenStream;
import orst.stratagusai.stratplan.PlanLexer;
import orst.stratagusai.stratplan.PlanParser;

/**
 * Unit test for simple App.
 */
public class StrategyParserTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public StrategyParserTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( StrategyParserTest.class );
    }

    /**
     * 
     */
    public void testParse() throws Exception
    {
        PlanLexer lex =
                new PlanLexer(new ANTLRFileStream("data/strategy1.strat"));
       	CommonTokenStream tokens = new CommonTokenStream(lex);

        PlanParser parser = new PlanParser(tokens);

        // we have to look for printed error messages.  don't know how
        // to test for parse errors through the API.
        //
        parser.plan();
    }
}
