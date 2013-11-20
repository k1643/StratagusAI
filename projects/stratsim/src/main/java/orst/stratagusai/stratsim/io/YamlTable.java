package orst.stratagusai.stratsim.io;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * functions to write tabular data as a YAML file.
 *
 */
public class YamlTable {
    private final static String NL = System.getProperty("line.separator");

    public static void write_YAML_table(double[][] data, String[] colhdr,
            String[] rowhdr, String label, String caption, String filepath,
            String comment) throws IOException {
        BufferedWriter doc = new BufferedWriter(new FileWriter(filepath));
        write_YAML_table(data, colhdr, rowhdr, label, caption, doc, comment);
        doc.close();
    }

    public static void write_YAML_table(double[][] data, String[] colhdr,
            String[] rowhdr, String label, String caption, Writer doc,
            String comment) throws IOException {
            write_YAML_table(null, data, colhdr, rowhdr, label, caption, doc, comment);
    }

    public static void write_YAML_table(
            Map<String,Integer> properties,
            double[][] data, String[] colhdr,
            String[] rowhdr, String label, String caption, Writer doc,
            String comment) throws IOException {
        // - label:
        //   matrix:
        //   - [1, 2, 3]
        //   - [4, 5, 6]
        //
        if (comment != null) {
            doc.write("#");
            doc.write(comment);
            doc.write(NL);
        }
        doc.write("- caption: \"" + caption + "\"" + NL);
        // write properties.
        if (properties != null) {
            for (Map.Entry<String,Integer> entry : properties.entrySet()) {
                doc.write(String.format("  %s: %d%s", entry.getKey(),
                                                      entry.getValue(),
                                                      NL));
            }
        }
        doc.write("  label: \"" + label + "\"" + NL);
        doc.write("  colhdr: [");
        for (int j = 0; j < colhdr.length; j++) {
            if (j > 0) {
                doc.write(',');
            }
            doc.write('"' + colhdr[j] + '"');
        }
        doc.write("]");
        doc.write(NL);
        doc.write("  rowhdr: [");
        for (int i = 0; i < rowhdr.length; i++) {
            if (i > 0) {
                doc.write(',');
            }
            doc.write('"' + rowhdr[i] + '"');
        }
        doc.write("]");
        doc.write(NL);
        doc.write("  matrix:");
        doc.write(NL);
        for (int i = 0; i < data.length; i++) {
            doc.write("  - [");
            for (int j = 0; j < data[i].length; j++) {
                if (j > 0) {
                    doc.write(",");
                }
                double x = data[i][j];
                if (x != 0) {
                    doc.write(String.format("%.0f ", x));
                } else if (x == 0) {
                    doc.write("0 ");
                } else // None
                {
                    doc.write(",None ");
                }
            }
            doc.write("]");
            doc.write(NL);
        }
    }

    public static void write_YAML_table(String[][] data, String[] colhdr,
            String[] rowhdr, String label, String caption, String filepath,
            String comment) throws IOException {
        BufferedWriter doc = new BufferedWriter(new FileWriter(filepath));
        write_YAML_table(data, colhdr, rowhdr, label, caption, doc, comment);
        doc.close();
    }

    public static void write_YAML_table(String[][] data, String[] colhdr, 
            String[] rowhdr, String label, String caption, Writer doc,
            String comment) throws IOException {
        // - label:
        //   matrix:
        //   - [1, 2, 3]
        //   - [4, 5, 6]
        //
        if (comment != null) {
            doc.write("#");
            doc.write(comment);
            doc.write(NL);
        }
        doc.write("- caption: \"" + caption + "\"" + NL);
        doc.write("  label: \"" + label + "\"" + NL);
        doc.write("  colhdr: [");
        for (int j = 0; j < colhdr.length; j++) {
            if (j > 0) {
                doc.write(',');
            }
            doc.write('"' + colhdr[j] + '"');
        }
        doc.write("]");
        doc.write(NL);
        doc.write("  rowhdr: [");
        for (int i = 0; i < rowhdr.length; i++) {
            if (i > 0) {
                doc.write(',');
            }
            doc.write('"' + rowhdr[i] + '"');
        }
        doc.write("]");
        doc.write(NL);
        doc.write("  matrix:\n");
        for (int i = 0; i < data.length; i++) {
            doc.write("  - [");
            for (int j = 0; j < data[i].length; j++) {
                if (j > 0) {
                    doc.write(",");
                }
                doc.write('"');
                doc.write(data[i][j]);
                doc.write("\" ");
            }
            doc.write("]");
            doc.write(NL);
        }
        doc.close();
    }
}
