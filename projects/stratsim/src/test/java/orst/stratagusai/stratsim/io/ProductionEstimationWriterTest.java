package orst.stratagusai.stratsim.io;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;
import orst.stratagusai.stratsim.model.BuildRequirements;

/**
 * Unit test for simple App.
 */
public class ProductionEstimationWriterTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ProductionEstimationWriterTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ProductionEstimationWriterTest.class );
    }

    /**
     * 
     */
    public void testWrite() throws Exception {
        ProductionEstimation est = new ProductionEstimation();
        BuildRequirements req = new BuildRequirements();
        req.setType("unit-oil-tanker");
        req.setTime(10);
        est.addRequirements(req.getType(), req);

        BuildRequirements pre = new BuildRequirements();
        pre.setType("unit-shipyard");
        pre.setTime(20);
        req.addPrecondition(pre);
        est.addRequirements(pre.getType(), pre);

         
        ProductionEstimationWriter.dump(est, "data/estimation1.yaml");

        // OK, it didn't crash
    }
}
