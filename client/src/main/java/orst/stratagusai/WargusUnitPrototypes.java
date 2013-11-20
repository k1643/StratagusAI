package orst.stratagusai;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Build Unit for a given Unit type.  Unit contains some
 * properties not sent by engine.
 *
 * Unit type strings in client resources wargus-unit-types.txt.
 * Human Unit properties defined in engine/trunk/data/scripts/human/units.lua.
 *
 * @author bking
 */
public class WargusUnitPrototypes {
    private static Logger log = Logger.getLogger(WargusUnitPrototypes.class);
    /** unit prototypes for data about types of units. */
    private Map<String, Unit> prototypes = new LinkedHashMap<String, Unit>();

    public Unit createUnit(String type, int unitId, int playerId) {
        Unit u = new Unit(unitId);
        u.setOwnerId(playerId);
        setTypeValues(u, type);
        return u;
    }

    /**
     *
     * unit data from script units.lua.
     */
    public void setTypeValues(Unit u, String typeName) {
        WargusUnitType type = WargusUnitType.getType(typeName);
        if (type == null) {
            throw new RuntimeException("unknown unit type " + typeName);
        }
        // typeNameToInt will throw exception if typeName unknown.
        u.setType(type.getCode());
        u.setUnitTypeString(typeName);
        switch (type) {
            case GOLD_MINE:
                //Costs = {"time", 150},
                u.setMaxSpeed(0);
                u.setMaxHP(25500);
                // TileSize = {3, 3}, BoxSize = {95, 95},
                u.setExtent(3, 3);
                u.setArmor(20);
                break;
            case OIL_PATCH:
                u.setMaxSpeed(0);
                u.setMaxHP(0);
                break;
            case FOOTMAN:
                u.setMaxSpeed(10);
                u.setMaxHP(60);
                u.setArmor(2);
                u.setBasicDamage(6);
                u.setPiercingDamage(3);
                u.setMaxAttackRange(1);
                break;
            case PEASANT:
                u.setMaxSpeed(10);
                u.setMaxHP(30);
                u.setBasicDamage(3);
                u.setPiercingDamage(2);
                u.setMaxAttackRange(1);
                break;
            case BALLISTA:
                u.setMaxSpeed(5);
                u.setMaxHP(110);
                u.setBasicDamage(80);
                u.setMaxAttackRange(8);
                break;
            case KNIGHT:
                u.setMaxSpeed(13);
                u.setMaxHP(90);
                u.setArmor(4);
                u.setBasicDamage(8);
                u.setPiercingDamage(4);
                u.setMaxAttackRange(1);            
                break;
            case ARCHER:
                u.setMaxSpeed(10);
                u.setMaxHP(40);
                u.setBasicDamage(3);
                u.setPiercingDamage(6);
                u.setMaxAttackRange(4);
                break;
            case MAGE:
                u.setMaxSpeed(8);
                u.setMaxHP(60);
                u.setBasicDamage(0);
                u.setPiercingDamage(9);
                u.setMaxAttackRange(2);
                break;
            case PALADIN:
                u.setMaxSpeed(13);
                u.setMaxHP(90);
                u.setArmor(4);
                u.setBasicDamage(8);
                u.setPiercingDamage(4);
                u.setMaxAttackRange(1);
                break;
            case GRYPHON_RIDER:
                u.setMaxSpeed(14);
                u.setMaxHP(100);
                u.setArmor(5);
                u.setBasicDamage(0);
                u.setPiercingDamage(16);
                u.setMaxAttackRange(4);
                break;
            case TOWN_HALL:
                u.setMaxSpeed(0);
                u.setMaxHP(1200);
                u.setArmor(20);
                u.setExtent(4, 4);
                break;
            case MAGE_TOWER:
                u.setMaxSpeed(0);
                u.setMaxHP(500);
                u.setArmor(20);
                u.setBasicDamage(0);
                u.setExtent(3, 3);
                break;
            case BLACKSMITH:
                u.setMaxSpeed(0);
                u.setMaxHP(775);
                u.setArmor(20);
                u.setBasicDamage(0);
                break;
            case FARM:
                u.setMaxSpeed(0);
                u.setMaxHP(400);
                u.setArmor(20);
                u.setExtent(2, 2);
                // Supply = 4
                break;
            case HUMAN_BARRACKS:
                u.setMaxSpeed(0);
                u.setMaxHP(800);
                u.setArmor(20);
                u.setExtent(3, 3);
                break;
            case CHURCH:
                u.setMaxSpeed(0);
                u.setMaxHP(700);
                u.setArmor(20);
                u.setBasicDamage(0);
                u.setExtent(3, 3);
                break;
            case WATCH_TOWER:
                u.setMaxSpeed(0);
                u.setMaxHP(100);
                u.setArmor(20);
                u.setBasicDamage(0);
                u.setExtent(2, 2);
                break;
            case STABLES:
                u.setMaxSpeed(0);
                u.setMaxHP(500);
                u.setArmor(20);
                u.setExtent(3, 3);
                break;
            case INVENTOR:
                u.setMaxSpeed(0);
                u.setMaxHP(500);
                u.setArmor(20);
                u.setExtent(3, 3);
                break;
            case GRYPHON_AVIARY:
                u.setMaxSpeed(0);
                u.setMaxHP(500);
                u.setArmor(20);
                u.setExtent(3, 3);
                break;
            case LUMBER_MILL:
                u.setMaxSpeed(0);
                u.setMaxHP(600);
                u.setArmor(20);
                u.setExtent(3, 3);
                break;
            case KEEP:
                u.setMaxSpeed(0);
                u.setMaxHP(1400);
                u.setArmor(20);
                u.setBasicDamage(0);
                u.setExtent(4, 4);
                break;
            case CASTLE:
                u.setMaxSpeed(0);
                u.setMaxHP(1600);
                u.setArmor(20);
                u.setBasicDamage(0);
                u.setExtent(4, 4);
                break;
            case GUARD_TOWER:
                u.setMaxSpeed(0);
                u.setMaxHP(130);
                u.setArmor(20);
                u.setBasicDamage(4);
                u.setPiercingDamage(12);
                u.setMaxAttackRange(6);
                u.setExtent(2, 2);
                break;
            case CANNON_TOWER:
                u.setMaxSpeed(0);
                u.setMaxHP(160);
                u.setArmor(20);
                u.setBasicDamage(50);
                u.setMaxAttackRange(7);
                u.setExtent(2, 2);
                break;

            default:
                throw new RuntimeException("Unknown unit type '" + type + "'");
        }
    }

    public Unit getPrototype(String unitType) {
        Unit u = prototypes.get(unitType);
        if (u == null) {
            u = new Unit(-1);
            setTypeValues(u, unitType);
            prototypes.put(unitType, u);
        }
        return u;
    }
}
