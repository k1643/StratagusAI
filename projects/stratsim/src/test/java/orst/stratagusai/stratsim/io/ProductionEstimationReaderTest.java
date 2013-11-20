package orst.stratagusai.stratsim.io;

import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;
import orst.stratagusai.stratsim.model.BuildRequirements;

/**
 *
 */
public class ProductionEstimationReaderTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ProductionEstimationReaderTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ProductionEstimationReaderTest.class );
    }

    /**
     * 
     */
    public void testRead() throws Exception {
        ProductionEstimation est = ProductionEstimationReader.load("data/estimation.yaml");
        Map<String,BuildRequirements> reqs = est.getRequirements();
        assertEquals(1, reqs.size());

        BuildRequirements req = reqs.get("unit-human-oil-tanker");
        assertNotNull(req);
        Set<BuildRequirements> pres = req.getPreconditions();
        assertEquals(1, pres.size());
        BuildRequirements pre = pres.iterator().next();
        assertEquals("unit-human-shipyard", pre.getType());
    }
}
