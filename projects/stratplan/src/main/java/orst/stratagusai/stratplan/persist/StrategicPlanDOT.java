package orst.stratagusai.stratplan.persist;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import orst.stratagusai.stratplan.UnitGroup;

import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;

/**
 * Read strategic plan and write as GraphViz dot file.
 *
 */
public class StrategicPlanDOT
{
    public static void main( String[] args ) throws Exception
    {
        String filename = "data/strategy.strat";
        if (args.length > 0) {
            filename = args[0];
        }

        StrategicPlan s = StrategicPlanReader.read(filename);
        writeDOTFile(s);
    }

    public static void writeDOTFile(StrategicPlan plan) throws IOException {
        writeDOTFile(plan, "Strategic Plan", "plan.dot");
    }

    public static void writeDOTFile(StrategicPlan plan, String graphLabel, String filename) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter(filename));

        out.write("digraph \"" + plan.getName() + "\" {");
	out.write("graph [label = \"" + graphLabel + "\"];\n");
        out.write("rankdir=LR;\n");
        out.write("size=\"8,5\";\n");
        out.write("node [shape=box,fontsize=10,labeljust=l];\n");

        for (Task t : plan.getTasks()) {
            if (StrategicPlan.NOOP_TYPE.equals(t.getType())) {
                continue;
            }
            UnitGroup using = t.getUsingGroup();
            UnitGroup spec = t.getTargetGroup();
            int rid = t.getTargetRegionId();
            String label = t.getType();
            if (spec != null) {
                label += "\\n  group " + spec.getId();
            }
            if (rid != -1) {
                label += "\\n  region " + rid;
            }
            if (using != null) {
                label += "\\n  using group " + using.getId();
            }
            label += "\\n est. end " + t.getEstimatedCompletionTime();
            
            out.write("\"" + t.getName() + "\" [label=\"" + label);
            out.write("\"];");
            out.newLine();
        }

        Task start = plan.getStart(); // make sure there is a start task.

        for (Task t : plan.getTasks()) {
            for (Task next : t.getSuccessors()) {
                out.write('"' + t.getName() + "\" -> \"" + next.getName() + "\";");
                out.newLine();
            }
        }

        out.write("}");
        out.newLine();
        out.close();
    }
}
