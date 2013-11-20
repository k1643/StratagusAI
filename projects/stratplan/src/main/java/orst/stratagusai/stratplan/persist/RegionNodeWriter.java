package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;
import orst.stratagusai.stratplan.model.RegionNode;

/**
 *
 * @author Brian
 */
public class RegionNodeWriter implements ObjectWriter {

    public void write(Writer out, String indent, Object o) throws IOException {
        RegionNode n = (RegionNode) o;
        out.write("(:RegionNode");
        out.write(" :region " + n.getRegion().getId());
        out.write(")");
    }

}
