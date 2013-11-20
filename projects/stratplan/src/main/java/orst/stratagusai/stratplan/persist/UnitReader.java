package orst.stratagusai.stratplan.persist;

import java.util.Map;
import orst.stratagusai.Unit;
import orst.stratagusai.WargusUnitPrototypes;
import orst.stratagusai.WargusUnitType;

/**
 *
 * @author Brian
 */
public class UnitReader {

    protected WargusUnitPrototypes prototypes = new WargusUnitPrototypes();

    /**
     *
     * @param ps unit properties (attributes)
     * @return
     */
    public Unit readUnit(Map<String,Object> ps) {
        int unitId = Integer.parseInt(ps.get("unitId").toString());
        int ownerId = Integer.parseInt(ps.get("ownerId").toString());
        String typeName = ps.get("UnitTypeString").toString();
        Unit u = createUnit(typeName, unitId, ownerId);
        setAttributes(u, ps);
        return u;
    }

    protected Unit createUnit(String typeName, int unitId, int ownerId) {
        WargusUnitType type = WargusUnitType.getType(typeName);
        Unit u;
        if (type != null) {
            u = prototypes.createUnit(typeName, unitId, ownerId);
        } else {
            // sometimes unit type is something like
            // 'unit-destroyed-3x3-place-water' to show dying animation.
            // can't get type properties for these type names.
            u = new Unit(unitId);
            u.setOwnerId(ownerId);
        }
        return u;
    }

    protected void setAttributes(Unit u, Map<String,Object> ps) {
        u.setHitPoints(Integer.parseInt(ps.get("HitPoints").toString()));
        u.setStatus(Integer.parseInt(ps.get("Status").toString()));
        u.setRAmount(Integer.parseInt(ps.get("RAmount").toString()));
        u.setStatusArg2(Integer.parseInt(ps.get("StatusArg2").toString()));
        u.setLocX(Integer.parseInt(ps.get("LocX").toString()));
        u.setLocY(Integer.parseInt(ps.get("LocY").toString()));
        u.setStatusArg1(Integer.parseInt(ps.get("StatusArg1").toString()));
        int armor = Integer.parseInt(ps.get("Armor").toString());
        int dmg = Integer.parseInt(ps.get("Damage").toString());
        int piercingDmg = Integer.parseInt(ps.get("PiercingDmg").toString());
        u.setArmor(armor);
        u.setBasicDamage(dmg);
        u.setPiercingDamage(piercingDmg);
    }
}
