package orst.stratagusai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import orst.stratagusai.config.Config;
import orst.stratagusai.util.Randomizer;

/**
 * Divide maps into training and evaluation sets.
 * 
 * @author bking
 */
public class GameTrainer extends GameRunner {
    private static final Logger log = Logger.getLogger(GameTrainer.class);

    private List<String> trainingMaps = new ArrayList<String>();

    private List<String> evaluationMaps = new ArrayList<String>();

    private int trainingCycles;

    public GameTrainer(GameProxy game) {
        super(game);
    }

    @Override
    public void configure(Config config) {
        setEpisodes(config.getEpisodes());
        List<String> paths = new ArrayList<String>(config.getMapPaths());
        Randomizer.shuffle(paths);
        // divide maps into training and evaluation sets.
        trainingMaps.clear();
        evaluationMaps.clear();
        int d = (2*paths.size())/3;  // take 2/3rds of data as training set.
        if (d < 2) {
            throw new RuntimeException("not enough training maps.");
        }
        int i = 0;
        for (; i < d; i++) {
            trainingMaps.add(paths.get(i));
        }
        for (; i < paths.size(); i++) {
            evaluationMaps.add(paths.get(i));
        }
        trainingCycles = config.getTrainingCycles();
        log.debug(trainingCycles + " training cycles.");
    }

    /**
     * run stratagus episodes.
     * @throws IOException
     */
    @Override
    public void run() throws IOException {
        if (show_video) {
            game.setVideoSpeed(5);
            game.setSpeed(5);
        } else {
            game.setVideoSpeed(-1);
            game.setSpeed(-1);
        }

        // begin session
        for (Controller c : controllers) {
            c.beginSession(game);
        }

        // an episode is one Stratagus game - e.g. a battle.
        for (int episode = 0; episode <= episodes; ) {
            // run training episodes on randomly selected maps.
            for (Controller c : controllers) {
                c.beginCycle(game, true); // begin training cycle
            }
            for (int training = 0; training < trainingCycles; training++) {
                restartScenario(selectMap());
                play(); // play one episode
                game.clearState();
                episode++;
            }
            for (Controller c : controllers) {
                    c.endCycle(game, true); // end training cycle
            }
            // run evaluation cycle on all maps.
            evaluate();            
        }

        // end session. give agents a chance to clean up.
        for (Controller c : controllers) {
            c.endSession(game);
        }
    }

    protected void evaluate() throws IOException {
        for (Controller c : controllers) {
            c.beginCycle(game, false); // evaluation
        }
        for (String path : evaluationMaps) {
            game.loadMapAndRestartScenario(path);
            play();
            logResult();
            game.clearState();
        }
        for (Controller c : controllers) {
            c.endCycle(game, false); // evaluation
        }
    }

    protected String selectMap() {
        // select random map to reload.
        return (String) Randomizer.select(trainingMaps);
    }
}
