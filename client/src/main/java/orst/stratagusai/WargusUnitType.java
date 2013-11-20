package orst.stratagusai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enumeration of Wargus unit types.  Units are defined in units.lua files.
 * Types are numbered by their order in the units.lua files.
 * data/scripts/units.lua is loaded first, then scripts/human/units.lua,
 * scripts/orc/units.lua.
 */
public enum WargusUnitType {
    GOLD_MINE ("unit-gold-mine", 11),
    OIL_PATCH("unit-oil-patch",12),
    FOOTMAN ("unit-footman", 23),
    PEASANT ("unit-peasant", 24),
    BALLISTA("unit-ballista", 26),
    KNIGHT("unit-knight", 27),
    ARCHER("unit-archer", 28),
    MAGE("unit-mage", 29),
    PALADIN("unit-paladin",30),
    GRYPHON_RIDER("unit-gryphon-rider",34),
    FARM ("unit-farm", 47),
    HUMAN_BARRACKS ("unit-human-barracks", 48),
    CHURCH("unit-church", 49),
    WATCH_TOWER ("unit-human-watch-tower", 50),
    STABLES ("unit-stables", 51),
    INVENTOR ("unit-inventor", 52),
    GRYPHON_AVIARY("unit-gryphon-aviary", 53),
    LUMBER_MILL ("unit-elven-lumber-mill", 55),
    FOUNDRY ("unit-human-foundry", 56),
    TOWN_HALL ("unit-town-hall", 57),
    MAGE_TOWER ("unit-mage-tower", 58),
    BLACKSMITH ("unit-human-blacksmith", 59),
    KEEP ("unit-keep", 62),
    CASTLE("unit-castle", 63),
    // "unit-start-location", 64
    GUARD_TOWER("unit-human-guard-tower", 65),
    CANNON_TOWER ("unit-human-cannon-tower", 66);

    private static Map<String,WargusUnitType> types = new LinkedHashMap<String,WargusUnitType>();

    static {
        types.put(GOLD_MINE.name, GOLD_MINE);
        types.put(OIL_PATCH.name, OIL_PATCH);
        types.put(FOOTMAN.name, FOOTMAN);
        types.put(PEASANT.name, PEASANT);
        types.put(BALLISTA.name, BALLISTA);
        types.put(KNIGHT.name, KNIGHT);
        types.put(ARCHER.name, ARCHER);
        types.put(MAGE.name, MAGE);
        types.put(PALADIN.name, PALADIN);
        types.put(GRYPHON_RIDER.name, GRYPHON_RIDER);
        types.put(FARM.name, FARM);
        types.put(HUMAN_BARRACKS.name, HUMAN_BARRACKS);
        types.put(CHURCH.name, CHURCH);
        types.put(WATCH_TOWER.name, WATCH_TOWER);
        types.put(STABLES.name, STABLES);
        types.put(INVENTOR.name, INVENTOR);
        types.put(GRYPHON_AVIARY.name, GRYPHON_AVIARY);
        types.put(LUMBER_MILL.name, LUMBER_MILL);
        types.put(FOUNDRY.name, FOUNDRY);
        types.put(TOWN_HALL.name, TOWN_HALL);
        types.put(MAGE_TOWER.name, MAGE_TOWER);
        types.put(BLACKSMITH.name, BLACKSMITH);
        types.put(KEEP.name, KEEP);
        types.put(CASTLE.name, CASTLE);
        types.put(GUARD_TOWER.name, GUARD_TOWER);
        types.put(CANNON_TOWER.name, CANNON_TOWER);
    }

    protected String name;
    protected int code;

    private WargusUnitType(String name, int code) {
        this.name = name;
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public int getOrdinal() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static WargusUnitType getType(String name) {
        return types.get(name);
    }
}
