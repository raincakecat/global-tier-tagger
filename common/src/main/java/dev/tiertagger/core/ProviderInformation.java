package dev.tiertagger.core;
import java.util.List;
/** Explanations of the behavior implemented by our adapters, not provider endorsements. */
public final class ProviderInformation {
 private ProviderInformation(){}
 public static List<String> details(String id){return switch(id){
  case "mctiers"->List.of("Uses HT/LT ranks across the main game modes.","Crystal and Vanilla use the same shared mode.","Retired players use their recorded peak tier when the API supplies it.");
  case "pvptiers"->List.of("Uses HT/LT ranks across the main game modes.","Profiles are matched to the player's premium Minecraft UUID.");
  case "subtiers"->List.of("Specialty modes include Minecart, Bow and Diamond Vanilla.","Select SubTiers, then use Advanced Options to choose its preferred game mode.","SubTiers specialty modes use fixed icons.");
  case "lucnoxity"->List.of("Supports low, middle and high positions: LT, MT and HT.","Middle tiers such as MT3 are preserved rather than converted to HT or LT.");
  case "tiertests"->List.of("Uses its own letter-grade scale, including grades such as A+ and B-.","Grades are displayed as supplied; they are not converted into HT/LT ranks.");
  case "mcpvp"->List.of("Supports middle tiers and tier 6 when returned by the API.","Ranks such as MT3 and HT6 keep their original labels.");
  case "pvphq"->List.of("A game mode needs at least one game played before its tier is displayed.","Default tiers for modes with zero games are hidden.","Failed requests wait at least 60 seconds before another attempt. Successful player results are cached for five minutes.");
  case "nova"->List.of("Uses the public player snapshot, cached locally for one hour.","Only UUID-matched premium player records are displayed.");
  case "prestige"->List.of("Can return rankings from multiple communities.","Conflicting ranks for the same player and game mode are omitted rather than choosing an arbitrary result.");
  case "lunar"->List.of("Player tiers are fetched using the premium Minecraft UUID. Background refreshes are spaced five to fifteen minutes apart.","Crystal and Vanilla use the same shared game mode.");
  case "nexus"->List.of("Current integrations cover Crystal, Mace, Spear Mace and SMP.","Sword is not included in the current integration.");
  case "jptiers"->List.of("Player profiles are matched by Minecraft UUID.","Game modes use the mod's shared names and icon settings.");
  case "tiermc"->List.of("Player profiles are matched by Minecraft UUID.","Returned game modes are mapped to the mod's shared game modes.");
  case "crack"->List.of("Premium accounts only. Cracked-account records are excluded.","The public snapshot is cached locally for fifteen minutes.");
  case "global"->List.of("Player ranks are fetched using their premium Minecraft UUID.","Current ranks are displayed; peak tiers are not substituted.");
  case "prime"->List.of("Premium accounts only, using PrimeTiers' current username records.","Current Minecraft identity is checked before displaying returned tiers.","PrimeTiers does not retain tested-account UUIDs. Renamed or reused usernames may show missing or incorrect historical ranks.","Results are cached for five minutes and group lookups are batched.");
  case "collapse"->List.of("Ranks are fetched in UUID batches. Background refreshes are spaced five to fifteen minutes apart.","Only profiles with a linked Minecraft identity can display ranks.","Imported profiles without a linked identity remain unavailable.");
  default->throw new IllegalArgumentException("No supported provider information: "+id);
 };}
}
