package orst.stratagusai.config;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.yaml.snakeyaml.Dumper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serializable game configuration.
 *
 * @author Brian
 */
public class Config {

    private String sessionName;

    private ControllerConfig[] agentConfigs;
    /** Configures goal to determine when episode is over */
    private GoalConfig goalConfig;

    /** maximum cycles per episode */
    private int max_cycles = Integer.MAX_VALUE;
    
    /** Episodes to run.  250 episodes run in about 20 minutes */
    private int episodes = 250;
    /** train and evaluate, or just evaluate */
    private boolean training;

    private int trainingCycles = 10;

    private List<String> mapPaths;

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
   
    public ControllerConfig[] getAgentConfigs() {
        return agentConfigs;
    }

    public void setAgentConfigs(ControllerConfig[] configs) {
        agentConfigs = configs;
    }

    public int getMaxCycles() {
        return max_cycles;
    }

    public void setMaxCycles(int max_cycles) {
        this.max_cycles = max_cycles;
    }


    public int getEpisodes() {
        return episodes;
    }

    public void setEpisodes(int episodes) {
        this.episodes = episodes;
    }

    public boolean isTraining() {
        return training;
    }

    public void setTraining(boolean training) {
        this.training = training;
    }

    public int getTrainingCycles() {
        return trainingCycles;
    }

    public void setTrainingCycles(int trainingCycles) {
        this.trainingCycles = trainingCycles;
    }

    public GoalConfig getGoalConfig() {
        return goalConfig;
    }

    public void setGoalConfig(GoalConfig goalConfig) {
        this.goalConfig = goalConfig;
    }

    public List<String> getMapPaths() {
        return mapPaths;
    }

    public void setMapPaths(List<String> mapPaths) {
        this.mapPaths = mapPaths;
    }

    public void addMapPath(String mapPath) {
        if (mapPaths == null) {
            mapPaths = new ArrayList<String>();
        }
        mapPaths.add(mapPath);
    }

    public static String dump(Config config) {
        Yaml yaml = new Yaml(new Dumper(new DumperOptions()));
        String output = yaml.dump(config);
        return output;
    }

    public static void dump(Config config, String filename) throws IOException {
        FileWriter out = new FileWriter(filename);
        Yaml yaml = new Yaml(new Dumper(new DumperOptions()));
        yaml.dump(config, out);
        out.close();
    }

    public static void dump(ControllerConfig config, String filename) throws IOException {
        FileWriter out = new FileWriter(filename);
        Yaml yaml = new Yaml(new Dumper(new DumperOptions()));
        yaml.dump(config, out);
        out.close();
    }

    public static Config load(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        Config conf = load(in);
        in.close();
        return conf;
    }

    public static Config load(Reader in) {
        Yaml yaml = new Yaml();
        return (Config) yaml.load(in);
    }
}
