package orst.stratagusai.stratsim.model;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.command.CommandType;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.MapNode;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.util.Spatial;

/**
 *
 * @author Brian
 */
public class ActionMove extends Action {
    private static final Logger log = Logger.getLogger(ActionMove.class);
    private static final Logger simlog = Logger.getLogger("game_sim_event");
    protected List<MapNode> path;
    /** the next node on the path to move to. */
    protected MapNode nextNode;

    public ActionMove() {
        super(CommandType.UNIT_MOVE);
    }

    public ActionMove(Unit u, Region r) {
        this(u.getOwnerId(), u.getUnitId(), r.getId());
    }

    public ActionMove(int ownerId, int unitId, int regionId) {
        super(CommandType.UNIT_MOVE);
        this.ownerId = ownerId;
        setUnitID(unitId);
        setTargetID(regionId);
    }

    public int getRegionId() {
        return getTargetID();
    }
    
    @Override
    public ActionStatus exec(Simulator sim) {
        GameState game = sim.getGameState();

        Unit u = game.getUnit(getUnitID());
        if (u == null) {
            log.error("Unit " + getUnitID() + " not in game state.");
            status = ActionStatus.CANNOT_COMPLETE;
            return status;
        }

        if (path == null) {
            // get path from current region to target region.
            path = getPath(sim, u);
            if (path.size() == 0) {
                status = ActionStatus.COMPLETE;  // move completed
                return status;
            } else {
                nextNode = path.get(0); // first target node is passage node exiting current region.
                assert nextNode != null : "unknown next node.";
            }
        } else if (nextNode == null) {
            // path not null, but null next node, so we must have already moved to end of path.
            status = ActionStatus.COMPLETE;  // move completed
            return status;
        }

        // advance unit's position along graph edge.
        int d = getDisplacement(sim, u);

        // if displacement > distance from unit to next node
        //  set unit position to next node postion
        //  set next node
        // else
        //   set unit position to current position plus displacement toward next node.
        //
        int nextX;
        int nextY;
        if (d > Spatial.distance(u.getLocX(), u.getLocY(), nextNode.getX(), nextNode.getY())) {
            nextX = nextNode.getX();
            nextY = nextNode.getY();
            nextNode = getNextNode(nextNode);
        } else {
            RealVector v = getDisplacementVector(d, u, nextNode);
            nextX = u.getLocX() + (int) Math.ceil(v.getEntry(0));
            nextY = u.getLocY() + (int) Math.ceil(v.getEntry(1));
        }
        changeLocation(sim, u, nextX, nextY);
        
        if (nextNode == null) {
            status = ActionStatus.COMPLETE;
        } else {
            status = ActionStatus.ACTIVE;
        }
        return status;
    }


    private List<MapNode> getPath(Simulator sim, Unit u) {
        GameState state = sim.getGameState();
        GameMap map = state.getMap();
        Region src = map.getRegion(u);
        assert getRegionId() != -1 : "destination region must be specified.";
        if (src.getId() == getRegionId()) {
            return new ArrayList<MapNode>();
        }
        Region dest = map.getRegion(getRegionId());
        List<Passage> psg = state.getMap().getShortestPath(src, dest);
        if (psg.isEmpty()) {
            log.warn("empty path from " + src + " to " + dest + ".");
        }
        // convert to list of map nodes.
        List<MapNode> nodes = new ArrayList<MapNode>();
        MapNode curr = null;
        for (Passage p : psg) {
            if (curr == null || curr == p.getRegionNode()) {
                // skip first region node, we want unit to exit to the region.
                curr = p.getPassageNode();
            } else {
                curr = p.getRegionNode();
            }
            nodes.add(curr);
        }
        return nodes;
    }

    /**
     * from scripts/human/units.lua:
     *
     * unit             Speed
     * unit-peasant     10
     * unit-knight      13
     * unit-archer      10
     * unit-footman     10
     * unit-ballista     5
     *
     * unit-peon at speed 10 moves across a tile in 16 game cycles.
     *
     * By logging movement in the stratagus engine I found that
     * a unit's tiles/cycle are approximately related to a unit's Speed by
     * tiles/cycle = Speed/5 * 1 tile/32 subtiles
     *
     * at Simulator.CYCLE_INCREMENT = 50 we have
     * unit-peasant  displacement = 3
     * unit-knight   displacement = 4
     * unit-ballista displacement = 2
    */
    private int getDisplacement(Simulator sim, Unit u) {
        int maxspeed = u.getMaxSpeed();
        if (maxspeed == 0) {
            throw new RuntimeException("Attempt to move immovable unit " + u + " at cycle " + sim.getCycle());
        }
        return (int) Math.round(maxspeed/5.0 * 1/32.0 * SimController.CYCLE_INCREMENT);
    }

    private void changeLocation(Simulator sim, Unit u, int x, int y) {
        // difference applied in Simulator.applyDifference().
        sim.addChange(new AttributeDifference(u.getUnitId(),
                                              "LocX",
                                              x - u.getLocX()));
        sim.addChange(new AttributeDifference(u.getUnitId(),
                                              "LocY",
                                              y - u.getLocY()));
        simlog.info(String.format("%d\tmove of unit %d:%d to %d,%d.",
                sim.getState().getCycle(),u.getOwnerId(),u.getUnitId(),x,y));
    }

    private MapNode getNextNode(MapNode node) {
        int i = path.indexOf(node);
        assert i != -1 : "node " + node + " not on path.";
        if (i < path.size() - 1) {
            return path.get(i+1);
        } else {
            return null;
        }
    }

    private RealVector getDisplacementVector(int d, Unit u, MapNode nextNode) {
        RealVector v = new ArrayRealVector(2);
        v.setEntry(0, nextNode.getX() - u.getLocX());
        v.setEntry(1, nextNode.getY() - u.getLocY());

        double norm = v.getNorm();
        assert norm > 0;
        // v <-  v * d
        //       -----
        //        |v|
        //
        v.mapMultiplyToSelf(d / norm);
        return v;
    }
}
