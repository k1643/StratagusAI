package orst.stratagusai.stratsim.io;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import orst.stratagusai.stratplan.persist.ObjectWriter;
import orst.stratagusai.stratsim.model.GroupSim;

/**
 *
 * @author Brian
 */
public class UnitGroupSimWriter implements ObjectWriter {

    public void write(Writer out, String indent, Object o) throws IOException {
        GroupSim u = (GroupSim) o;
        out.write("(:UnitGroupSim");
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
        out.write("(:unit-counts");
        Map<String,Integer> counts = u.getUnitTypes();
        for (Map.Entry<String,Integer> entry : counts.entrySet()) {
            out.write(" ");
            out.write(entry.getKey());
            out.write(" ");
            int c = entry.getValue();
            out.write(String.valueOf(c));
        }
        out.write(")");
        out.write(")");
    }

}
