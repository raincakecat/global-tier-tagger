package dev.tiertagger.core;
import java.net.URI;
import java.util.*;

/** MCTiers rankings, with paced requests and identity-specific failures. */
public final class McTiersClient extends QueuedRankingProvider {
 private final URI base;
 public McTiersClient(){this(URI.create("https://mctiers.net/api/v2/profile/"));}
 McTiersClient(URI base){super("mctiers",300_000,false,System::currentTimeMillis);this.base=base;}
 protected String connectedLabel(){return "MCTiers connected";}
 protected Map<String,String> fetch(UUID id)throws Exception{String json=getProfile(base.resolve(id+"/rankings").toString());return json==null?Map.of():McTiersParser.parse(json);}
}
