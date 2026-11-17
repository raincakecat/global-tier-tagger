package dev.tiertagger.core;
import java.net.URI;
/** Only ranks associated with linked Minecraft UUIDs are returned. */
public final class CollapseClient extends BatchedRankingProvider {
 public CollapseClient(){this(URI.create("https://collapsetiers.cattiers.com/api/v1/ranks/lookup"));}
 CollapseClient(URI endpoint){super("collapse",endpoint);}
}
