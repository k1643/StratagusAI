package orst.stratagusai.stratplan.model;

import orst.stratagusai.util.Spatial;

/**
 * A passage is a connection between a RegionNode and a PassageNode.
 * 
 * 
 */
public class Passage {
    protected RegionNode regionNode;
    protected PassageNode passageNode;

    /** the length of the passage.  Used for estimating path lengths */
    protected int extent = 1;

    public Passage() {}

    public Passage(RegionNode rn, PassageNode pn) {
        this.regionNode = rn;
        this.passageNode = pn;
        extent = (int) Math.round(Spatial.distance(
                                    rn.getRegion().getX(),
                                    rn.getRegion().getY(),
                                    pn.getX(),
                                    pn.getY()));
    }

    public Passage(RegionNode node1, PassageNode node2, int extent) {
        this.regionNode = node1;
        this.passageNode = node2;
        this.extent = extent;
    }

    public PassageNode getPassageNode() {
        return passageNode;
    }

    public void setPassageNode(PassageNode passageNode) {
        this.passageNode = passageNode;
    }

    public RegionNode getRegionNode() {
        return regionNode;
    }

    public void setRegionNode(RegionNode regionNode) {
        this.regionNode = regionNode;
    }

    public int getExtent() {
        return extent;
    }

    public void setExtent(int extent) {
        this.extent = extent;
    }
}
