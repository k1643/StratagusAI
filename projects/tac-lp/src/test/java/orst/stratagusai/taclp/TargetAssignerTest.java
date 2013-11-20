package orst.stratagusai.taclp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.Unit;

/**
 * Unit test for simple App.
 */
public class TargetAssignerTest
        extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public TargetAssignerTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(TargetAssignerTest.class);
    }

    /**
     * 
     */
    public void testAssigner() {
        List<Unit> allies = new ArrayList<Unit>();
        List<Unit> enemies = new ArrayList<Unit>();
        int nextId = 0;

        CreateUnit(nextId++, "unit-footman", 17, 41, allies);
        CreateUnit(nextId++, "unit-footman", 17, 42, allies);
        CreateUnit(nextId++, "unit-footman", 14, 43, allies);
        CreateUnit(nextId++, "unit-footman", 18, 41, allies);
        CreateUnit(nextId++, "unit-footman", 18, 42, allies);
        CreateUnit(nextId++, "unit-footman", 18, 43, allies);
        CreateUnit(nextId++, "unit-footman", 17, 49, allies);
        CreateUnit(nextId++, "unit-footman", 17, 50, allies);
        CreateUnit(nextId++, "unit-footman", 18, 49, allies);
        CreateUnit(nextId++, "unit-footman", 18, 50, allies);

        // enemies
        CreateUnit(nextId++, "unit-footman", 3, 41, enemies);
        CreateUnit(nextId++, "unit-footman", 3, 42, enemies);
        CreateUnit(nextId++, "unit-town-hall", 0, 47, enemies);
        CreateUnit(nextId++, "unit-elven-lumber-mill", 0, 44, enemies);
        CreateUnit(nextId++, "unit-human-barracks", 0, 41, enemies);
        CreateUnit(nextId++, "unit-farm", 4, 49, enemies);
        CreateUnit(nextId++, "unit-farm", 4, 47, enemies);
        CreateUnit(nextId++, "unit-farm", 4, 45, enemies);
        CreateUnit(nextId++, "unit-farm", 4, 43, enemies);
        CreateUnit(nextId++, "unit-farm", 6, 49, enemies);
        CreateUnit(nextId++, "unit-farm", 6, 47, enemies);
        CreateUnit(nextId++, "unit-farm", 6, 45, enemies);
        CreateUnit(nextId++, "unit-peasant", 2, 51, enemies);
        CreateUnit(nextId++, "unit-peasant", 3, 51, enemies);
        CreateUnit(nextId++, "unit-peasant", 4, 51, enemies);

        // p,v,a,m,t,K0,...,K4
        double[] params = {1,1,1,1,1,
                           1,1,1,1,1};
        Features f = Features1.extract(allies, enemies);
        Set<Assignment> a = TargetAssigner.assign(params, f, allies, enemies);

        // expected assignments calculated by AMPL:
        // ally   enemy         indexes
        // -------------
        // foot1: foot1             0: 0
        // foot2: foot2             1: 1
        // foot3: foot2             2: 1
        // foot4: foot1             3: 0
        // foot5: foot1             4: 0
        // foot6: foot2             5: 1
        // foot7: barracks          6: 4
        // foot8: barracks          7: 4
        // foot9: barracks          8: 4
        // foot10: barracks         9: 4
        int[] expected = {0,1,1,0,0,1,4,4,4,4};
        final int N = a.size();
//        assertEquals(allies.size(), N);
        for (Assignment assign : a) {
            System.out.println(assign);
        }
    }

    private void CreateUnit(int id, String type, int x, int y, List<Unit> units) {
        Unit u = new Unit(id);
        u.setUnitTypeString(type);
        u.setLoc(x, y);
        units.add(u);
    }
}
