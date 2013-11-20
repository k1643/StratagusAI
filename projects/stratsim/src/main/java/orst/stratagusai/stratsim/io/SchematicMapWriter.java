package orst.stratagusai.stratsim.io;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;

/**
 * Write a compressed version of the map.
 * 
 * @author Brian
 */
public class SchematicMapWriter {
    private static final Logger log = Logger.getLogger(SchematicMapWriter.class);

    public static void write(GameState s, String filename) throws IOException {
        write(s, filename, false);
    }

    public static void write(GameState s, String filename, boolean append) throws IOException {
        Writer out = new FileWriter(filename, append);
        write(s, out);
        out.close();
    }

    public static void write(GameState s, Writer out) throws IOException {
        GameMap map = s.getMap();
        int extentX = map.getExtentX();
        int extentY = map.getExtentY();
        int ratio = 5;
        int cols = (int) Math.ceil(extentX / (float) ratio);  // 5 tiles per compressed tile
        int rows = (int) Math.ceil(extentY / (float) ratio);
        char[][] cells = map.getCells();
        char[][] compressed = new char[rows][cols];
        // write tiles
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                compressed[row][col] = compress(cells, row, col, ratio);
            }
        }
        // write units
        for (Region r : map.getRegions()) {
            int x = r.getX()/ratio;
            int y = r.getY()/ratio;
            for (Unit u : s.getUnits(r)) {
                out.write(u.toString());
                out.write('\n');
                if (u.getOwnerId() == 0) {
                    compressed[y][x] = '0';
                    x++;
                } else if (u.getOwnerId() == 1) {
                    compressed[y][x] = '1';
                    x++;
                }
                if (x >= compressed.length) {
                    x = r.getX()/ratio;
                    y++;
                }
                if (y >= compressed[0].length) {
                    break;  // leave some out.
                }
            }
        }
        for (int row = 0; row < rows; row++) {
            out.write(compressed[row]);
            out.write('\n');
        }
    }

    private static char compress(char[][] cells, int row, int col, int ratio) {
        // return the majority character
        Map<Character,Integer> counts = new LinkedHashMap<Character,Integer>();
        int startRow = row*ratio;
        int startCol = col*ratio;
        for (int i = startRow; i < startRow+ratio && i < cells.length; i++) {
            for (int j = startCol; j < startCol+ratio && j < cells[i].length; j++) {
                if (counts.containsKey(cells[i][j])) {
                    int count = counts.get(cells[i][j]);
                    counts.put(cells[i][j], count+1);
                } else {
                    counts.put(cells[i][j], 1);
                }
            }
        }
        char c = '.';
        int count = 0;
        for (Map.Entry<Character,Integer> entry : counts.entrySet()) {
            if (entry.getValue() > count) {
                c = entry.getKey();
                count = entry.getValue();
            }
        }
        return c == '0' ? ' ' : c;
    }
}
