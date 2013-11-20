package orst.stratagusai.stratsim.analysis;

import java.util.Collection;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.GroupType;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 *
 * @author Brian
 */
public class UnitAnalysis {

    public static boolean isSimulation(Collection<Unit> us) {
        assert !us.isEmpty() : "empty collection of units";
        Boolean sim = null;
        for (Unit u : us) {
            if (sim == null) {
                sim = u instanceof GroupSim;
            } else if (sim) {
                assert u instanceof GroupSim;
            } else {
                assert !(u instanceof GroupSim);
            }
        }
        return sim;
    }


    public static boolean isCombat(Unit u) {
        return isCombat(u.getUnitTypeString());
    }

    public static boolean isCombat(String type) {
        return "unit-footman".equals(type) ||
               "unit-ballista".equals(type) ||
               "unit-knight".equals(type) ||
               "unit-archer".equals(type) ||
               "unit-mage".equals(type) ||
               "unit-paladin".equals(type) ||
               "unit-dwarves".equals(type) ||
               "unit-ranger".equals(type) ||
               "unit-female-hero".equals(type) ||
               "unit-gryphon-rider".equals(type) ||
               "unit-flying-angel".equals(type) ||
               "unit-white-mage".equals(type) ||

               "unit-human-destroyer".equals(type) ||
               "unit-battleship".equals(type) ||
               "unit-human-submarine".equals(type) ||

               // unit-group-combat is created by GameStateAbstractor
               // and ActionProduce.
               GroupType.GROUP_COMBAT.equals(type);
    }

    public static boolean isPeasant(Unit u) {
        return isPeasant(u.getUnitTypeString());
    }

    public static boolean isPeasant(String type) {
        return "unit-peasant".equals(type) ||
               GroupType.GROUP_PEASANT.equals(type);
    }


    /** buildings are units that produce other units. */
    public static boolean isBuilding(Unit u) {
        return isBuilding(u.getUnitTypeString());
    }

    public static boolean isBuilding(String type) {
        return "unit-farm".equals(type) ||
               "unit-human-barracks".equals(type) ||
               "unit-church".equals(type) ||
               "unit-stables".equals(type) ||
               "unit-human-shipyard".equals(type) ||
               "unit-elven-lumber-mill".equals(type) ||
               "unit-human-foundry".equals(type) ||
               "unit-town-hall".equals(type) ||
               "unit-mage-tower".equals(type) ||
               "unit-human-blacksmith".equals(type) ||
               "unit-human-refinery".equals(type) ||
               "unit-human-oil-platform".equals(type) ||

               // unit-group-building is created by GameStateAbstractor and
               // ActionProduce.
               GroupType.GROUP_BUILDING.equals(type);
    }
}
