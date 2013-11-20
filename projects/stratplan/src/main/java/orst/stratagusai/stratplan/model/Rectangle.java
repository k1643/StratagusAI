package orst.stratagusai.stratplan.model;

/**
 *
 * @author Brian
 */
public class Rectangle {
   protected int minX;
    protected int maxX;
    protected int minY;
    protected int maxY;

    public Rectangle() {}

    public Rectangle(int minX, int minY, int maxX, int maxY) {
        setBounds(minX, minY, maxX, maxY);
    }

    public void setBounds(int minX, int minY, int maxX, int maxY) {
        assert minX <= maxX;
        assert minY <= maxY;
        assert minX >= 0;
        assert minY >= 0;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    public int getX() {
        return (int) Math.round((minX + maxX)/2.0);
    }

    public int getY() {
        return (int) Math.round((minY + maxY)/2.0);
    }

    public int getExtentX() {
        return maxX - minX + 1;
    }

    public int getExtentY() {
        return maxY - minY + 1;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setMinX(int minX) {
        this.minX = minX;
    }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public boolean contains(int locX, int locY) {
        return locX >= minX && locX <= getMaxX() &&
               locY >= minY && locY <= getMaxY();
    }

    /**
     * displace by dx,dy.
     * 
     * @param dx
     * @param dy
     */
    public void displace(int dx, int dy) {
        this.minX += dx;
        this.maxX += dx;
        this.minY += dy;
        this.maxY += dy;
    }

    /**
     * move top left corner to given tile coordinates.
     *
     * @param x
     * @param y
     */
    public void moveTo(int x, int y) {
        int dx = x - minX;
        int dy = y - minY;
        displace(dx, dy);
    }

    @Override
    public String toString() {
        return "[Rectangle" +
                    " :minX " + getMinX() + " :extentX " + getExtentX() +
                    " :minY " + getMinY() + " :extentY " + getExtentY() + "]";
    }
}
