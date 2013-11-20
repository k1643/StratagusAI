package orst.stratagusai;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import orst.stratagusai.config.ControllerConfig;
import orst.stratagusai.config.Config;

/**
 *
 * @author kingbria
 */
public class Main {
    private static final Logger log = Logger.getLogger(Main.class);

    public static void main(String [] args) throws Exception {
        Options options = new Options();
        options.addOption("c", true, "configuration file");
        options.addOption("m", true, "message level (0|1|2|3|off|debug|info|warn)");
        options.addOption("s", true, "game server name (default 'localhost')");
        options.addOption("v", true, "show video (true|1|false|0)");
        options.addOption("w", true, "warp speed (true|1|false|0)");
        options.addOption("h", false, "help");

        Parser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(Main.class.getName(), options);
            System.exit(-1);
        }

        GameRunner loop;
        if (cmd.hasOption("c")) {
            Config conf = Config.load(cmd.getOptionValue("c"));
            loop = configure(conf);
        } else {
            loop = configure();
        }
        if (cmd.hasOption("m")) {
            // loggers defined in log4j.properties resource file.
            // these levels replace the levels set in thed property file.
            String level = cmd.getOptionValue("m");
            Logger orstlog = Logger.getLogger("orst");
            Logger msglog = Logger.getLogger("msglog");
            if ("off".equals(level) || "0".equals(level)) {
                orstlog.setLevel(Level.OFF);
                msglog.setLevel(Level.OFF);
            } else if ("debug".equals(level) || "1".equals(level)) {
                orstlog.setLevel(Level.DEBUG);
                msglog.setLevel(Level.DEBUG);
            } else if ("info".equals(level) || "2".equals(level)) {
                orstlog.setLevel(Level.INFO);
                msglog.setLevel(Level.INFO);
            } else if ("warn".equals(level) || "3".equals(level)) {
                orstlog.setLevel(Level.WARN);
                msglog.setLevel(Level.WARN);
            }
        }

        if (cmd.hasOption("v")) {
            String v = cmd.getOptionValue("v");
            loop.setShowVideo("1".equals(v) || "true".equals(v));
        }
        if (cmd.hasOption("w")) {
            String w = cmd.getOptionValue("w");
            loop.setWarpSpeed("1".equals(w) || "true".equals(w));
        }

        if (cmd.hasOption("s")) {
            loop.getGame().connect(cmd.getOptionValue("s"));
        } else {
            loop.getGame().connect();
        }
        long start = System.currentTimeMillis();
        try {
            loop.run();
        } catch (Throwable t) {
            log.error("error in agent loop", t);
        }
        loop.getGame().killStratagus();
        loop.getGame().closeGameProxySocket();
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(String.format("Games over!  Elapsed time %.2f minutes", elapsed/(1000*60F)));
    }

    private static GameRunner configure(Config conf) throws Exception {
        GameProxy game = new GameProxy();
        GameRunner loop;
        if (conf.isTraining()) {
             loop = new GameTrainer(game);
             log.debug("training session configured.");
        } else {
            loop = new GameRunner(game);
            log.debug("evaluation session configured.");
        }
        loop.configure(conf);

        ControllerConfig [] configs = conf.getAgentConfigs();
        // get player controllers
        for (int i = 0; i < configs.length; i++) {
            ControllerConfig agentConfig = configs[i];
            String className = agentConfig.getControllerClassName();
            Controller c = (Controller) Class.forName(className).newInstance();
            c.setPlayerId(i);
            c.configure(agentConfig);
            loop.addController(c); // controller for player i
        }
        return loop;
    }

    private static GameRunner configure() {
        GameProxy game = new GameProxy();
        GameRunner loop = new GameRunner(game);

        Controller c = new HeuristicAgent(0);
        loop.addController(c); // controller for player
        return loop;
    }
}
