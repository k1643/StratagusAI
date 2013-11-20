package orst.stratagusai.stratsim.io;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.ObjectWriter;
import orst.stratagusai.stratplan.persist.UnitWriter;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 * Write game cycle and unit state.
 *
 * @author Brian
 */
public class UnitsGameStateWriter {

    protected final static String INDENT = "  ";
    protected final static String NL = System.getProperty("line.separator");
    protected Map<Class,ObjectWriter> writers = new LinkedHashMap<Class,ObjectWriter>();

    public UnitsGameStateWriter() {
        writers.put(Unit.class, new UnitWriter());
        writers.put(GroupSim.class, new UnitGroupSimWriter());
    }

    public static void write(GameState g, String filename) throws IOException {
        UnitsGameStateWriter wr = new UnitsGameStateWriter();
        FileWriter out = new FileWriter(filename);
        wr.write(out, g);
        out.close();
    }

    /**
     * Add writer for a class.  This allows a module to add subclassed
     * objects to a game state and provide a writer for the subclass.
     */
    public void setObjectWriter(Class c, ObjectWriter wr) {
        writers.put(c, wr);
    }
    
    public void write(Writer out, GameState g) throws IOException {
        out.write("(:GameState :cycle " + g.getCycle() + NL);
        out.write(INDENT);
        out.write("(:units" + NL);
        List<Unit> units = g.getUnits();
        for (Unit u : units) {
            out.write(INDENT);
            out.write(INDENT);
            ObjectWriter unitwr = writers.get(u.getClass());
            unitwr.write(out, INDENT, u);
            out.write(NL);
        }
        out.write(INDENT);
        out.write(")" + NL);
        out.write(")");
        out.write(NL);
    }

    public String write(GameState g) throws IOException {
        StringWriter out = new StringWriter();
        write(out, g);
        return out.toString();
    }
}
