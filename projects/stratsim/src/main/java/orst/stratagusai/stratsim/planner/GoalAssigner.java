package orst.stratagusai.stratsim.planner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.GlpkException;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_prob;
import org.gnu.glpk.glp_smcp;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.Passage;
import orst.stratagusai.stratplan.model.Region;

/**
 * Assign UnitGroups to StrategicGoals using a linear programming model.
 */
public class GoalAssigner {
    private static final Logger log = Logger.getLogger(GoalAssigner.class);


}
