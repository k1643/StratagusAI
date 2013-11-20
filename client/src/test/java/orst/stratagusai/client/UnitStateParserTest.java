package orst.stratagusai.client;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CommonTokenStream;

/**
 * Unit test for simple App.
 */
public class UnitStateParserTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public UnitStateParserTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( UnitStateParserTest.class );
    }

    /**
     * 
     */
    public void testParse() throws Exception
    {
        UnitStateLexer lex =
                new UnitStateLexer(new ANTLRFileStream("data/unit_state.txt"));
       	CommonTokenStream tokens = new CommonTokenStream(lex);

        UnitStateParser parser = new UnitStateParser(tokens);

        // we have to look for printed error messages.  don't know how
        // to test for parse errors through the API.
        //
        UnitStateParser.unit_state_return r = parser.unit_state();


    }
}
