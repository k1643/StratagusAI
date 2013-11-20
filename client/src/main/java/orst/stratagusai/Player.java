package orst.stratagusai;

/**
 *
 * @author Brian
 */
public class Player {
    protected int id;
    protected long gold;
    protected long oil;
    protected long wood;
    protected long supply;
    protected long demand;

    public Player(Player p) {
        this.id = p.id;
        this.gold = p.gold;
        this.oil = p.oil;
        this.wood = p.wood;
        this.supply = p.supply;
        this.demand = p.demand;
    }

    public Player(int id) {
        this.id = id;
    }

    public long getDemand() {
        return demand;
    }

    public void setDemand(long demand) {
        this.demand = demand;
    }

    public long getGold() {
        return gold;
    }

    public void setGold(long gold) {
        this.gold = gold;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getOil() {
        return oil;
    }

    public void setOil(long oil) {
        this.oil = oil;
    }

    public long getSupply() {
        return supply;
    }

    public void setSupply(long supply) {
        this.supply = supply;
    }

    public long getWood() {
        return wood;
    }

    public void setWood(long wood) {
        this.wood = wood;
    }

    
}
