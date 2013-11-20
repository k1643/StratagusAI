package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import orst.stratagusai.stratplan.model.Rectangle;
import orst.stratagusai.stratplan.model.Region;

/**
 *
 * @author Brian
 */
public class RegionWriter implements ObjectWriter {

    public void write(Writer out, String indent, Object o) throws IOException {
        Region r = (Region) o;
        out.write("(:Region");
        out.write(" :id " + r.getId());
        out.write(" :center (" + r.getX() + " " + r.getY() + ")");
        List<Rectangle> rects = r.getRectangles();
        for (Rectangle rect : rects) {
            out.write(" (");
            out.write(String.valueOf(rect.getMinX()));
            out.write(" ");
            out.write(String.valueOf(rect.getMinY()));
            out.write(" ");
            out.write(String.valueOf(rect.getMaxX()));
            out.write(" ");
            out.write(String.valueOf(rect.getMaxY()));
            out.write(")");
        }
        out.write(")");
    }

}
