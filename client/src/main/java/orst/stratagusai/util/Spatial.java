package orst.stratagusai.util;

import orst.stratagusai.Direction;

/**
 * Spatial utility functions.
 * 
 * @author bking
 */
public class Spatial {

    public static double distance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return Math.sqrt(dx*dx + dy*dy);
    }

    public static double distance2(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return dx*dx + dy*dy;
    }

    /**
     * Direction of motion is calculated from the old location x1,y1 and
     * the new location x2,y2.
     */
    public static Direction getDirection(int x0, int y0, int x1, int y1) {
        if (x0 == -1 || y0 == -1) {
            return Direction.STANDING;
        }
        if (x0 < x1) {
            // -> SE, E, NE
            if (y0 < y1) return Direction.SE;
            if (y0 > y1) return Direction.NE;
            return Direction.E;
        } else if (x0 > x1) {
            // <- SW, W, NW
            if (y0 < y1) return Direction.SW;
            if (y0 > y1) return Direction.NW;
            return Direction.W;
        } else {
            // N, S
            if (y0 < y1) return Direction.S;
            if (y0 > y1) return Direction.N;
            return Direction.STANDING;
        }
    }
}
