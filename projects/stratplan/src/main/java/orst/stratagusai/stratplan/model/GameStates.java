package orst.stratagusai.stratplan.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import orst.stratagusai.stratplan.persist.GameStateReader;

/**
 * load a saved game state.
 *
 * @author Brian
 */
public class GameStates {
    /**
     * temporary map.  Replace when we have a map analyzer
     * that can abstract Regions.
     *
     * @return
     */
    public static GameState getGameState(String filename) {
        // load from resource file.
        InputStream is =
                ClassLoader.getSystemResourceAsStream(filename);
        assert is != null : "no game file '" + filename + "'";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        try {
            return GameStateReader.load(br);
        } catch (IOException e) {
            throw new RuntimeException("unable to load " + filename + " as resource");
        }
    }
}
