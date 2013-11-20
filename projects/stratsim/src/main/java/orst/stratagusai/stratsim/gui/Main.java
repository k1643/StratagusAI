/* 
 *
 */
package orst.stratagusai.stratsim.gui;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Command Line interface for view of a game.
 *
 * @author Brian King
 */
public class Main
{
    protected static Logger log = Logger.getLogger(Main.class);
    private static Logger simlog = Logger.getLogger("game_sim_event");

    protected static GameFrame frame;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("c", true, "configuration");
        options.addOption("h", false, "help");

        Parser parser = new GnuParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(Main.class.getName(), options);
            System.exit(-1);
        }

        simlog.setLevel(Level.INFO);

        frame = new GameFrame();
        frame.setVisible(true);  // start GUI loop
        
    }
}