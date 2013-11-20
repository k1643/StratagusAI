package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;
import orst.stratagusai.stratplan.model.PassageNode;

/**
 *
 * @author Brian
 */
public class PassageNodeWriter implements ObjectWriter {

    public void write(Writer out, String indent, Object o) throws IOException {
        PassageNode n = (PassageNode) o;
        out.write(String.format("(:PassageNode :id %d (%d %d))", n.getId(), n.getX(), n.getY()));
    }

}
