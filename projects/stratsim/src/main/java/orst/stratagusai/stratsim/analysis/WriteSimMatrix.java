package orst.stratagusai.stratsim.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.model.StrategicState;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameStates;
import orst.stratagusai.stratsim.planner.SwitchingPlanner;
import orst.stratagusai.stratsim.planner.StrategyTemplate;
import orst.stratagusai.util.Randomizer;

/**
 * Write simulated strategy matrix values.  Files written:
 *
 *  sim_scores_|strategy_set|.yaml
 *  sim_win_rate_|strategy_set|.yaml
 * 
 *  Plus map-specific files.
 *
 *  Run time: 1 minute 25 seconds.
 */
public class WriteSimMatrix {

    protected static String dir = ".";

    public static void main(String[] args) throws Exception {

        Randomizer.init(14L); // initialize common random seed.
        
        // turn off debug logs
        Logger orstlog = Logger.getLogger("orst");
        Logger msglog = Logger.getLogger("msglog");
        orstlog.setLevel(Level.OFF);
        msglog.setLevel(Level.OFF);

        // where to write results?
        if (args.length >= 1) {
            dir = args[0];
        }
        System.out.println("Writing result YAML files to '" + dir + "'.");

        String[] gameFiles = {
            "2bases-game.txt",             // in stratsim module.
            "the-right-strategy-game.txt", // in stratplan module.
        };
        String[] strategy_sets = { "2012-02-05" /* "atk-dfnd", "synth" */ };

        for (String name : strategy_sets) {
            sim_matrix(gameFiles, name);
            System.out.println(name + " strategy set done.");
        }
        System.out.println("Done!");
    }

    /**
     * make simulated game matrix for given games.
     *
     */
    public static void sim_matrix(String[] gameFiles, String strategy_set) throws IOException {

        StrategyTemplate[] strategies = SwitchingPlanner.getStrategies(strategy_set);
        // make win rate matrix
        final int N = strategies.length;
        final int nMaps = gameFiles.length;
        double map_win_rate[][][] = new double[nMaps][N][N];
        double map_scores[][][] = new double[nMaps][N][N];

        for (int m = 0; m < nMaps; m++) {
            String gameFile = gameFiles[m];
            map_sim_matrix(gameFile, strategy_set, map_scores[m], map_win_rate[m]);
        }
    }

    static void write_YAML_table(double[][] data, String label, String caption, String filepath) throws IOException {
        // - caption:
        //   label:
        //   matrix:
        //   - [1, 2, 3]
        //   - [4, 5, 6]
        //
        BufferedWriter doc = new BufferedWriter(new FileWriter(filepath));
        // tex.write("% table written on by {1}\n".format(today.strftime('%Y-%m-%d'),sys.argv[0]))
        doc.write("# table written by " + WriteSimMatrix.class.getName() + "\n");
        doc.write("- caption: \"" + caption + "\"\n");
        doc.write("  label: \"" + label + "\"\n");
        doc.write("  matrix:\n");
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
            doc.write("]\n");
        }
        doc.close();
        System.out.println("wrote " + filepath + ".");
    }

    protected final static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-DD");

    private static void map_sim_matrix(String gameFile, String strategy_set, double[][] scores, double[][] win_rate) throws IOException {
        final int playerId = 0;
        GameState state = GameStates.getGameState(gameFile);
        SwitchingPlanner planner = new SwitchingPlanner();
        Map params = new LinkedHashMap();
        params.put("strategy_set", strategy_set);
        params.put("simReplan", true);
        params.put("choice_method", "Nash");
        params.put("player","Nash");
        params.put("opponent", "none");
        planner.configure(params);
        PlayerGroups[] gs = GroupAnalysis.defineGroups(state);
        StrategicState s = new StrategicState(gs, state);
        planner.makePlan(playerId, s);
        double[][] values = planner.getValues();

        // update win rate.
        // win_rate is transpose matrix to show rates from the column player perspective
        // just easier to print in a report that way.
        final int N = win_rate.length;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                scores[j][i] += values[i][j]; // transpose matrix
                if (values[i][j] > 0) {
                    win_rate[j][i] += 1;
                } else if (values[i][j] < 0) {
                    win_rate[j][i] -= 1;
                }
            }
        }
        // write win rate for map
        double[][] map_win_rate = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (values[i][j] > 0) {
                    map_win_rate[j][i] += 1;
                } else if (values[i][j] < 0) {
                    map_win_rate[j][i] -= 1;
                }
            }
        }
        Date now = new Date();
        String mapname = gameFile.replace("_", "-").replace(".txt", "");

        // write scores for map
        //String mapname = gameFile.replace("_", "-").replace(".txt", "");
        String caption = String.format("Strategy Simulation Scores on %s (%s)",
                mapname, formatter.format(now));
        String filename = String.format("sim_scores_%s_%s.yaml",
                                    strategy_set, gameFile.replace(".txt", ""));
        File f = new File(dir, filename);
        write_YAML_table(scores, "sim_scores_" + mapname, caption, f.getPath());
    }
}
