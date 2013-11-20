/*
 *
 */
package orst.stratagusai.stratsim.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.util.LinkedHashMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;

import org.apache.log4j.Logger;
import orst.stratagusai.TileType;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.GroupType;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.MapNode;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.PassageNode;
import orst.stratagusai.stratplan.model.Rectangle;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.util.Randomizer;

/**
 * Graphic presentation of the Map model.  The MapView
 * shows objects as they move on the Map.
 *
 *
 * @author Brian King
 */
@SuppressWarnings("serial")
public class MapView extends JPanel {
    private static final Logger log = Logger.getLogger(MapView.class);

    static Color[] tileColor = {
        new Color(0x55,0,0x55), // COAST
        new Color(0,0x66,0), // FOREST
        new Color(0xAA,0xAA,0xAA), // WALL
        Color.white, // OTHER (traversable)
        new Color(139,139,177),  // ROCK
        new Color(0,0,0xAA) // WATER
    };

    static Color [] ownerColor = {
        new Color(255,0,0,0x55),
        new Color(0,0,255,0x55),
        Color.GREEN,
        Color.cyan,
        Color.MAGENTA
    };


    /** width in map units. */
    protected int mw;

    /** height in map units. */
    protected int mh;

    /** the game */
    protected GameState game;

    /** the game map */
    protected GameMap map;

    protected Map<Unit,Integer> displacement = new LinkedHashMap<Unit,Integer>();

    /**
     * constructor 
     */
    public MapView() {}

    public void setGame(GameState game) {
        this.game = game;
        this.map = game.getMap();

        mw = map.getExtentX();
        mh = map.getExtentY();
    }


    @Override
    protected void paintComponent(Graphics g) {
        if (map == null) {
            super.paintComponent(g);
            return;
        }
        paintTerrain(g);
        paintGameObjs(g);
    }

    protected void paintTerrain(Graphics g) {
 
        char[][] cells = map.getCells();
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                char t = cells[i][j];
                Color color = Color.white;
                if (t == TileType.COAST) {
                    color = tileColor[0];
                } else if (t == TileType.FOREST) {
                    color = tileColor[1];
                } else if (t == TileType.HUMAN_WALL) {
                    color = tileColor[2];
                } else if (t == TileType.ORC_WALL) {
                    color = tileColor[2];
                } else if (t == TileType.OTHER) {
                    color = tileColor[3];
                } else if (t == TileType.ROCK) {
                    color = tileColor[4];
                } else if (t == TileType.WALL) {
                    color = tileColor[2];
                } else if (t == TileType.WATER) {
                    color = tileColor[5];
                }
                g.setColor(color);
                g.fillRect(gCoordX(j), gCoordY(i),
                           gCoordX(j+1), gCoordY(i+1));
            }
        }
        // draw region boundaries.
        g.setColor(new Color(0,0,0,50));
        for (Region region : map.getRegions()) {
            List<Rectangle> recs = region.getRectangles();
            for (int i = 0; i < recs.size(); i++) {
                Rectangle r = recs.get(i);
                /*String style;
                if (region.isChokepoint()) {
                    style = "fill:red;opacity:0.4";
                } else {
                    style = "fill:none;stroke:white";
                } */
                int x = r.getMinX();
                int y = r.getMinY();
                int width = r.getExtentX();
                int height = r.getExtentY();
                g.drawRect(gCoordX(x), gCoordY(y), scaleX(width), scaleY(height));
                /*
                if (i == 0) {
                    // region label
                    out.write(String.format("  <text xml:space='preserve' text-anchor='middle' font-size='16' x='%d' y='%d' fill='#FFFFFF'>", S*x+20, S*y+20));
                    out.write("R" + region.getId());
                    out.write("</text>\n");
                } */

            }
        }

        // draw passages (connectivity graph)
        Set<Passage> ps = map.getPassages();
        g.setColor(Color.MAGENTA);
        if (!ps.isEmpty()) {
            for (Passage p : map.getPassages()) {
                Region r = p.getRegionNode().getRegion();
                PassageNode pn = p.getPassageNode();
                g.drawLine(gCoordX(r.getX()), gCoordY(r.getY()),
                           gCoordX(pn.getX()), gCoordY(pn.getY()));
            }
            // draw map nodes (connectivity graph nodes)
            for (MapNode n : map.getMapNodes()) {
                int x = n.getX();
                int y = n.getY();
                g.drawRect(gCoordX(x)-1, gCoordY(y)-1, 3, 3);
            }
        }

    }

    protected void paintGameObjs(Graphics g) {

        List<Unit> objs = game.getUnits();
        for(Unit obj : objs) {
            if (obj.isDead()) {
                continue;
            }
            int minx = obj.getLocX();
            int miny = obj.getLocY();
            if (minx < 0 && miny < 0) {
                continue;
            }
            int w = 1; // obj.getBounds().getExtentX();
            int h = 1; // obj.getBounds().getExtentY();

            // translate to graphics coordinates
            int gminx = gCoordX(minx);
            int gminy = gCoordY(miny);
            int gw = scaleX(w);
            int gh = scaleY(h);

            // object based on owner
            int owner = obj.getOwnerId();
            Color color = ownerColor[owner];
            Color combatInteriorColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 125);
            Color bldgInteriorColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 50);

            String type = obj.getUnitTypeString();
            if (GroupType.GROUP_BUILDING.equals(type)) {
                g.setColor(bldgInteriorColor);
                g.fillRect(gminx, gminy, scaleX(w+1), scaleY(h+1)); // minX, minY, width, height
                g.setColor(color);
                g.drawRect(gminx, gminy, scaleX(w+1), scaleY(h+1)); // minX, minY, width, height
            } else {
                int d;
                if (displacement.containsKey(obj)) {
                    d = displacement.get(obj);
                } else {
                    d = Randomizer.nextInt(5);
                    displacement.put(obj, d);
                }
                g.setColor(combatInteriorColor);
                g.fillOval(gminx+d, gminy+d, gw, gh);
                g.setColor(color);
                g.drawOval(gminx+d, gminy+d, gw, gh); // minX, minY, width, height
            }
            
        }

    }


    /**
     * convert map coordinates to graphics coordinates
     * <p>
     * corresponds to
     * <pre>
     * sint4 scale(sint4 x, sint4 mag) const { return (x*mag)/gfx_mag_frac; }
     * </pre>
     * in GUI.H
     *
     * @param mx x in map coordinates
     * @return x in graphcs coordinates
     */
    protected int gCoordX(int mx) {
        int gw = getWidth();  // graphics width
        return (int) Math.floor(gw * (mx / ((float) mw)));
    }

    /** convert map coordinates to graphics coordinates */
    protected int gCoordY(int my) {
        int gh = getHeight();
        return (int) Math.floor(gh * (my / ((float) mh)));
    }

    /** scale game distance to graphics distance */
    protected int scaleX(int d) {
        int gw = getWidth();  // graphics width
        return (int) Math.ceil(gw * (d / ((float) mw)));
    }

    /** scale game distance to graphics distance */
    protected int scaleY(int d) {
        int gh = getHeight();
        return (int) Math.ceil(gh * (d / ((float) mh)));
    }
}
