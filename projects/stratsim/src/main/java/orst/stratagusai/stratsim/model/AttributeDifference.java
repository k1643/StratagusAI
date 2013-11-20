package orst.stratagusai.stratsim.model;

/**
 * A difference to apply to a Unit attribute.
 *
 * @author Brian
 */
public class AttributeDifference {
    protected int id;
    protected String name;
    protected int difference;

    public AttributeDifference() {}

    public AttributeDifference(int id, String name, int difference) {
        this.id = id;
        this.name = name;
        this.difference = difference;
    }

    public int getDifference() {
        return difference;
    }

    public void setDifference(int difference) {
        this.difference = difference;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
