package orst.stratagusai.stratsim.io;

import java.io.FileWriter;
import java.io.IOException;
import org.yaml.snakeyaml.Dumper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;
import orst.stratagusai.stratsim.analysis.ProductionEstimation;

/**
 *
 * @author Brian
 */
public class ProductionEstimationWriter {
    public static String dump(ProductionEstimation map) {
        Yaml yaml = new Yaml(new Dumper(new DumperOptions()));
        String output = yaml.dump(map);
        return output;
    }

    public static void dump(ProductionEstimation map, String filename) throws IOException {
        FileWriter out = new FileWriter(filename);
        DumperOptions options = new DumperOptions();
        options.setAllowReadOnlyProperties(true); 
        Yaml yaml = new Yaml(new Dumper(new Representer(), options));
        //JavaBeanDumper yaml = new JavaBeanDumper(new Representer(), options);
        
        yaml.dump(map, out);
        out.close();
    }
}
