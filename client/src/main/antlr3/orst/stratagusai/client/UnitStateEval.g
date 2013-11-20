tree grammar UnitStateEval;
options {
    tokenVocab=UnitState;   // reuse tokens generated by UnitState.g.
                             // without this token ID numbers don't match
                             // between parser and AST walker.
    ASTLabelType=CommonTree;
}
@header {
package orst.stratagusai.client;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.UnitEvent;
import orst.stratagusai.UnitEventType;
import orst.stratagusai.GameProxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
}

@members {

    protected GameProxy proxy;

    /** units that aren't updated are assumed dead. */
    protected Set<Integer> to_update;

    protected List<UnitEvent> events;

    public void setGameProxy(GameProxy proxy) {
        this.proxy = proxy;
    }
}

unit_state
@init {
    Map<Integer,Unit> units = proxy.getUnits();
    to_update = new LinkedHashSet<Integer>(units.keySet());
    events = new ArrayList<UnitEvent>();
}
    : ^(UNIT_STATE units player+ events?) {

       // these weren't updated, so mark them dead.
       for (Integer i : to_update) {
            Unit unit = proxy.getUnit(i);
            unit.setHitPoints(0);
       }

       // add the events
       proxy.setEvents(events);
};

units
    : ^(UNITS unit*);

/*( 0 . #s(unit player-id 0 type 23 loc (2 15) hp 60 r-amt 0 kills 0 status 1 status-args ())) */
unit
    : ^(UNIT idS=INT '.' '(' 'unit' 'player-id' ownerIdS=INT 'type' typeS=INT 'loc' '(' xS=INT yS=INT ')' 'hp' hpS=INT 'r-amt' rAmountS=INT 'kills' killsS=INT 'armor' armorS=INT 'dmg' dmgS=INT 'piercing-dmg' piercingDmgS=INT 'status' statusS=INT 'status-args' '(' arg1S=INT? arg2S=INT? ')' ')' ) {

        int id = Integer.parseInt($idS.text);
        int ownerId = Integer.parseInt($ownerIdS.text);
        int type = Integer.parseInt($typeS.text);
        int x = Integer.parseInt($xS.text);
        int y = Integer.parseInt($yS.text);
        int hp = Integer.parseInt($hpS.text);
        int rAmount = Integer.parseInt($rAmountS.text);
        int kills = Integer.parseInt($killsS.text);
        int armor = Integer.parseInt($armorS.text);
        int dmg = Integer.parseInt($dmgS.text);
        int piercingDmg = Integer.parseInt($piercingDmgS.text);
        int status = Integer.parseInt($statusS.text);

        proxy.updateUnit(ownerId,
                         id, type, x, y,
                         hp, rAmount, kills,
                         armor, dmg, piercingDmg,
                         status,
                         $arg1S.text,
                         $arg2S.text);

       to_update.remove(id);
    };

/* (:player :id 0 :gold 2000 :wood 1000 :oil 1000 :supply 0 :demand 5) */
player
@init {
    Map<String,String> props = new LinkedHashMap<String,String>();
}
    : ^(PLAYER idStr=INT labeled_val[props]+ ) {

        int id = Integer.parseInt($idStr.text);
        Player player = proxy.getPlayer(id);
        if (player == null) {
            player = new Player(id);
            proxy.addPlayer(player);
        }
        // ":gold", ":wood", ":demand", ":supply", ":oil"
        player.setGold(Integer.parseInt(props.get(":gold")));
        player.setWood(Integer.parseInt(props.get(":wood")));
        player.setOil(Integer.parseInt(props.get(":oil")));
        player.setDemand(Integer.parseInt(props.get(":demand")));
        player.setSupply(Integer.parseInt(props.get(":supply")));
    };

labeled_val[Map<String,String> props]
    : ^(LABELED_VAL l=LABEL val=INT) {
        props.put($l.text, $val.text);
    };

events
    : ^(EVENTS event*);

event
    : ^(BUILT id=INT arg=INT) { events.add(new UnitEvent(UnitEventType.BUILT, Integer.parseInt($id.text), Integer.parseInt($arg.text))); }
    | ^(TRAINED id=INT arg=INT) { events.add(new UnitEvent(UnitEventType.TRAINED, Integer.parseInt($id.text), Integer.parseInt($arg.text))); }
    | ^(DIED id=INT) { events.add(new UnitEvent(UnitEventType.DIED, Integer.parseInt($id.text))); };