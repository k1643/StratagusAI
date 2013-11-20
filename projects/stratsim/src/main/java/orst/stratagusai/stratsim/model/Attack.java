package orst.stratagusai.stratsim.model;

import org.apache.log4j.Logger;
import orst.stratagusai.Unit;

/**
 * implement common attack functions.
 *
 * @author Brian
 */
class Attack {
    private static Logger log = Logger.getLogger(Attack.class);

    /**
     * Caller of this function is responsible for assumption that
     * ally and enemy are in the same Region.
     *
     */
    public static int getDamage(Unit ally, Unit enemy) {
        if (ally.isDead()) {
            log.warn("attacker is dead.");
            return 0;
        }
        // calculate damage.  See missile.cpp CalculateDamageStats() for
        // damage calculation. Basically
        //     damage = max(basic_damage - armor, 1) + piercing_damage
        //
        // See UnitAbstractor for code to aggregate property values.
        //
        // Strength(enemy) <- Strength(enemy)
        //                     - (max(Damage(ally)-Armor(enemy),|ally|)
        //
        //                   basic  piercing
        //                  damage    damage    armor
        // FOOTMAN               6         3        0
        // TOWNHALL              0                 20
        //
        int damage = Math.max(ally.getBasicDamage() - enemy.getArmor(),1) + ally.getPiercingDamage();

        // adjust for number of cycles since last damage calculation.
        
        if (damage <= 0) {
            log.debug("damage=" + damage + ". ally basic damage:" + ally.getBasicDamage() + " enemy armor:" + enemy.getArmor());
            return 0;
        } else if (damage > enemy.getHitPoints()) {
            damage = enemy.getHitPoints();
        } else {
            // ally may get two or more strikes in 50 cycles.
            damage = 2*damage;
        }
        return damage;
    }
}
