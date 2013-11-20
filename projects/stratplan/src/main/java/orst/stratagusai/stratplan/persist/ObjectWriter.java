package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;

/**
 *
 * @author Brian
 */
public interface ObjectWriter {
    void write(Writer out, String indent, Object o) throws IOException;
}
