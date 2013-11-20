package orst.stratagusai.taclp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import orst.stratagusai.GameProxy;
import orst.stratagusai.Goal;
import orst.stratagusai.Unit;
import orst.stratagusai.config.Config;
import orst.stratagusai.util.Randomizer;

/**
 *
 * @author Brian
 */
public class CoordinateAscentRunner {
    private static final Logger log = Logger.getLogger(CoordinateAscentRunner.class);

    protected static final int CYCLES_PER_TRANSITION = 30;
    /** sleep time per update cycle */
    protected static final int SLEEP_TIME = 100;

    /** maximum cycles per episode (game). */
    protected static int max_cycles = Integer.MAX_VALUE;

    /** Episodes to run.  250 episodes run in about 20 minutes */
    protected static int episodes = 250;

    protected static GameProxy game = new GameProxy();

    protected static boolean show_video = true;
    protected static boolean warp_speed = false;

    //protected static List<String> mapPaths = new ArrayList<String>();

    private static List<String> trainingMaps = new ArrayList<String>();

    private static List<String> evaluationMaps = new ArrayList<String>();

    protected static CoordinateAscentCombatController controller;

    protected static Goal goal = new Goal();

    protected static StatusFrame statusWindow;

    protected static File resultLog;
    
    public static void main(String [] args) throws Exception {
        Options options = new Options();
        options.addOption("c", true, "configuration file");
        options.addOption("g", false, "open status window");
        options.addOption("m", true, "message level (0|1|2|3|off|debug|info|warn)");
        options.addOption("s", true, "game server name (default 'localhost')");
        options.addOption("v", true, "show video (true|1|false|0)");
        options.addOption("w", true, "warp speed (true|1|false|0)");
        options.addOption("h", false, "help");

        Parser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(CoordinateAscentCombatController.class.getName(), options);
            System.exit(-1);
        }

        controller = new CoordinateAscentCombatController(0);
        if (cmd.hasOption("c")) {
            Config conf = Config.load(cmd.getOptionValue("c"));
            configure(conf);
        } else {
            System.err.println("No configuration file given.");
            System.exit(-1);
        }
        if (cmd.hasOption("m")) {
            // loggers defined in log4j.properties resource file.
            // these levels replace the levels set in thed property file.
            String level = cmd.getOptionValue("m");
            Logger orstlog = Logger.getLogger("orst");
            Logger msglog = Logger.getLogger("msglog");
            if ("off".equals(level) || "0".equals(level)) {
                orstlog.setLevel(Level.OFF);
                msglog.setLevel(Level.OFF);
            } else if ("debug".equals(level) || "1".equals(level)) {
                orstlog.setLevel(Level.DEBUG);
                msglog.setLevel(Level.DEBUG);
            } else if ("info".equals(level) || "2".equals(level)) {
                orstlog.setLevel(Level.INFO);
                msglog.setLevel(Level.INFO);
            } else if ("warn".equals(level) || "3".equals(level)) {
                orstlog.setLevel(Level.WARN);
                msglog.setLevel(Level.WARN);
            }
        }

        if (cmd.hasOption("v")) {
            String v = cmd.getOptionValue("v");
            show_video = ("1".equals(v) || "true".equals(v));
        }
        if (cmd.hasOption("w")) {
            String w = cmd.getOptionValue("w");
            warp_speed = ("1".equals(w) || "true".equals(w));
        }

        if (cmd.hasOption("g")) {
            statusWindow = new StatusFrame();
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                    statusWindow.setVisible(true);
                }
            });
        }

        if (cmd.hasOption("s")) {
            game.connect(cmd.getOptionValue("s"));
        } else {
            game.connect();
        }
        try {
            run();
        } catch (Throwable t) {
            log.error("error in agent loop", t);
        }
        game.killStratagus();
        game.closeGameProxySocket();
        System.out.println("game over!");
    }


    /**
     * run stratagus episodes.
     * @throws IOException
     */
    static public void run() throws IOException {
        if (trainingMaps == null || evaluationMaps == null) {
            throw new RuntimeException("map paths not configured.");
        }
        if (show_video) {
//          game.setVideoSpeed(5);
            if (warp_speed) {
                game.setSpeed(-1);
            }
        } else {
            game.setVideoSpeed(-1);
            game.setSpeed(-1);
        }

        // begin session
        controller.beginSession(game);
        while (!controller.isDone()) {
            // run training episodes on randomly selected maps.
            controller.beginCycle(game, true); // begin training cycle
            while (controller.isTraining()) {
                restartScenario(selectMap());
                play(); // play one episode
                game.clearState();
                updateStatusWindow();
            }
            controller.endCycle(game, true); // end training cycle
            
            // run evaluation cycle on all maps.
            controller.beginCycle(game, false); // evaluation
            for (String path : evaluationMaps) {
                game.loadMapAndRestartScenario(path);
                play();
                logResult();
                game.clearState();
                updateStatusWindow();
            }
            controller.endCycle(game, false); // evaluation
        }
        controller.endSession(game);
        updateStatusWindow();
    }

    static protected void restartScenario(String mapPath) {
        log.debug("restart map " + mapPath);
        game.loadMapAndRestartScenario(mapPath);
    }

    /**
     * Run an episode.
     *
     * @param episode
     */
    static protected void play() {
        game.init();
        game.getUnitStatesFromStratagus();
        game.getMapStateFromStratagus();
        try {
            // begin episode
            controller.beginEpisode(game);
            // advance game cycles to complete an episode
            // if all units of either side are dead, then episode is done.
            for (int currentGameCycle = 0;
                    currentGameCycle <= max_cycles;
                    currentGameCycle += CYCLES_PER_TRANSITION) {
                // get state
                game.getCurrentCycleFromStratagus();
                game.getUnitStatesFromStratagus();  // "LISPGET STATE"
                if (episodeDone()) {
                    break;
                }
                controller.nextActions(game);
                updateStatusWindow();
                // Advance the game a cycle
                game.advanceNCycles(CYCLES_PER_TRANSITION);
                Thread.sleep(SLEEP_TIME);
            }
            // end episode
            controller.endEpisode(game);
        } catch (Exception e) {
            log.error("error in game loop", e);
            System.exit(1);
        }
    }

    protected static String selectMap() {
        // select random map to reload.
        int i = Randomizer.nextInt(trainingMaps.size());
        return trainingMaps.get(i);
    }

    public static boolean episodeDone() {
        return goal.episodeDone(game);
    }

    public static void configure(Config config) throws Exception {
        max_cycles = config.getMaxCycles();
        log.debug("max cycles=" + max_cycles);
        episodes = config.getEpisodes();
        List<String> mapPaths = new ArrayList<String>(config.getMapPaths());
        Collections.shuffle(mapPaths, Randomizer.getRandom());
        // divide maps into training and evaluation sets.
        trainingMaps.clear();
        evaluationMaps.clear();
        int d = (2*mapPaths.size())/3;  // take 2/3rds of data as training set.
        if (d < 2) {
            throw new RuntimeException("not enough training maps.");
        }
        int i = 0;
        for (; i < d; i++) {
            trainingMaps.add(mapPaths.get(i));
        }
        for (; i < mapPaths.size(); i++) {
            evaluationMaps.add(mapPaths.get(i));
        }
    }

    private static void updateStatusWindow() {
        if (statusWindow == null) {
            return;
        }
        final Map<String,String> status = new LinkedHashMap<String,String>();
        status.put("cycle", String.valueOf(game.getCurrentCycle()));
        status.put("training", String.valueOf(controller.isTraining()));
        final String label6 = "params";
        final String value6 = controller.getParams().toString();
        controller.getStatus(status);
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                statusWindow.updateFields(status, label6, value6);
            }
        });
    }

    protected static void logResult() throws IOException {
        if (resultLog == null) {
            SimpleDateFormat formatter = new SimpleDateFormat("MM-DD-HH-mm");
            Date now = new Date();
            String name = "games-" + formatter.format(now) + ".txt";
            resultLog = new File(name);
        }
        Map<Integer, Unit> units0 = game.getUnits(0);
        Map<Integer, Unit> units1 = game.getUnits(1);
        PrintWriter out = new PrintWriter(new FileWriter(resultLog, true));
        out.print(getTotalHP(units0.values()));
        out.print(",");
        out.print(getTotalHP(units1.values()));
        out.println();
        out.close();
    }

    protected static int getTotalHP(Collection<Unit> units) {
        int total = 0;
        for (Unit u : units) {
            total += u.getHitPoints();
        }
        return total;
    }

}
