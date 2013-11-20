package orst.stratagusai.stratsim.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Build requirements of a unit type.
 * 
 * @author Brian
 */
public class BuildRequirements {

    /** the kind of thing to produce */
    protected String type;
    
    /** time required after preconditions met */
    protected int time;

    /** the unit type that produces type */
    protected String producer;

    protected Set<BuildRequirements> preconditions;

    public BuildRequirements() {}

    public BuildRequirements(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Set<BuildRequirements> getPreconditions() {
        if (preconditions == null) {
            preconditions = new LinkedHashSet<BuildRequirements>();
        }
        return preconditions;
    }

    public void setPreconditions(Set<BuildRequirements> preconditions) {
        this.preconditions = preconditions;
    }

    public void addPrecondition(BuildRequirements pre) {
        if (preconditions == null) {
            preconditions = new LinkedHashSet<BuildRequirements>();
        }
        this.preconditions.add(pre);
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }
}
