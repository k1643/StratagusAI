package orst.stratagusai;

/**
 * Codes for map tile types.  Codes are set in socket.cpp.
 *
 * @author Brian
 */
public interface TileType {
    final static char FOREST = 'T';
    final static char WATER = '^';
    final static char COAST = '+';
    final static char HUMAN_WALL = 'h';
    final static char ROCK = '#';
    final static char WALL = 'W';
    final static char ORC_WALL = 'c';
    final static char OTHER = '0';
}
