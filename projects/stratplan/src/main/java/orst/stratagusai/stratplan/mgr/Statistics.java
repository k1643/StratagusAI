package orst.stratagusai.stratplan.mgr;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Planning statistics
 * 
 * @author Brian
 */
public class Statistics {
    protected final static String NL = System.getProperty("line.separator");
    private String filename = "statistics.csv";

    private List<String> columns = new ArrayList<String>();

    /** current row */
    private List<Object> row = new ArrayList<Object>();

    public Statistics(String filename) {
        this.filename = filename;
    }
    
    public void setColumns(String...columns) {
        for(String col : columns) {
            this.columns.add(col);
            row.add(null);
        }
    }

    public void setColumns(List<String> columns) {
        for(String col : columns) {
            this.columns.add(col);
            row.add(null);
        }
    }

    public void setValue(String col, int value) {
        int i = columns.indexOf(col);
        row.set(i, value);
    }

    public void setValue(String col, String value) {
        int i = columns.indexOf(col);
        row.set(i, value);
    }

    public void setValue(int col, String value) {
        row.set(col, value);
    }

    public void setValue(int col, int value) {
        row.set(col, value);
    }

    public void setValue (int col, double value) {
        row.set(col, value);
    }

    public void nextRow() {
        writeRow();
        row.clear();
        for (String c : columns) {
            row.add(null);
        }
    }

    /** append row to statistics CSV file */
    protected void writeRow() {
        try {
            FileWriter out = new FileWriter(filename, true);
            for (int i = 0; i < row.size(); i++) {
                Object o = row.get(i);
                if (i > 0) {
                    out.write(",");
                }
                if (o instanceof String) {
                    out.write("\"");
                }
                out.write(String.valueOf(o));
                if (o instanceof String) {
                    out.write("\"");
                }
            }
            out.write(NL);
            out.close();
        } catch (IOException e) {
           throw new RuntimeException(e); 
        }
    }
}
