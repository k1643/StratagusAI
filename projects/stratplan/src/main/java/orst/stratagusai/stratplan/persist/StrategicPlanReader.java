package orst.stratagusai.stratplan.persist;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.PlanEval;
import orst.stratagusai.stratplan.PlanLexer;
import orst.stratagusai.stratplan.PlanParser;

/**
 *
 * @author Brian
 */
public class StrategicPlanReader {
    public static StrategicPlan read(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        StrategicPlan s = read(in);
        in.close();
        return s;
    }

    public static StrategicPlan read(Reader in) throws IOException {
        ANTLRReaderStream input = new ANTLRReaderStream(in);
        PlanLexer lex = new PlanLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lex);
        PlanParser parser = new PlanParser(tokens);
        try {
            PlanParser.plan_return r = parser.plan();

            CommonTree t = (CommonTree) r.getTree();

            // create a tree node stream from the tree.
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
            PlanEval walker = new PlanEval(nodes);
            StrategicPlan plan = walker.plan();
            return plan;
        } catch (RecognitionException ex) {
            throw new IOException("unable to parse plan input.", ex);
        }
    }
}
