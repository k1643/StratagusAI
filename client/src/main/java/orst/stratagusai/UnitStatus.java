package orst.stratagusai;

/**
 *
 * Enumeration for status codes.  See socket.cpp for correpondence with
 * stratagus Unit state.
 *
 * @author bking
 */
public enum UnitStatus {

    DEFAULT(0),
    STAND(1),
    MOVING(2),
    ATTACKING(3),
    BUILDING_OR_TRAINING(4),
    UNDER_CONSTRUCTION(5),
    REPAIRING(6),
    HARVESTING(7),
    RETURNING_RESOURCE(8),
    DIE(9);
    
    private int code;

    UnitStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static UnitStatus valueOf(int code) {
        switch (code) {
            case 0: return DEFAULT;
            case 1: return STAND;
            case 2: return MOVING;
            case 3: return ATTACKING;
            case 4: return BUILDING_OR_TRAINING;
            case 5: return UNDER_CONSTRUCTION;
            case 6: return REPAIRING;
            case 7: return HARVESTING;
            case 8: return RETURNING_RESOURCE;
            case 9: return DIE;
        }
        throw new RuntimeException("Unknown unit status code " + code);
    }
}
