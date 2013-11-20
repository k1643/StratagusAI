package orst.stratagusai.stratsim.planner;

/**
 *
 * @author Brian
 */
public class UnitRequirement {
    private String type;
    private int amount;

    public UnitRequirement() {}

    public UnitRequirement(String type) {
        this.type = type;
        this.amount = 1;
    }

    public UnitRequirement(String type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UnitRequirement other = (UnitRequirement) obj;
        if ((this.type == null) ? (other.type != null) : !this.type.equals(other.type)) {
            return false;
        }
        if (this.amount != other.amount) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 29 * hash + this.amount;
        return hash;
    }

    @Override
    public String toString() {
        return "[" + type + " " + amount + "]";
    }
}
