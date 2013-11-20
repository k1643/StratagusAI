package orst.stratagusai.stratplan.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A region of a map.  The boundaries of a region are impassable terrain and
 * chokepoints.
 *
 * @author Brian
 */
public class Region {

    /** 
     * regions have an ID so we can refer to them in the serialized game 
     * state, and in task arguments.
     */
    protected int id = -1;

    protected List<Rectangle> rectangles = new ArrayList<Rectangle>();

    /** center x */
    protected int x;

    /** center y */
    protected int y;

    /** bidirectional relationship */
    protected RegionNode node;

    protected boolean isChokepoint;

    public Region() {}

    public Region(int id) {
        this.id = id;
        node = new RegionNode(this);
    }

    public Region(int id, Rectangle...rs) {
        this.id = id;
        node = new RegionNode(this);
        for (Rectangle r : rs) {
            rectangles.add(r);
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        node = new RegionNode(this);
    }

    public void addRectangle(Rectangle r) {
        rectangles.add(r);
    }

    public List<Rectangle> getRectangles() {
        return rectangles;
    }

    public void setRectangles(List<Rectangle> rectangles) {
        this.rectangles = rectangles;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public RegionNode getNode() {
        return node;
    }

    public void setNode(RegionNode node) {
        assert node != null : "RegionNode can not be null";
        this.node = node;
    }

    public boolean isChokepoint() {
        return isChokepoint;
    }

    public void setChokepoint(boolean isChokepoint) {
        this.isChokepoint = isChokepoint;
    }

    public boolean contains(int locX, int locY) {
        for (Rectangle r : rectangles) {
            if (r.contains(locX, locY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        String s = "[Region :id " + getId();
        if (isChokepoint) {
            s += " :chokepoint";
        }
        return s + "]";
    }
}
