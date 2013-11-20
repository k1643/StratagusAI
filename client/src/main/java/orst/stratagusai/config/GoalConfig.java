package orst.stratagusai.config;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Sean
 */
public class GoalConfig {
	private String goalClassName;

	public String getGoalClassName() {
		return goalClassName;
	}

	public void setGoalClassName(String goalClassName) {
		this.goalClassName = goalClassName;
	}

	public static GoalConfig load(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        GoalConfig goal = load(in);
        in.close();
        return goal;
    }

    public static GoalConfig load(Reader in) {
        Yaml yaml = new Yaml();
        return (GoalConfig) yaml.load(in);
    }
}
