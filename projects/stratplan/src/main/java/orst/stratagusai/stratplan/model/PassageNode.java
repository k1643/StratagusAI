package orst.stratagusai.stratplan.model;

/**
 * A passage between regions on a map.  Passages are at chokepoints or
 * at transitions between terrain.  A land-water boundary may be a
 * passage, because units may have to change transportation at the
 * boundary.
 *
 * @author Brian
 */
public class PassageNode extends MapNode {

    /** extent is related to how long it takes to cross a Passage */
    protected int extent;

    protected int x;

    protected int y;

    public PassageNode() {}

    public PassageNode(int id) {
        this.id = id;
    }

    public int getExtent() {
        return extent;
    }

    public void setExtent(int extent) {
        this.extent = extent;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return String.format("(PassageNode :id %d (%d %d))", id, x, y);
    }


}
