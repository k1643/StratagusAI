package orst.stratagusai.stratplan.command;

import orst.stratagusai.stratplan.model.Location;

/**
 * Represents a unit command with a given CommandType.
 * @author Sean
 */
public class UnitCommand {

    private CommandType type;
    private int unitID = -1;
    private int targetID = -1;
    private Location location;
    private String unitToBuild;

    public UnitCommand(CommandType type) {
        this.type = type;
    }

    public CommandType getType() {
        return type;
    }

    public void setType(CommandType type) {
        this.type = type;
    }

    public int getUnitID() {
        return unitID;
    }

    public void setUnitID(int unitID) {
        this.unitID = unitID;
    }

    public int getTargetID() {
        return targetID;
    }

    public void setTargetID(int targetID) {
        this.targetID = targetID;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setLocation(int x, int y) {
        this.location = new Location(x, y);
    }

    public String getUnitToBuild() {
        return unitToBuild;
    }

    public void setUnitToBuild(String unitToBuild) {
        this.unitToBuild = unitToBuild;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UnitCommand other = (UnitCommand) obj;
        if (this.type != other.type && (this.type == null || !this.type.equals(other.type))) {
            return false;
        }
        if (this.unitID != other.unitID) {
            return false;
        }
        if (this.targetID != other.targetID) {
            return false;
        }
        if (this.location != other.location && (this.location == null || !this.location.equals(other.location))) {
            return false;
        }
        if ((this.unitToBuild == null) ? (other.unitToBuild != null) : !this.unitToBuild.equals(other.unitToBuild)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 97 * hash + this.unitID;
        hash = 97 * hash + this.targetID;
        hash = 97 * hash + (this.location != null ? this.location.hashCode() : 0);
        hash = 97 * hash + (this.unitToBuild != null ? this.unitToBuild.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        String s = "[UnitCommand " +
                type +
                " unitId=" + unitID;
        if (targetID != -1) {
            s += " targetId=" + targetID;
        } else if (location != null) {
            s += " location=("+location.getX()+","+location.getY()+")";
        }
        return s + "]";
    }


}
