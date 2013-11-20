package orst.stratagusai.stratplan.persist;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import orst.stratagusai.Player;
import orst.stratagusai.TileType;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitType;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.MapNode;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.PassageNode;
import orst.stratagusai.stratplan.model.Rectangle;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.model.RegionNode;

/**
 * Write map in SVG format.
 * 
 * @author Brian
 */
public class GameMapSVG {

    protected final static String NL = System.getProperty("line.separator");
    /** scale */
    protected final static int S = 10;

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("g", true, "show grid lines");  // option, requires argument
        options.addOption("u", true, "draw units");
        options.addOption("t", true, "color by tile types");
        options.addOption("m", true, "map");
        options.addOption("h", false, "help");

        Parser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(GameMapSVG.class.getName(), options);
            System.exit(-1);
        }


        String[] mapfiles = new String[]{
            "one-way-map.txt",
            "the-right-strategy-map.txt",
        };
        if (cmd.hasOption("m")) {
            mapfiles = new String[]{
                args[0]
            };           
        }
        boolean drawUnits = true;
        boolean showGridLines = true;
        boolean colorByTileType = true;
        if (cmd.hasOption("g")) {
            showGridLines = Boolean.valueOf(cmd.getOptionValue("g"));
        }
        if (cmd.hasOption("u")) {
            drawUnits = Boolean.valueOf(cmd.getOptionValue("u"));
        }
        if (cmd.hasOption("t")) {
            colorByTileType = Boolean.valueOf(cmd.getOptionValue("t"));
        }
        for (String filename : mapfiles) {
            // load from resource file.
            InputStream is = ClassLoader.getSystemResourceAsStream(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            GameMap map = GameMapReader.load(br);
            GameState s = new GameState();
            s.setMap(map);
            String outname = filename.replace(".txt", ".svg");
            PrintWriter out = new PrintWriter(new FileWriter(outname));
            write(out, s, drawUnits, showGridLines, colorByTileType);
            out.close();
        }
    }

    public static void write(Writer out, GameState state, boolean drawUnits, boolean showGridLines, boolean colorByTileType) throws IOException {
        GameMap map = state.getMap();

        out.write(String.format("<svg width=\"%d\" height=\"%d\" xmlns=\"http://www.w3.org/2000/svg\">\n", S * map.getExtentX(), S * map.getExtentY()));
        out.write(" <g>\n");
        out.write("  <title>Abstract Map</title>\n");
        write(out, map, showGridLines, colorByTileType);
        if (drawUnits) {
            writeUnits(out, state);
        }
        out.write(" </g>\n");
        out.write("</svg>\n");
    }

    public static void write(Writer out, GameMap map, boolean showGridLines, boolean colorByTileType) throws IOException {

        char[][] cells = map.getCells();
        if (cells != null && cells.length > 0) {
            for (int i = 0; i < cells.length; i++) {
                for (int j = 0; j < cells[i].length; j++) {
                    char t = cells[i][j];
                    String color = "#FFFFFF";
                    if (colorByTileType) {
                        if (t == TileType.COAST) {
                            color = "#A52A2A";
                        } else if (t == TileType.FOREST) {
                            color = "#006600";
                        } else if (t == TileType.HUMAN_WALL) {
                            color = "#AAAAAA";
                        } else if (t == TileType.ORC_WALL) {
                            color = "#AAAAAA";
                        } else if (t == TileType.OTHER) { // traversable
                            color = "#339900";
                        } else if (t == TileType.ROCK) {
                            color = "#666666";
                        } else if (t == TileType.WALL) {
                            color = "#AAAAAA";
                        } else if (t == TileType.WATER) {
                            color = "#0000AA";
                        }
                    } else {
                        color = "#888888";
                    }
                    out.write(String.format("  <rect fill='%s' x='%d' y='%d' width='%d' height='%d'/>\n", color, S * j, S * i, S, S));
                    if (j > 0 && showGridLines) {
                        // column lines
                        if (j % 10 == 0) {
                            out.write(String.format("  <line x1='%d' y1='0' x2='%d' y2='640' style='stroke-dasharray: 9, 5; stroke: black'/>\n",
                                    S * j, S * j));
                        } else {
                            out.write(String.format("  <line x1='%d' y1='0' x2='%d' y2='640' stroke='black'/>\n",
                                    S * j, S * j));
                        }
                    }
                }
                if (i > 0 && showGridLines) {
                    // row lines
                    if (i % 10 == 0) {
                        out.write(String.format("  <line x1='0' y1='%d' x2='640' y2='%d' style='stroke-dasharray: 9, 5; stroke: black' fill='none'/>\n",
                                S * i, S * i));
                    } else {
                        out.write(String.format("  <line x1='0' y1='%d' x2='640' y2='%d' stroke='#000000' fill='none'/>\n",
                                S * i, S * i));
                    }
                }
            }
        }

        // draw region boundaries.
        for (Region region : map.getRegions()) {
            List<Rectangle> recs = region.getRectangles();
            for (int i = 0; i < recs.size(); i++) {
                Rectangle r = recs.get(i);
                String style;
                if (region.isChokepoint()) {
                    style = "fill:red;opacity:0.4";
                } else {
                    style = "fill:none;stroke:white";
                }
                int x = r.getMinX();
                int y = r.getMinY();
                int width = r.getExtentX();
                int height = r.getExtentY();
                out.write(String.format("  <rect x='%d' y='%d' width='%d' height='%d' style='%s' />\n", S * x, S * y, S * width, S * height, style));
                if (i == 0) {
                    // region label
                    out.write(String.format("  <text xml:space='preserve' text-anchor='middle' font-size='18' x='%d' y='%d' fill='#FFFFFF'>", S*x+20, S*y+30));
                    out.write("R" + region.getId());
                    out.write("</text>\n");
                }

            }
            // center
            out.write(String.format("  <rect fill='#FF00AA' stroke='#FF00AA' x='%d' y='%d' width='%d' height='%d'/>\n", S * region.getX(), S * region.getY(), S, S));
        }
        // draw passages (connectivity graph)
        Set<Passage> ps = map.getPassages();
        if (!ps.isEmpty()) {
            for (Passage p : map.getPassages()) {
                Region r = p.getRegionNode().getRegion();
                PassageNode pn = p.getPassageNode();
                out.write(String.format("  <line x1='%d' y1='%d' x2='%d' y2='%d' stroke='#0000CC' fill='none'/>\n",
                        S * r.getX() + 5, S * r.getY() + 5, S * pn.getX() + 5, S * pn.getY() + 5));
            }
            // draw map nodes (connectivity graph nodes)
            for (MapNode n : map.getMapNodes()) {
                if (n instanceof RegionNode) {
                    Region r = ((RegionNode) n).getRegion();
                    int x = r.getX();
                    int y = r.getY();
                    out.write(String.format("  <rect fill='#FF00AA' stroke='#000000' x='%d' y='%d' width='%d' height='%d'/>\n", S * x, S * y, S, S));
                } else {
                    int x = ((PassageNode) n).getX();
                    int y = ((PassageNode) n).getY();
                    out.write(String.format("  <rect fill='#AA00FF' stroke='#000000' x='%d' y='%d' width='%d' height='%d'/>\n", S * x, S * y, S, S));
                }
            }
        }

        // write coordinates
        if (cells != null && cells.length > 0) {
            for (int i = 0; i < cells.length; i += 10) {
                out.write(String.format("  <text xml:space='preserve' text-anchor='middle' font-size='12' x='9' y='%d' fill='#FFFFFF'>", S * i + 10));
                out.write(String.valueOf(i));
                out.write("</text>\n");
                if (i > 0) {
                    out.write(String.format("  <text xml:space='preserve' text-anchor='middle' font-size='12' x='630' y='%d' fill='#FFFFFF'>", S * i + 10));
                    out.write(String.valueOf(i));
                    out.write("</text>\n");
                }
            }
            for (int j = 10; j < cells[0].length; j += 10) {
                out.write(String.format("  <text xml:space='preserve' text-anchor='middle' font-size='12' x='%d' y='9' fill='#FFFFFF'>", S * j + 10));
                out.write(String.valueOf(j));
                out.write("</text>\n");
                out.write(String.format("  <text xml:space='preserve' text-anchor='middle' font-size='12' x='%d' y='630' fill='#FFFFFF'>", S * j + 10));
                out.write(String.valueOf(j));
                out.write("</text>\n");
            }
        }

    }

    /** */
    private static void writeUnits(Writer out, GameState state) throws IOException {
        final int N = state.getPlayers().size();
        for (int i = 0; i < N; i++) {
            Player player = state.getPlayer(i);
            if (player != null && i != GameState.NEUTRAL_PLAYER) {
                for (Unit u : state.getUnits(i).values()) {
                    String color;
                    if (i == 0) {
                        color = "#FF0000";
                    } else if (i == 1) {
                        color = "#0000FF";
                    } else {
                        color = "#00FF00";
                    }
                    int x = u.getLocX();
                    int y = u.getLocY();
                    int width = u.getExtentX();
                    int height = u.getExtentY();
                    out.write(String.format("  <rect fill='%s' stroke='black' x='%d' y='%d' width='%d' height='%d'/>\n", color, S * x, S * y, S * width, S * height));
                }
            }
        }
        for (Unit u : state.getNeutralUnits().values()) {
            String color = "#FFFFFF";
            if (WargusUnitType.GOLD_MINE.getCode() == u.getType()) {
                color = "#FFD700";
            } else if (WargusUnitType.OIL_PATCH.getCode() == u.getType()) {
                color = "#000000";
            }
            int x = u.getLocX();
            int y = u.getLocY();
            int width = 1;
            int height = 1;
            out.write(String.format("  <rect fill='%s' stroke='black' x='%d' y='%d' width='%d' height='%d'/> <!-- %d %s -->\n", color, S * x, S * y, S * width, S * height, u.getType(), u.getUnitTypeString()));
        }
    }
}
