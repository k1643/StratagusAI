package orst.stratagusai.prodh;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 * @author Brian
 */
public class ProductionReq {

    /** the kind of thing to produce */
    protected String type;
    
    /** time required after preconditions met */
    protected int time;

    /** the unit type that produces type */
    protected String producer;

    protected Set<ProductionReq> preconditions;

    public ProductionReq() {}

    public ProductionReq(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Set<ProductionReq> getPreconditions() {
        if (preconditions == null) {
            preconditions = new LinkedHashSet<ProductionReq>();
        }
        return preconditions;
    }

    public void setPreconditions(Set<ProductionReq> preconditions) {
        this.preconditions = preconditions;
    }

    public void addPrecondition(ProductionReq pre) {
        if (preconditions == null) {
            preconditions = new LinkedHashSet<ProductionReq>();
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
