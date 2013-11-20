package orst.stratagusai;

import java.io.File;
import java.io.IOException;

/**
 * Call EngineProcess.startStratagus() to run the stratagus engine.
 *
 * @author Brian
 */
public class EngineProcess {
    public static void main(String[] args) throws Exception {
        Process proc = startStratagus(false);
        proc.waitFor();
        System.out.println("Done!");
    }

    public static File whereis(String program) {
        String systemPath = System.getenv("PATH");
        String[] pathDirs = systemPath.split(File.pathSeparator);
        for (String pathDir : pathDirs) {
            File file = new File(pathDir, program);
            if (file.isFile())
                return file;
        }
        return null;
    }

    public static Process startStratagus(boolean AI) throws IOException {
        File path;
        String os = System.getProperty("os.name");
        System.out.println("running on " + os);
        if (os.equals("posix"))
            path = whereis("stratagus");
        else
            path = whereis("stratagus.exe");
        if (path == null)
            throw new RuntimeException("stratagus executable not found in path.");
        File cwd = path.getParentFile();
        String[] cmd;
        if (AI) {
            cmd = new String[]{"stratagus", "-g", "scripts/gameloop.lua"};
        } else {
            // turn engine opponent AI off.
            cmd = new String[]{"stratagus", "-g", "scripts/gameloop.lua","-a","0"};
        }
        Process proc = Runtime.getRuntime().exec(cmd, null, cwd);
        String cmdstr = "";
        for (int i = 0; i < cmd.length; i++) {
            if (i > 0) {
                cmdstr += ", ";
            }
            cmdstr += cmd[i];
        }
        System.out.println("launched stratagus " + "cmd=" + cmdstr);
        return proc;
    }
}
