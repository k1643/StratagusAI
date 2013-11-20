package orst.stratagusai.prodh;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 *
 * @author Brian
 */
public class ProductionReqs {
    /** map unit type to build requirements */
    protected Map<String, ProductionReq> reqs =
            new LinkedHashMap<String, ProductionReq>();

    public Map<String, ProductionReq> getRequirements() {
        return reqs;
    }

    public static ProductionReqs getProductionReqs() {
        ProductionReqs.class.getClassLoader();
        InputStream is =
            ClassLoader.getSystemResourceAsStream("orst/stratagusai/prodh/production_reqs.yaml");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        ProductionReqs est = ProductionReqsReader.load(br);
        return est;
    }

    public void setRequirements(Map<String, ProductionReq> reqs) {
        this.reqs = reqs;
    }

    public void addRequirements(String type, ProductionReq req) {
        reqs.put(type, req);
    }

    /**
     * get the immediate prerequisites of the given type.
     */
    public ProductionReq getRequirements(String type) {
        return reqs.get(type);
    }
}
