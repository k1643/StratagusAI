package orst.stratagusai.stratplan.model;

/**
 * A MapNode is either a RegionNode or a PassageNode.
 * 
 * @author Brian
 */
public abstract class MapNode {
    /** MapNode has an Id so we can refer to it in a serialized GameState */
    protected int id = -1;

    public MapNode() {}

    public MapNode(int id) { this.id = id; }

    public int getId() {
        return id;
    }

    public abstract int getX();

    public abstract int getY();
}
