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
import orst.stratagusai.stratplan.model.GameMap;

/**
 *
 * @author Brian
 */
public class GameMapReader {
    private static final Logger log = Logger.getLogger(GameMapReader.class);
    public static GameMap load(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        GameMap conf = load(in);
        in.close();
        return conf;
    }

    public static GameMap load(Reader in) throws IOException {
        ANTLRReaderStream input = new ANTLRReaderStream(in);
        GameStateLexer lex = new GameStateLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lex);
        GameStateParser parser = new GameStateParser(tokens);
        try {
            GameStateParser.map_return r = parser.map();

            CommonTree t = (CommonTree) r.getTree();

            // create a tree node stream from the tree.
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
            GameStateEval walker = new GameStateEval(nodes);
            GameMap map = walker.map();
            return map;
        } catch (RecognitionException ex) {
            throw new IOException("unable to parse input.", ex);
        }
    }
}
