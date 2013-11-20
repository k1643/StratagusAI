package orst.stratagusai.stratplan.persist;

import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.MapNode;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.Rectangle;
import orst.stratagusai.stratplan.model.Region;

/**
 * Unit test for simple App.
 */
public class GameStateWriterTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GameStateWriterTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( GameStateWriterTest.class );
    }

    /**
     * 
     */
    public void testWrite() throws Exception {
        GameState game = new GameState();
        
        GameMap map = new GameMap();
        // int minX, int extentX, int minY, int extentY) {
        // a 40 column, 20 row map.
        char[][] cells = new char[20][40];
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                cells[i][j] = 'T';
            }
        }
        map.setCells(cells);
        Region r1 = new Region(1, new Rectangle(0,0,19,19));
        Region r2 = new Region(2, new Rectangle(20,0,40,19));
        map.addRegion(r1);
        map.addRegion(r2);
        //Passage p = new Passage(r1, r2);
        //map.addPassage(p);
        game.setMap(map);
        Unit u = new Unit(1);
        u.setRAmount(40000);
        u.setHitPoints(25500);
        u.setLocX(2);
        u.setLocY(2);
        u.setStatus(1);
        u.setStatusArg1(0);
        u.setStatusArg2(0);
        u.setType(11);
        u.setUnitTypeString("unit-gold-mine");
        game.addUnit(u);
         
        GameStateWriter.write(game, "data/game.txt");
        assertTrue( true );
    }

    public void testRoundTrip() throws Exception {
        GameState game1 = GameStateReader.load("data/game1.txt");
        assertNotNull(game1);
        GameStateWriter.write(game1, "data/game1.out.txt");
        GameState game2 = GameStateReader.load("data/game1.out.txt");

        assertEquals(game1.getCycle(), 50);
        Player p = game1.getPlayer(0);
        assertEquals(p.getGold(), 400);
        assertEquals(p.getOil(), 500);
        assertEquals(p.getWood(), 600);
        assertEquals(game1.getCycle(), game2.getCycle());
        Player p2 = game2.getPlayer(0);
        assertEquals(p.getGold(), p2.getGold());
        assertEquals(p.getOil(), p2.getOil());
        assertEquals(p.getWood(), p2.getWood());
        assertEquals(1, game1.getUnits().size());
        assertEquals(game1.getUnits().size(),
                     game2.getUnits().size());
        assertEquals(game1.getMap().getRegions().size(),
                     game2.getMap().getRegions().size());

        // test that negative number is parsed correctly
        Unit u1 = game1.getUnit(1);
        assertNotNull(u1);
        Unit u2 = game2.getUnit(1);
        assertNotNull(u2);
        assertEquals(-2, u1.getCurrentTarget());
        assertEquals(u1.getCurrentTarget(), u2.getCurrentTarget());
    }

    public void testMapConnectivity() throws Exception {
        GameState game1 = GameStateReader.load("data/game_with_map_passages.txt");
        GameMap map = game1.getMap();
        /*
                    (:Region :id 2 :minX 20 :extentX 19 :minY 0 :extentY 19)
                    (:Region :id 1 :minX 0 :extentX 19 :minY 0 :extentY 19)
                    (:connections
                      (:PassageNode :id 1)
                      (:RegionNode :region 2)
                      (:RegionNode :region 1)
                      (:Passage  :regionNode 2 :passageNode 1)
                      (:Passage  :regionNode 1 :passageNode 1)
         */
       Set<Passage> ps = map.getPassages();
       Set<MapNode> ns = map.getMapNodes();
       assertEquals(2, ps.size());
       assertEquals(3, ns.size());    
    }

    public void testConnectivityRoundTrip() throws Exception {
        GameState game1 = GameStateReader.load("data/game_with_map_passages.txt");
        GameMap map1 = game1.getMap();
        /*
                    (:Region :id 2 :minX 20 :extentX 19 :minY 0 :extentY 19)
                    (:Region :id 1 :minX 0 :extentX 19 :minY 0 :extentY 19)
                    (:connections
                      (:PassageNode :id 1)
                      (:RegionNode :region 2)
                      (:RegionNode :region 1)
                      (:Passage  :regionNode 2 :passageNode 1)
                      (:Passage  :regionNode 1 :passageNode 1)
         */
        GameStateWriter.write(game1, "data/game_with_map_passages.out.txt");
        GameState game2 = GameStateReader.load("data/game_with_map_passages.out.txt");
        GameMap map2 = game2.getMap();
       Set<Passage> ps1 = map1.getPassages();
       Set<MapNode> ns1 = map1.getMapNodes();
       Set<Passage> ps2 = map1.getPassages();
       Set<MapNode> ns2 = map1.getMapNodes();
       assertEquals(ps1.size(), ps2.size());
       assertEquals(ns1.size(), ns2.size());
    }
}
