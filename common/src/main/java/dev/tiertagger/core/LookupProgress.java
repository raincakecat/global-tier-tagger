package dev.tiertagger.core;
import java.util.Collection;

/** Progress is derived from this profile, never a provider's unrelated traffic. */
public final class LookupProgress {
 public static String describe(Collection<PlayerLookup> states,boolean hasRanks){
  int checking=0,queued=0,waiting=0,failed=0,skipped=0;
  for(var state:states)switch(state.phase()){
   case FETCHING->checking++;
   case QUEUED,NOT_REQUESTED->queued++;
   case RETRY_WAIT->waiting++;
   case FAILED,PARTIAL->failed++;
   case SKIPPED->skipped++;
   default->{}
  }
  String errors=failed>0?" | "+failed+" failed (Status)":"";
  if(checking+queued+waiting>0){String text=checking>0?"Checking "+checking+" tier "+(checking==1?"list":"lists"):"";if(queued>0)text+=(text.isEmpty()?"Queued ":" | Queued ")+queued;if(waiting>0)text+=(text.isEmpty()?"Waiting to retry ":" | Waiting to retry ")+waiting;return text+errors;}
  if(failed>0)return (hasRanks?"Partial results":"Lookup incomplete")+errors;
  if(skipped>0)return "Unsupported Minecraft identity";
  return hasRanks?"All tier lists checked":"No ranked modes returned";
 }
 private LookupProgress(){}
}
