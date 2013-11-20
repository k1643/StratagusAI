package orst.stratagusai.stratsim;

import java.io.FileWriter;
import java.io.PrintWriter;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.persist.GameStateReader;
import orst.stratagusai.stratplan.persist.StrategicPlanReader;
import orst.stratagusai.stratsim.model.SimController;
import orst.stratagusai.stratsim.model.SimState;
import orst.stratagusai.util.Randomizer;

public class Main {
    public static void main(String[] args) throws Exception {
        if (!(args.length == 0 || args.length == 3)) {
            System.err.println("usage: " + Main.class.getName() + " [strategy1 strategy2 gamestate]");
            System.exit(0);
        }
        String file0;
        String file1;
        String gameFile;
        if (args.length == 3) {
            file0 = args[0];
            file1 = args[1];
            gameFile = args[2];
        } else {
            // test a combat game.  two players each have two groups of 6 footmen.
            file0 = "data/combat-0.strat";
            file1 = "data/combat-1.strat";
            gameFile = "data/combat-game.txt";
        }
        Randomizer.init(12);  // initialize common randomizer for repeatability.
        StrategicPlan s0 = StrategicPlanReader.read(file0);
        StrategicPlan s1 = StrategicPlanReader.read(file1);

        GameState s = GameStateReader.load(gameFile);
        PlayerGroups[] gs = GroupAnalysis.defineGroups(s);
        SimState state = new SimState(gs, s);

        long start = System.currentTimeMillis();
        SimController sim = new SimController(state);
        sim.setPlan(0,s0);
        sim.setPlan(1,s1);
        sim.simulate();
        PrintWriter out = new PrintWriter(new FileWriter("result.txt", true));
        out.println(gameFile + " " + file0 + " " + file1);
        out.println("simulation takes " + 
                    (System.currentTimeMillis()-start)/1000.0 +
                    " secs.");
        if (!sim.isTerminal()) {
            out.println("Game did not end.");
            for (Unit u : s.getUnits()) {
                out.println(u);
            }
        }
        int[] scores = sim.getScores();
        for (int i = 0; i < scores.length; i++) {
            String msg = "player " + i + "\tscore " + scores[i];
            System.out.println(msg);
            out.println(msg);
        }
        out.println();
        out.close();
    }
}