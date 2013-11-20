package orst.stratagusai.stratplan.model;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.collections15.Transformer;
import org.apache.log4j.Logger;
import orst.stratagusai.Unit;

/**
 * 
 * 
 */
public class GameMap {
    private static final Logger log = Logger.getLogger(GameMap.class);

    protected char[][] cells = null;

    /* transformer extracts passage extent as a weight for the shortest-path
     * algorithm.
     */
    protected Transformer<Passage, Integer> wtTransformer =
    new Transformer<Passage,Integer>() {
        public Integer transform(Passage edge) {
            return edge.getExtent();
        }
    };

    /** connectivity graph of the game map */
    protected Graph<MapNode,Passage> graph = new UndirectedSparseGraph();

    /** shortest path caches path information.  Remember to reset() after
     *  each graph change.
     */
    protected DijkstraShortestPath<MapNode,Passage> pathFinder =
                new DijkstraShortestPath(graph, wtTransformer);

    /** regions by ID */
    protected Map<Integer,Region> regions = new LinkedHashMap<Integer,Region>();

    /** map name can be provided for logging and documentation. */
    protected String name;
    
    public GameMap() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public char[][] getCells() {
        return cells;
    }

    public void setCells(char[][] cells) {
        this.cells = cells;
    }

    public char getCell(int x, int y) {
        assert cells != null : "map cells not set.";
        return cells[y][x];
    }

    public int getExtentX() {
        assert cells != null : "map cells not set.";
        return cells[0].length;
    }

    public int getExtentY() {
        assert cells != null : "map cells not set.";
        return cells.length;
    }

    public void setRegions(Set<Region> regions) {
        this.regions.clear();
        for (Region r : regions) {
            assert r.getId() != -1 : "region must have an id.";
            this.regions.put(r.getId(), r);
        }
    }

    public Set<Region> getRegions() {
        Set<Region> rs = new LinkedHashSet<Region>();
        rs.addAll(regions.values());
        return rs;
    }

    public void addRegion(Region region) {
        assert region.getId() != -1 : "region must have an id.";
        regions.put(region.getId(), region);
    }

    public Region getRegion(int id) {
        return regions.get(id);
    }

    public void setPassages(Set<Passage> passages) {
        for (Passage p : passages) {
            graph.addEdge(p, p.getRegionNode(), p.getPassageNode());
        }
        pathFinder.reset();
    }

    public Set<Passage> getPassages() {
         // sort passages so they are iterated in a repeatable order.
        Comparator cmp = new Comparator() {
            public int compare(Object o1, Object o2) {
                Passage p1 = (Passage) o1;
                Passage p2 = (Passage) o2;
                int i = p1.getRegionNode().getId() - p2.getRegionNode().getId();
                if (i != 0) {
                    return i;
                }
                i = p1.getPassageNode().getId() - p2.getPassageNode().getId();
                return i;
            }
        };
        Set<Passage> passages = new TreeSet<Passage>(cmp);
        passages.addAll(graph.getEdges());
        return passages;
    }

    public void addPassage(Passage p) {
        assert p != null;
        assert p.getRegionNode() != null : "region node not set.";
        assert p.getPassageNode() != null : "passage node not set.";
        // set bidirectional relationshiop.
        Region r = p.getRegionNode().getRegion();
        r.setNode(p.getRegionNode());
        
        graph.addEdge(p, p.getRegionNode(), p.getPassageNode());
        pathFinder.reset();
    }

    public Set<MapNode> getMapNodes() {
         // sort nodes so they are iterated in a repeatable order.
        Comparator nodeCmp = new Comparator() {
            public int compare(Object o1, Object o2) {
                MapNode n1 = (MapNode) o1;
                MapNode n2 = (MapNode) o2;
                if (n1 instanceof RegionNode && n2 instanceof PassageNode) {
                        return -1;
                } else if (n1 instanceof PassageNode && n2 instanceof RegionNode) {
                        return 1;
                }
                return n1.getId() - n2.getId();
            }
        };
        Set<MapNode> ns = new TreeSet<MapNode>(nodeCmp);
        ns.addAll(graph.getVertices());
        return ns;
    }

    /**
     * get distance between region centers as represented in the connectivity
     * graph.
     */
    public float getDistance(Region r1, Region r2) {
        List<Passage> path = getShortestPath(r1, r2);
        float d = 0;
        for (Passage p : path) {
            d += p.getExtent();
        }
        return d;
    }

    public List<Passage> getShortestPath(Region r1, Region r2) {
        assert r1 != null : "first region cannot be null.";
        assert r1.getNode() != null : "first region has null region node: " + r1 + ".";
        assert r2.getNode() != null : "second region has null region node: " + r2 + ".";
       
        List<Passage> l = pathFinder.getPath(r1.getNode(), r2.getNode());
        return l;
    }

    
    /**
     * Get regions that are dominated by enemies.
     *
     * TODO: too much analysis in GameMap class?  Move to another class?
     */
    Set<Region> getEnemyRegions(Collection<Unit> units, int playerId) {
        Set<Region> rs = new LinkedHashSet<Region>();
        for(Region r : regions.values()) {
            int controller = getController(units, r);
            if (controller != -1 && controller != playerId) {
                rs.add(r);
            }
        }
        return rs;
    }

    /**
     * return id of player who dominates the given region.  Return -1
     * for no one.
     */
    protected int getController(Collection<Unit> us, Region r) {
        // strength of each player in the region
        Map<Integer,Integer> strength = new LinkedHashMap<Integer,Integer>();
        Set<Unit> units = getUnits(us, r);
        int firstStrength = 0;
        int secondStrength = 0;
        int controller = -1;
        final int NEUTRAL_PLAYER = 15;  // TODO: where to put this constant?
        for (Unit u : units) {
            int owner = u.getOwnerId();
            if (owner == NEUTRAL_PLAYER) {
                continue;
            }
            int hp = u.getHitPoints();
            if (!strength.containsKey(owner)) {
                strength.put(owner, hp);
            } else {
                hp = strength.get(owner) + hp;
                strength.put(owner, hp);
            }
            if (hp > firstStrength) {
                firstStrength = hp;
                controller = owner;
            } else if (hp > secondStrength) {
                secondStrength = hp;
            }
        }
        // who controls the region?
        if (firstStrength > secondStrength*2) {
            return controller;
        }
        return -1;  // no player dominates
    }

    /**
     * get units at given region.
     */
    Set<Unit> getUnits(Collection<Unit> units, Region r) {
        Set<Unit> us = new LinkedHashSet<Unit>();
        for (Unit u : units) {
            if (r.contains(u.getLocX(), u.getLocY())) {
                us.add(u);
            }
        }
        return us;
    }

    /**
     * get units at given region.
     */
    Set<Unit> getUnits(Collection<Unit> units, int playerId, Region r) {
        assert r != null : "region cannot be null.";
        assert units != null;
        Set<Unit> us = new LinkedHashSet<Unit>();
        for (Unit u : units) {
            if (u.getOwnerId() == playerId &&
                r.contains(u.getLocX(), u.getLocY())) {
                us.add(u);
            }
        }
        return us;
    }

    /**
     * Get the Region that the unit is in.
     */
    public Region getRegion(Unit u) {
        assert u != null : "unit cannot be null";
        return getRegion(u.getLocX(), u.getLocY());
    }

    public Region getRegion(int x, int y) {
        for (Region r : regions.values()) {
            if (r.contains(x, y)) {
                return r;
            }
        }
        String msg = "location (" + x + ","+ y + ") not in map region.";
        log.error(msg);
        throw new RuntimeException(msg);
    }

    public List<char[][]> getTerrain(Region region) {
        List<Rectangle> rs = region.getRectangles();
        List<char[][]> terrain = new ArrayList<char[][]>();
        for (Rectangle r : rs) {
            char[][] t = new char[r.getExtentY()][r.getExtentX()];
            for (int y = 0; y < r.getExtentY(); y++) {
                for (int x = 0; x < r.getExtentX(); x++) {
                    t[y][x] = cells[r.getMinY()+ y][r.getMinX()+x];
                }
            }
            terrain.add(t);
        }
        return terrain;
    }
}
