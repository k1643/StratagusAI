package orst.stratagusai.taclp;

import orst.stratagusai.Unit;

/**
 *
 * @author Brian
 */
public class Assignment {
    Unit attacker;
    Unit target;

    public Assignment(Unit attacker, Unit target) {
        this.attacker = attacker;
        this.target = target;
    }

    @Override
    public String toString() {
        return "unit " + attacker.getUnitId() + " attacks " + target.getUnitId() + " " + target.getUnitTypeString();
    }


}
