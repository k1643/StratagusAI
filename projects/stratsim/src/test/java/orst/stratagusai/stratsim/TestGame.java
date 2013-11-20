package orst.stratagusai.stratsim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.GameStateReader;

/**
 *
 * @author Brian
 */
public class TestGame {
    /**
     * Hard-coded map for test cases.
     *
     * @return
     */
    public static GameState getOneWayInOneWayOut() {
        // load from resource file.
        //
        InputStream is =
                ClassLoader.getSystemResourceAsStream("one-way-game.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        try {
            GameState s = GameStateReader.load(br);
            return s;
        } catch (IOException e) {
            throw new RuntimeException("unable to load one-way-game.txt as resource");
        }
    }

    public static GameState getOneWayInOneWayOutWithBases() {
        // load from resource file.
        InputStream is =
                ClassLoader.getSystemResourceAsStream("one-way-with-bases.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        try {
            GameState s = GameStateReader.load(br);
            return s;
        } catch (IOException e) {
            throw new RuntimeException("unable to load one-way-with-bases.txt as resource");
        }
    }
}
