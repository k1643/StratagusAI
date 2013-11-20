package orst.stratagusai.stratplan;

import java.util.Map;

/**
 * Summary or aggregation of group properties.
 *
 * @author Brian
 */
public interface GroupRepresentative {

    Map<String,Integer> getUnitTypes();

    /**
     * does the group contain at least the given number of the given type?
     */
    boolean contains(String unitType, int amount);

    /** get central location */
    int getLocX();

    /** get central location */
    int getLocY();

    int getBasicDamage();

    int getPiercingDamage();
}
