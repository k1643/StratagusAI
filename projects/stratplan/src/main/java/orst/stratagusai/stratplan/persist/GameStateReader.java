package orst.stratagusai.stratplan.persist;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.GameStateEval;
import orst.stratagusai.stratplan.GameStateLexer;
import orst.stratagusai.stratplan.GameStateParser;
import orst.stratagusai.stratplan.model.GameState;

/**
 *
 * @author Brian
 */
public class GameStateReader {
    private static final Logger log = Logger.getLogger(GameStateReader.class);
    public static GameState load(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        GameState conf = load(in);
        in.close();
        return conf;
    }

    public static GameState load(Reader in) throws IOException {
        ANTLRReaderStream input = new ANTLRReaderStream(in);
        GameStateLexer lex = new GameStateLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lex);
        GameStateParser parser = new GameStateParser(tokens);
        try {
            GameStateParser.game_state_return r = parser.game_state();

            CommonTree t = (CommonTree) r.getTree();

            // create a tree node stream from the tree.
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
            GameStateEval walker = new GameStateEval(nodes);
            GameState game = walker.game_state();
            return game;
        } catch (RecognitionException ex) {
            throw new IOException("unable to parse strategy input.", ex);
        }
    }
}
