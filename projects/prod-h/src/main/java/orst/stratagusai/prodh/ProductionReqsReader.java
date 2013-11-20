package orst.stratagusai.prodh;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Brian
 */
public class ProductionReqsReader {
    public static ProductionReqs load(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        ProductionReqs conf = load(in);
        in.close();
        return conf;
    }

    public static ProductionReqs load(Reader in) {
        Yaml yaml = new Yaml();
        return (ProductionReqs) yaml.load(in);
    }
}
