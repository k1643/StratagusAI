package orst.stratagusai.stratsim.planner;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import org.apache.log4j.Logger;
import orst.stratagusai.stratplan.StrategicPlanner;
import orst.stratagusai.stratplan.mgr.Statistics;

/**
 * Planner
 *
 */
public abstract class BasePlanner implements StrategicPlanner {
    private static final Logger log = Logger.getLogger(BasePlanner.class);
    protected final static String NL = System.getProperty("line.separator");

    protected Statistics stats;

    public void setStatistics(Statistics stats) {
        this.stats = stats;
    }

    public Statistics getStatistics() {
        return stats;
    }

    public void configure(Map params) {}

    protected void logStrategy(int playerId, String name, int cycle) {
        final String filename = "strategies" + playerId + ".txt";
        try {
            // open for append.
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, true));
            out.write("strategy " + name + " at " + cycle + NL);
            out.close();
        } catch (IOException ex) {
            log.error("unable to write " + filename, ex);
        }
    }

}
