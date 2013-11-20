package orst.stratagusai.stratsim.analysis;

import java.util.LinkedHashSet;
import java.util.Set;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.Region;

/**
 *
 * @author Brian
 */
public class RegionAnalysis {

    /**
     * get regions that have allied production units
     * 
     */
    public static Set<Region> getBases(int playerId, GameState s) {
        Set<Region> bases = new LinkedHashSet<Region>();
        for (Region r : s.getMap().getRegions()) {
            for (Unit u : s.getUnits(r)) {
                if (u.getOwnerId() == playerId &&
                    (UnitAnalysis.isBuilding(u) || UnitAnalysis.isPeasant(u)) &&
                    !u.isDead()) {
                    bases.add(r);
                    break;
                }
            }
        }
        return bases;
    }

    /**
     * get regions that have enemy production units.
     * 
     */
    public static Set<Region> getEnemyBases(int playerId, GameState s) {
        Set<Region> bases = new LinkedHashSet<Region>();
        for (Region r : s.getMap().getRegions()) {
            for (Unit u : s.getUnits(r)) {
                if (u.getOwnerId() != playerId &&
                    u.getOwnerId() != GameState.NEUTRAL_PLAYER &&
                    (UnitAnalysis.isBuilding(u) || UnitAnalysis.isPeasant(u)) &&
                    !u.isDead()) {
                    bases.add(r);
                    break;
                }
            }
        }
        return bases;
    }

    public static int getProductionStrength(int playerId, GameState s, Region r) {
        int strength = 0;
        Set<Unit> units = s.getUnits(r);
        for (Unit u : units) {
            if (u.getOwnerId() == playerId && UnitAnalysis.isBuilding(u)) {
                strength += u.getHitPoints();
            }
        }
        return strength;
    }

    public static int getEnemyProductionStrength(int playerId, GameState s, Region r) {
        int strength = 0;
        Set<Unit> units = s.getUnits(r);
        for (Unit u : units) {
            if (u.getOwnerId() == playerId || u.getOwnerId() == GameState.NEUTRAL_PLAYER) {
                continue;
            }
            if (UnitAnalysis.isBuilding(u) || UnitAnalysis.isPeasant(u)) {
                strength += u.getHitPoints();
            }
        }
        return strength;
    }
}
