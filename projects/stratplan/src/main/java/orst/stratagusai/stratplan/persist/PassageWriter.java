package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;
import orst.stratagusai.stratplan.model.Passage;

/**
 *
 * @author Brian
 */
public class PassageWriter implements ObjectWriter {

    public void write(Writer out, String indent, Object o) throws IOException {
        Passage p = (Passage) o;
        out.write("(:Passage");
        out.write(" :regionNode " + p.getRegionNode().getId());
        out.write(" :passageNode " + p.getPassageNode().getId());
        out.write(")");
    }

}
