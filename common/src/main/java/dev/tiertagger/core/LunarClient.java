package dev.tiertagger.core;
import java.net.URI;
/** Lunar's UUID batch protocol uses the shared bounded batch transport. */
public final class LunarClient extends BatchedRankingProvider {
 public LunarClient(){this(URI.create("https://lunar-ranks.cattiers.com/v1/ranks/lookup"));}
 LunarClient(URI endpoint){super("lunar",endpoint);}
}
