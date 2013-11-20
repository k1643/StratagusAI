package orst.stratagusai.prodh;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class ProductionReqsWriterTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ProductionReqsWriterTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ProductionReqsWriterTest.class );
    }

    /**
     * 
     */
    public void testWrite() throws Exception {
        ProductionReqs reqs = ProductionReqs.getProductionReqs();
        ProductionReqsWriter.dump(reqs, "data/reqs.yaml");

        // OK, it didn't crash
    }
}
