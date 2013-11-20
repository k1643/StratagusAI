package orst.stratagusai.stratplan.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import orst.stratagusai.Unit;

/**
 * Calculate distance on a plane.  Keeps sorted lists of interval endpoints
 * for each dimension.
 *
 * Sweep and Prune described in
 *
 * "I-COLLIDE: An Interactive and Exact Collision Detection System for
 * Large-Scale Environments". Cohen, Lin, Manocha, Ponamgi.
 *
 * See also http://en.wikipedia.org/wiki/Collision_detection
 *
 * @author bking
 */
public class Plane {
    /** extent in X dimension */
    protected int extentX;

    /** extent in Y dimension */
    protected int extentY;

    /** use center points to determine proximity */
    protected List<Unit> sortedX = new ArrayList<Unit>();

    /** use center points to determine proxitmity */
    protected List<Unit> sortedY = new ArrayList<Unit>();

    /** order on x coordinate ascending */
    protected Comparator<Unit> compareX = new Comparator<Unit>() {
        public int compare(Unit o1, Unit o2) {
            return o1.getLocX() - o2.getLocX();
        }
    };

    protected Comparator<Unit> compareY = new Comparator<Unit>() {
        public int compare(Unit o1, Unit o2) {
            return o1.getLocY() - o2.getLocY();
        }
    };

    public Plane(int extentX, int extentY) {
        this.extentX = extentX;
        this.extentY = extentY;
    }

    public int getExtentX() {
        return extentX;
    }

    public int getExtentY() {
        return extentY;
    }

    public void add(Unit o) {
        sortedX.add(o);
        sortedY.add(o);
        update();
    }

    public void remove(Unit o) {
        sortedX.remove(o);
        sortedY.remove(o);
    }

    public void update() {
        Collections.sort(sortedX, compareX);
        Collections.sort(sortedY, compareY);
    }

    /**
     * get nearest object to the given position
     */
    public Unit getNearest(int x, int y) {
        Collection<Unit> near = new ArrayList<Unit>();
        getNearestX(x, near);
        getNearestY(y, near);
        // find closest of the neighbors.
        Unit closest = null;
        double min = Double.POSITIVE_INFINITY;
        for (Unit n : near) {
            double d = distance(x, y, n.getLocX(), n.getLocY());
            if (n != null && d < min) {
                min = d;
                closest = n;
            }
        }
        return closest;
    }

   private void getNearestX(int x, Collection near) {
        // search forward for nearby objects
        int min = Integer.MAX_VALUE;
        Set<Unit> s = new LinkedHashSet<Unit>();
        for (int j = 0; j < sortedX.size(); j++) {
            Unit n = sortedX.get(j);
            int d = Math.abs(x - n.getLocX());
            if (d <= min) {
                if (d < min) {
                    s.clear();
                    min = d;
                }
                s.add(n);
            }
        }
        near.addAll(s);
    }

    private void getNearestY(int y, Collection near) {
        // search forward for nearby objects
        int min = Integer.MAX_VALUE;
        Set<Unit> s = new LinkedHashSet<Unit>();
        for (int j = 0; j < sortedX.size(); j++) {
            Unit n = sortedX.get(j);
            int d = Math.abs(y - n.getLocY());
            if (d <= min) {
                if (d < min) {
                    s.clear();
                    min = d;
                }
                s.add(n);
            }
        }
        near.addAll(s);
    }


    /**
     * get nearest object that is not the given object.
     */
    public Unit getNearest(Unit o) {
        Collection<Unit> near = new ArrayList<Unit>();
        getNearestX(o, near);
        getNearestY(o, near);
        // find closest of the neighbors.
        Unit closest = null;
        double min = Double.POSITIVE_INFINITY;
        for (Unit n : near) {
            double d = distance(o,n);
            if (n != null && d < min) {
                min = d;
                closest = n;
            }
        }
        return closest;
    }


    private void getNearestX(Unit o, Collection near) {
        int i = sortedX.indexOf(o);
        if (i < 0) {
            throw new RuntimeException("Object " + o + " not found on plane");
        }
        // search backward for nearby objects
        int x = Integer.MIN_VALUE;
        for (int j = i-1; j > 0; j--) {
            Unit n = sortedX.get(j);
            if (x == Integer.MIN_VALUE) {
                x = n.getLocX();
            } else if (n.getLocX() != x) {
                break;
            }
            near.add(n);
        }
        // search forwardward for nearby objects
        x = Integer.MIN_VALUE;
        for (int j = i+1; j < sortedX.size(); j++) {
            Unit n = sortedX.get(j);
            if (x == Integer.MIN_VALUE) {
                x = n.getLocX();
            } else if (n.getLocX() != x) {
                break;
            }
            near.add(n);
        }
    }

    private void getNearestY(Unit o, Collection near) {
        int i = sortedY.indexOf(o);
        if (i < 0) {
            throw new RuntimeException("Object " + o + " not found on plane");
        }
        // search backward for nearby objects
        int y = Integer.MIN_VALUE;
        for (int j = i-1; j > 0; j--) {
            Unit n = sortedY.get(j);
            if (y == Integer.MIN_VALUE) {
                y = n.getLocY();
            } else if (n.getLocY() != y) {
                break;
            }
            near.add(n);
        }
        // search forwardward for nearby objects
        y = Integer.MIN_VALUE;
        for (int j = i+1; j < sortedY.size(); j++) {
            Unit n = sortedY.get(j);
            if (y == Integer.MIN_VALUE) {
                y = n.getLocY();
            } else if (n.getLocY() != y) {
                break;
            }
            near.add(n);
        }
    }

    /**
     * get the units that are in range of the given units.
     * Returned set does not include the argument units.
     *
     * @param us
     * @param range
     * @return
     */
    public Set<Unit> getInRange(Set<Unit> us, float range) {
        Set<Unit> n = new LinkedHashSet<Unit>();
        for (Unit u : us) {
            n.addAll(getInRange(u, range));
        }
        n.removeAll(us);        
        return n;
    }

    public Set<Unit> getInRange(Unit o, float range) {
        Set<Unit> near = new LinkedHashSet<Unit>();
        // get neighbors in rectangular region
        getNeighborX(o, range, near);
        getNeighborY(o, range, near);
        // find which neighbors are actually in range
        Iterator<Unit> itr = near.iterator();
        while (itr.hasNext()) {
            Unit n = itr.next();
            double d = distance(o,n);
            if (d > range) {
                itr.remove();
            }
        }
        return near;
    }

    private void getNeighborX(Unit u, double range, Collection near) {
        int i = sortedX.indexOf(u);
        if (i < 0) {
            throw new RuntimeException("Object " + u + " not found on plane");
        }
        // search backward for nearby objects
        int x = u.getLocX();
        for (int j = i-1; j > 0; j--) {
            Unit n = sortedX.get(j);
            if (Math.abs(x - n.getLocX()) > range) {
                break;
            }
            near.add(n);
        }
        // search forward for nearby objects
        for (int j = i+1; j < sortedX.size(); j++) {
            Unit n = sortedX.get(j);
            if (Math.abs(x - n.getLocX()) > range) {
                break;
            }
            near.add(n);
        }
    }

    private void getNeighborY(Unit u, double range, Collection near) {
        int i = sortedY.indexOf(u);
        if (i < 0) {
            throw new RuntimeException("Object " + u + " not found on plane");
        }
        // search backward for nearby objects
        int y = u.getLocY();
        for (int j = i-1; j > 0; j--) {
            Unit n = sortedY.get(j);
            if (Math.abs(y - n.getLocY()) > range) {
                break;
            }
            near.add(n);
        }
        // search forward for nearby objects
        for (int j = i+1; j < sortedY.size(); j++) {
            Unit n = sortedY.get(j);
            if (Math.abs(y - n.getLocY()) > range) {
                break;
            }
            near.add(n);
        }
    }

    public static double distance(Unit o1, Unit o2) {
        int dx = o1.getLocX() - o2.getLocX();
        int dy = o1.getLocY() - o2.getLocY();
        return Math.sqrt(dx*dx + dy*dy);
    }

    public static double distance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return Math.sqrt(dx*dx + dy*dy);
    }
}
