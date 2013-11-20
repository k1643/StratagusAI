package orst.stratagusai.stratplan.persist;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import orst.stratagusai.stratplan.Argument;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.Port;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.Task;
import orst.stratagusai.stratplan.Trigger;

/**
 *
 * @author Brian
 */
public class StrategicPlanWriter {
    protected static final String INDENT = "  ";
    protected static final String NL = System.getProperty("line.separator");

    public static String write(StrategicPlan plan) {
        StringWriter sw = new StringWriter();
        try {
            write(plan, sw);
            return sw.toString();
        } catch(IOException e) {
            throw new RuntimeException("Can not write plan to string", e);
        }
    }

    public static void write(StrategicPlan plan, String filename) throws IOException {
        FileWriter in = new FileWriter(filename);
        write(plan, in);
        in.close();
    }

    public static void write(StrategicPlan plan, Writer out) throws IOException {
        out.write("(:plan ");
        out.write(plan.getName());
        out.write(" :player ");
        out.write(String.valueOf(plan.getPlayerId()));
        out.write(NL);
        writeGroupSpecs(plan, out);
        for (Task task : plan.getTasks()) {
            write(plan, task, out);
        }        
        out.write(")");
        out.write(NL);
    }

    public static void write(StrategicPlan plan, Task task, Writer out) throws IOException {
        if (StrategicPlan.START_NAME.equals(task.getName())) {
            // don't write root task
            return;
        }
        out.write(INDENT);
        out.write("(:task ");
        out.write(task.getName());
        out.write(" ");
        writeArguments(task, out);
        if (task.getComment() != null) {
            out.write(NL);
            out.write(INDENT);
            out.write(INDENT);
            out.write("# ");
            out.write(task.getComment());
        }
        out.write(NL);
        out.write(INDENT);
        out.write(INDENT);
        out.write(":type ");
        out.write(task.getType());
        out.write(NL);
        UnitGroup using = task.getUsingGroup();
        if (using != null) {
            out.write(INDENT);
            out.write(INDENT);
            out.write(":using ");
            out.write(String.valueOf(using.getId()));
            out.write(NL);
        }
        writeStartPort(plan, task.getStartPort(), out);
        writeEndPort(plan, task.getEndPort(), out);
        out.write(INDENT);
        out.write(")");
        out.write(NL);
    }

    private static void writeStartPort(StrategicPlan s, Port port, Writer out) throws IOException {
        writePort(s, port, ":start", out);
    }

    private static void writeEndPort(StrategicPlan s, Port port, Writer out) throws IOException {
        writePort(s, port, ":end", out);
    }

    private static void writePort(StrategicPlan s, Port port, String src, Writer out) throws IOException {
        Set<Trigger> triggers = port.getOutgoingTriggers();
        if (triggers.isEmpty()) {
            return;
        }
        out.write(INDENT);
        out.write(INDENT);
        out.write(src);  // :start or :end
        out.write(" (:trigger ");
        for (Trigger trigger : triggers) {
            // trigger connects to start or end port of target task.
            Port target = s.getDestination(trigger);
            Task trgTask = target.getTask();
            out.write('(');
            if (target == trgTask.getStartPort()) {
                out.write("start ");
            } else if (target == trgTask.getEndPort()) {
                out.write("end ");
            } else {
                throw new RuntimeException("trigger and port references do not match. Source task " + port.getTask().getName() + ", Target task " + trgTask.getName());
            }
            out.write(trgTask.getName());
            out.write(')');
        }
        out.write(")");
        out.write(NL);
    }

    public static void writeArguments(Task t, Writer out) throws IOException {
        out.write("(");
        if (t.getTargetGroup() != null) {
            out.write("(:group ");
            out.write(String.valueOf(t.getTargetGroup().getId()));
            out.write(")");
        }
        if (t.getTargetRegionId() != -1) {
            int id = t.getTargetRegionId();
            out.write("(:region ");
            out.write(String.valueOf(id));
            out.write(")");
        }
        out.write(")");
    }

    public static void writeGroupSpecs(StrategicPlan plan, Writer out) throws IOException {
        Map<Integer,UnitGroup> gs = new TreeMap<Integer,UnitGroup>();
        for (UnitGroup g : plan.getPlayerGroups().getGroups()) {
            gs.put(g.getId(), g);
        }
        for (UnitGroup g : gs.values()) { // iterate in order by key.
            writeGroup(g, out);
        }
    }

    public static void writeGroup(UnitGroup g, Writer out) throws IOException {
        out.write(INDENT);
        out.write("(:group-spec ");
        out.write(String.valueOf(g.getId()));
        out.write(" :type ");
        out.write(g.getType());
        for (Argument units : g.getUnitTypeReqs()) {
            out.write(" ");
            out.write(units.getName());
            out.write(" ");
            out.write(String.valueOf(units.getValue()));
        }
        out.write(")");
        out.write(NL);
    }
}
