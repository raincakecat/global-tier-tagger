package dev.tiertagger.core;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
/** Local session counters only; no telemetry or player identities. */
public final class TrafficStats {
 public record Snapshot(long requests,long responseBodyBytes){}
 private static final class Counters {final LongAdder requests=new LongAdder(),bytes=new LongAdder();}
 private static final ConcurrentHashMap<String,Counters> ALL=new ConcurrentHashMap<>();
 static void request(String id){ALL.computeIfAbsent(id,k->new Counters()).requests.increment();}
 static void bytes(String id,long n){ALL.computeIfAbsent(id,k->new Counters()).bytes.add(n);}
 public static Snapshot snapshot(String id){var c=ALL.get(id);return c==null?new Snapshot(0,0):new Snapshot(c.requests.sum(),c.bytes.sum());}
 private TrafficStats(){}
}
