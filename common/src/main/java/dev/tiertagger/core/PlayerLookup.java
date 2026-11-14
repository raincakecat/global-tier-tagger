package dev.tiertagger.core;

/** A single player's lookup state, independent of requests for other players. */
public record PlayerLookup(Phase phase, String reason, long retryAtMillis) {
 public enum Phase { NOT_REQUESTED, QUEUED, FETCHING, READY, RETRY_WAIT, FAILED, PARTIAL, SKIPPED }
 public static PlayerLookup of(Phase phase){return new PlayerLookup(phase,"",0);}
 public boolean failed(){return phase==Phase.FAILED||phase==Phase.PARTIAL;}
 public static boolean supported(java.util.UUID id){return id!=null&&id.version()==4&&id.variant()==2;}
}
