package orst.stratagusai.stratplan.persist;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.PassageNode;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.model.RegionNode;

/**
 *
 * @author Brian
 */
public class GameStateWriter {

    protected final static String INDENT = "  ";
    protected final static String NL = System.getProperty("line.separator");
    protected Map<Class,ObjectWriter> writers = new LinkedHashMap<Class,ObjectWriter>();

    public GameStateWriter() {
        writers.put(GameMap.class, new GameMapWriter(writers));
        writers.put(Region.class, new RegionWriter());
        writers.put(Unit.class, new UnitWriter());
        writers.put(Passage.class, new PassageWriter());
        writers.put(RegionNode.class, new RegionNodeWriter());
        writers.put(PassageNode.class, new PassageNodeWriter());
    }

    public static void write(GameState g, String filename) throws IOException {
        GameStateWriter wr = new GameStateWriter();
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
        for(Player p : g.getPlayers()) {

            // out.write(INDENT + ":cycle " + g.getCycle() + "\n");
            out.write(INDENT);
            out.write("(:player");
            out.write(" :id " + p.getId());
            out.write(" :gold " + p.getGold());
            out.write(" :oil " + p.getOil());
            out.write(" :wood " + p.getWood());
            out.write(" :supply " + p.getSupply());
            out.write(" :demand " + p.getDemand());
            out.write(")");
            out.write(NL);
        }
        GameMap map = g.getMap();
        ObjectWriter mapwr = writers.get(map.getClass());
        mapwr.write(out, INDENT, map);
        out.write(NL);

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
