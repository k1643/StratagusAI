package orst.stratagusai.stratplan.command;

import java.util.Collection;
import java.util.Set;
import orst.stratagusai.GameProxy;

/**
 * Acts as a factory to generate UnitCommands and translates UnitCommands to the
 * game proxy.
 * 
 * NOTE: Only supports unit commands.
 * @author Sean
 */
public class CommandTranslator {

    // Command factory methods =================================================

    public UnitCommand createComUnitMove(int unitID, int x, int y) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_MOVE);
        com.setUnitID(unitID);
        com.setLocation(x, y);
        return com;
    }

    public UnitCommand createComUnitAttack(int unitID, int attackMeID) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_ATTACK);
        com.setUnitID(unitID);
        com.setTargetID(attackMeID);
        return com;
    }

    public UnitCommand createComUnitStop(int unitID) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_STOP);
        com.setUnitID(unitID);
        return com;
    }

    public UnitCommand createComUnitBuild(int unitID, String unitToBuild,
            int x, int y) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_BUILD);
        com.setUnitID(unitID);
        com.setUnitToBuild(unitToBuild);
        com.setLocation(x, y);
        return com;
    }

    public UnitCommand createComUnitRepair(int unitID, int unitToRepair) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_REPAIR);
        com.setUnitID(unitID);
        com.setTargetID(unitToRepair);
        return com;
    }

    public UnitCommand createComUnitReturnRes(int unitID, int targetID) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_RETURN_RES);
        com.setUnitID(unitID);
        com.setTargetID(targetID);
        return com;
    }

    public UnitCommand createComUnitHarvestGold(int unitID, int targetID) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_HARVEST_GOLD);
        com.setUnitID(unitID);
        com.setTargetID(targetID);
        return com;
    }

    public UnitCommand createComUnitHarvestOil(int unitID, int targetID) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_HARVEST_OIL);
        com.setUnitID(unitID);
        com.setTargetID(targetID);
        return com;
    }

    public UnitCommand createComUnitHarvestWood(int unitID, int x, int y) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_HARVEST_WOOD);
        com.setUnitID(unitID);
        com.setLocation(x, y);
        return com;
    }

    public UnitCommand createComBuildingTrain(int unitID, String unitToBuild,
            int x, int y) {
        UnitCommand com = new UnitCommand(CommandType.BUILDING_TRAIN_UNIT);
        com.setUnitID(unitID);
        com.setUnitToBuild(unitToBuild);
        return com;
    }

    public UnitCommand createComFollow(int unitID, int leaderId) {
        UnitCommand com = new UnitCommand(CommandType.UNIT_FOLLOW);
        com.setUnitID(unitID);
        com.setTargetID(leaderId);
        return com;
    }

    // End UnitCommand factory methods =========================================

    /**
     * Translates UnitCommands and sends them via the GameProxy.
     * @param state holds the UnitCommand queue
     * @param game proxy
     */
    public static void sendUnitCommands(Collection<UnitCommand> commands,
            GameProxy game) {

        for (UnitCommand UnitCommand : commands) {
            sendUnitCommand(UnitCommand, game);
        }
    }

    public static void sendUnitCommand(UnitCommand UnitCommand,
            GameProxy game) {

        int unitID, targetID;
        int x, y;
        String stringArg;
        
        switch (UnitCommand.getType()) {
            case UNIT_MOVE:
                unitID = UnitCommand.getUnitID();
                x = UnitCommand.getLocation().getX();
                y = UnitCommand.getLocation().getY();
                game.myUnitCommandMove(unitID, x, y);
                break;
            case UNIT_ATTACK:
                unitID = UnitCommand.getUnitID();
                targetID = UnitCommand.getTargetID();
                game.myUnitCommandAttack(unitID, targetID);
                break;
            case UNIT_STOP:
                unitID = UnitCommand.getUnitID();
                game.myUnitCommandStopAllActions(unitID);
                break;
            case UNIT_BUILD:
                unitID = UnitCommand.getUnitID();
                stringArg = UnitCommand.getUnitToBuild();
                x = UnitCommand.getLocation().getX();
                y = UnitCommand.getLocation().getY();
                game.myUnitCommandBuildBuilding(unitID, stringArg, x, y);
                break;
            case UNIT_REPAIR:
                unitID = UnitCommand.getUnitID();
                targetID = UnitCommand.getTargetID();
                game.myUnitCommandRepair(unitID, targetID);
                break;
            case UNIT_RETURN_RES:
                unitID = UnitCommand.getUnitID();
                targetID = UnitCommand.getTargetID();
                game.myUnitCommandDropOffResources(unitID, targetID);
                break;
            case UNIT_HARVEST_GOLD:
                unitID = UnitCommand.getUnitID();
                targetID = UnitCommand.getTargetID();
                game.myUnitCommandHarvestGold(unitID, targetID);
                break;
            case UNIT_HARVEST_WOOD:
                unitID = UnitCommand.getUnitID();
                x = UnitCommand.getLocation().getX();
                y = UnitCommand.getLocation().getY();
                game.myUnitCommandHarvestWood(unitID, x, y);
                break;
            case UNIT_HARVEST_OIL:
                unitID = UnitCommand.getUnitID();
                targetID = UnitCommand.getTargetID();
                game.myUnitCommandHarvestOil(unitID, targetID);
                break;
            case BUILDING_TRAIN_UNIT:
                unitID = UnitCommand.getUnitID();
                stringArg = UnitCommand.getUnitToBuild();
                game.myUnitCommandTrainUnit(unitID, stringArg);
                break;
            case UNIT_FOLLOW:
                unitID = UnitCommand.getUnitID();
                targetID = UnitCommand.getTargetID();
                game.unitCommandFollow(unitID, targetID);
                break;
            default:
                String msg = "Command "+UnitCommand.getType()+" not supported";
                throw new RuntimeException(msg);
        }
    }

    public String translate(UnitCommand command) {
        // TODO: implement UnitCommand to string translation
        // for batch UnitCommands.
        throw new RuntimeException("not yet implemented");
    }
}
