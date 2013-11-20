package orst.stratagusai.stratplan.persist;

import java.io.IOException;
import java.io.Writer;
import orst.stratagusai.Unit;

/**
 *
 * @author Brian
 */
public class UnitWriter implements ObjectWriter {

    public void write(Writer out, String indent, Object o) throws IOException {
        Unit u = (Unit) o;
        out.write("(:Unit");
        out.write(" :unitId " + u.getUnitId());
        out.write(" :ownerId " + u.getOwnerId());
        out.write(" :RAmount " + u.getRAmount());
        out.write(" :CurrentTarget " + u.getCurrentTarget());
        out.write(" :HitPoints " + u.getHitPoints());
        out.write(" :LocX " + u.getLocX());
        out.write(" :LocY " + u.getLocY());
        out.write(" :Status " + u.getStatus());
        out.write(" :Armor " + u.getArmor());
        out.write(" :Damage " + u.getBasicDamage());
        out.write(" :PiercingDmg " + u.getPiercingDamage());
        out.write(" :StatusArg1 " + u.getStatusArg1());
        out.write(" :StatusArg2 " + u.getStatusArg2());
        out.write(" :Type " + u.getType());
        out.write(" :UnitTypeString " + u.getUnitTypeString());
        out.write(")");
    }

}
