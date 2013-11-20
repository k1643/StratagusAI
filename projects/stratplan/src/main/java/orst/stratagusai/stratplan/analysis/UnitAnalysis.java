package orst.stratagusai.stratplan.analysis;

import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitType;

/**
 *
 * Classify Unit types.  TODO: refactor, copied from stratsim.
 *
 * @author Brian
 */
public class UnitAnalysis {


    public static boolean isCombat(Unit u) {
        return isCombat(u.getUnitTypeString());
    }

    public static boolean isCombat(WargusUnitType type) {
        return isCombat(type.getName());
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
               "unit-human-submarine".equals(type);
    }

    public static boolean isPeasant(WargusUnitType type) {
        return isPeasant(type.getName());
    }

    public static boolean isPeasant(Unit u) {
        return isPeasant(u.getUnitTypeString());
    }

    public static boolean isPeasant(String type) {
        return "unit-peasant".equals(type);
    }

    public static boolean isBuilding(WargusUnitType type) {
        return isBuilding(type.getName());
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
               "unit-human-oil-platform".equals(type);
    }
}
