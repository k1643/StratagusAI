package orst.stratagusai.stratsim.io;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.yaml.snakeyaml.Yaml;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;

/**
 *
 * @author Brian
 */
public class ProductionEstimationReader {
    public static ProductionEstimation load(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        ProductionEstimation conf = load(in);
        in.close();
        return conf;
    }

    public static ProductionEstimation load(Reader in) {
        Yaml yaml = new Yaml();
        return (ProductionEstimation) yaml.load(in);
    }
}
