package orst.stratagusai;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.config.Config;
import orst.stratagusai.config.GoalConfig;

/**
 * Run configured Strategies in evaluation mode.
 * 
 * @author bking
 */
public class GameRunner {

    private static final Logger log = Logger.getLogger(GameRunner.class);
    protected GameProxy game;

    protected final int CYCLES_PER_TRANSITION = 30;
    /** sleep time per update cycle. Keeping this time short speeds up
     * games in warp_speed mode.
     */
    protected final int SLEEP_TIME = 10;

    /** maximum cycles per episode (game). */
    protected int max_cycles = Integer.MAX_VALUE;

    /** Episodes to run.  250 episodes run in about 20 minutes */
    protected int episodes = 250;
    protected boolean show_video = true;
    protected boolean warp_speed = false;
    
    /** controllers of players.  Strategies request actions based on
     * the current state. */
    protected List<Controller> controllers = new ArrayList<Controller>();
    protected List<String> mapPaths = new ArrayList<String>();

    /** Goal tests when an episode is done. */
    protected Goal goal = new Goal();
    /** directory to write log files into. value determined in getLogBase(). */
    protected String log_base;
    /** write results of games */
    protected File resultLog;

    public GameRunner(GameProxy game) {
        this.game = game;
    }

    public void configure(Config config) throws Exception {
        max_cycles = config.getMaxCycles();
        setEpisodes(config.getEpisodes());
        setMapPaths(config.getMapPaths());
        log.debug("max cycles=" + max_cycles);

        if (config.getGoalConfig() != null) {
            GoalConfig goalConfig = config.getGoalConfig();
            String className = goalConfig.getGoalClassName();
            goal = (Goal) Class.forName(className).newInstance();
        }
    }

    public GameProxy getGame() {
        return game;
    }

    public void setMaxCycles(int max_cycles) {
        this.max_cycles = max_cycles;
    }

    public void setShowVideo(boolean video) {
        this.show_video = video;
    }

    public void setWarpSpeed(boolean b) {
        this.warp_speed = b;
    }


    public void setEpisodes(int episodes) {
        log.debug("run " + episodes + " episodes");
        this.episodes = episodes;
    }

    public void setMapPaths(List<String> mapPaths) {
        this.mapPaths = mapPaths;
    }

    public void setMapPaths(String[] mapPaths) {
        this.mapPaths.clear();
        for (String path : mapPaths) {
            this.mapPaths.add(path);
        }    
    }

    /**
     * run stratagus episodes.
     * @throws IOException
     */
    public void run() throws IOException {
        if (mapPaths == null) {
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
        for (Controller c : controllers) {
            c.beginSession(game);
        }

        for (String mapPath : mapPaths) {
            for (Controller c : controllers) {
                c.beginCycle(game, false); // evaluation
            }
            // an episode is one Stratagus game - e.g. a battle.
            for (int episode = 1; episode <= episodes; episode++) {
                restartScenario(mapPath);
                play(); // play one episode
                //logResult();
                game.clearState();
            }
            for (Controller c : controllers) {
                c.endCycle(game, false); // evaluation
            }
        }

        // end session. give agents a chance to clean up.
        for (Controller c : controllers) {
            c.endSession(game);
        }
    }

    protected void restartScenario(String mapPath) {
        log.debug("restart map " + mapPath);
        game.loadMapAndRestartScenario(mapPath);
    }

    /**
     * Run an episode.
     * 
     * @param episode
     */
    protected void play() {
        game.init();  // cycle and map dimensions
        game.getUnitStatesFromStratagus();
        game.getMapStateFromStratagus();  // get map again tiles.
        try {
            // begin episode
            for (Controller c : controllers) {
                c.beginEpisode(game);
            }
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
                for (Controller c : controllers) {
                    c.nextActions(game);
                }
                // Advance the game a cycle
                game.advanceNCycles(CYCLES_PER_TRANSITION);
                Thread.sleep(SLEEP_TIME);
            }
            // end episode
            for (Controller c : controllers) {
                c.endEpisode(game);
            }
        } catch (Exception e) {
            log.error("error in game loop", e);
            System.exit(1);
        }
    }

    public void addController(Controller controller) {
        controllers.add(controller);
    }

    public boolean episodeDone() {
        return goal.episodeDone(game);
    }

    protected void logResult() throws IOException {
        if (resultLog == null) {
            String log_base = getLogBase();
            SimpleDateFormat formatter = new SimpleDateFormat("MM-DD-HH-mm");
            Date now = new Date();
            String name = "games-" + formatter.format(now) + ".txt";
            resultLog = new File(log_base, name);
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

    protected int getTotalHP(Collection<Unit> units) {
        int total = 0;
        for (Unit u : units) {
            total += u.getHitPoints();
        }
        return total;
    }

    protected String getLogBase() {
        if (log_base == null) {
            SimpleDateFormat formatter = new SimpleDateFormat("DD-HH-mm");
            Date now = new Date();
            for (int i = 0; log_base == null; i++) {
                String name = "logs/games-" + formatter.format(now) + "-" + i;
                File dir = new File(name);
                if (!dir.exists()) {
                    log_base = name;
                    if (!dir.mkdir()) {
                        throw new RuntimeException("unable to create directory " + log_base);
                    }
                }
            }
        }
        return log_base;
    }
}
