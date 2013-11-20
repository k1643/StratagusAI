package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.MapNode;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.PassageNode;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.model.RegionNode;

/**
 *
 * @author Brian
 */
public class GameMapWriter implements ObjectWriter {
    protected final static String NL = System.getProperty("line.separator");
    protected Map<Class,ObjectWriter> writers;

    public GameMapWriter() {
        writers = new LinkedHashMap<Class,ObjectWriter>();
        writers.put(Region.class, new RegionWriter());
        writers.put(Passage.class, new PassageWriter());
        writers.put(RegionNode.class, new RegionNodeWriter());
        writers.put(PassageNode.class, new PassageNodeWriter());
    }
    
    public GameMapWriter(Map<Class,ObjectWriter> writers) {
        this.writers = writers;
    }

    public void write(Writer out, GameMap map) throws IOException {
        write(out,"",map);
    }

    public void write(Writer out, String indent, Object o) throws IOException {
        GameMap map = (GameMap) o;
        out.write(indent);
        out.write("(:GameMap");
        out.write(NL);
        // cells
        char[][] cells = map.getCells();
        if (cells != null && cells.length > 0) {
            out.write(indent);
            out.write(indent);
            out.write("(:cells");
            out.write(NL);
            for (int i = 0; i < cells.length; i++) {
                out.write(indent);
                out.write(indent);
                out.write(indent);
                out.write("'");
                for (int j = 0; j < cells[i].length; j++) {
                    out.write(cells[i][j]);
                }
                out.write("'");
                out.write(NL);
            }
            out.write(indent);
            out.write(indent);
            out.write(")");
            out.write(NL);
        }
        ObjectWriter wr = writers.get(Region.class);
        for (Region r : map.getRegions()) {
            out.write(indent);
            out.write(indent);
            wr.write(out, indent, r);
            out.write(NL);
        }
        Set<Passage> ps = map.getPassages(); 
        if (!ps.isEmpty()) {
            ObjectWriter pr = writers.get(Passage.class);
            out.write(indent);
            out.write(indent);
            out.write("(:connections");
            out.write(NL);
            ObjectWriter pnr = writers.get(PassageNode.class);
            for (MapNode n : map.getMapNodes()) {
                if (n instanceof PassageNode) {
                    out.write(indent);
                    out.write(indent);
                    out.write(indent);
                    pnr.write(out, indent, n);
                    out.write(NL);
                }
            }
            for (Passage p : map.getPassages()) {
                out.write(indent);
                out.write(indent);
                out.write(indent);
                pr.write(out, indent, p);
                out.write(NL);
            }
            out.write(indent);
            out.write(indent);
            out.write(")");
            out.write(NL);
        }
        out.write(indent);
        out.write(")");
    }

}
