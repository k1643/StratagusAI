package orst.stratagusai;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import orst.stratagusai.client.UnitStateEval;
import orst.stratagusai.client.UnitStateLexer;
import orst.stratagusai.client.UnitStateParser;

/**
 *
 * @author Brian
 */
public class UnitStateReader {
    public static void read(GameProxy game, String filename) throws IOException {
        FileReader in = new FileReader(filename);
        read(game, in);
        in.close();
    }

    public static void read(GameProxy game, Reader in) throws IOException {
        ANTLRReaderStream input = new ANTLRReaderStream(in);
        UnitStateLexer lex = new UnitStateLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lex);
        UnitStateParser parser = new UnitStateParser(tokens);
        try {
            UnitStateParser.unit_state_return r = parser.unit_state();

            CommonTree t = (CommonTree) r.getTree();

            // create a tree node stream from the tree.
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
            UnitStateEval walker = new UnitStateEval(nodes);
            walker.setGameProxy(game);
            walker.unit_state();
        } catch (RecognitionException ex) {
            throw new IOException("unable to parse strategy input.", ex);
        }
    }
}
