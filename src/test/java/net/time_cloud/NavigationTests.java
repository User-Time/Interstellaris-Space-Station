package net.time_cloud;
import java.util.*;
import net.time_cloud.navigation.*;
public final class NavigationTests {
    static int checks;
    static void ok(boolean result,String name){if(!result)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    static void reject(Runnable action,String name){try{action.run();throw new AssertionError(name);}catch(RuntimeException expected){ok(true,name);}}
    public static void main(String[] args){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();String one="core_"+"a".repeat(32),two="core_"+"b".repeat(32);
        VisitLedger ledger=new VisitLedger();ok(!ledger.contains(a,one),"new player locked");ok(ledger.add(a,one),"first visit added");ok(!ledger.add(a,one),"duplicate visit idempotent");ok(!ledger.contains(b,one),"discoveries isolated per player");
        ok(ledger.add(b,two),"second player independent");var decoded=VisitLedger.parse(ledger.json());ok(decoded.forPlayer(a).equals(Set.of(one))&&decoded.forPlayer(b).equals(Set.of(two)),"JSON durable roundtrip");
        reject(()->ledger.add(a,"../../invalid"),"invalid station identity rejected");reject(()->ledger.forPlayer(a).add(two),"client cannot mutate ledger snapshot");
        var bad=ledger.json();bad.addProperty("schema",2);reject(()->VisitLedger.parse(bad),"unknown discovery schema rejected");
        var duplicate=ledger.json();duplicate.getAsJsonArray("players").add(duplicate.getAsJsonArray("players").get(0).deepCopy());reject(()->VisitLedger.parse(duplicate),"duplicate player records rejected");
        var big=new VisitLedger();for(int i=0;i<512;i++)big.add(a,String.format("core_%032x",i));reject(()->big.add(a,"core_"+"f".repeat(32)),"per-player station count capped");ok(VisitLedger.parse(big.json()).forPlayer(a).size()==512,"maximum-size record roundtrip");
        VisitProgress progress=new VisitProgress();ok(!progress.observe(a,one,100),"first sample not a visit");ok(!progress.observe(a,one,139),"less than 40 ticks locked");ok(progress.observe(a,one,140),"40 ticks unlock");ok(!progress.observe(b,one,140),"dwell isolated per player");
        ok(!progress.observe(a,two,150),"switch target resets dwell");ok(!progress.observe(a,null,200),"leaving clears dwell");ok(!progress.observe(a,two,220),"return starts fresh dwell");ok(!progress.observe(a,two,210),"clock rollback resets dwell");ok(progress.observe(a,two,250),"dwell resumes after clock reset");progress.remove(a);ok(!progress.observe(a,two,300),"logout clears transient dwell");progress.clear();ok(!progress.observe(b,one,400),"server reset clears transient dwell");
        System.out.println("NAVIGATION_MODEL_TESTS_PASSED checks="+checks);
    }
}
