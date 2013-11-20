package orst.stratagusai.stratplan.model;

/**
 *
 * @author Brian
 */
public class RegionNode extends MapNode {
    protected Region region;

    public RegionNode() {}

    public RegionNode(Region r) {
        setRegion(r);
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
        id = region.getId();
    }

    public int getX() {
        return region.getX();
    }

    public int getY() {
        return region.getY();
    }
}
