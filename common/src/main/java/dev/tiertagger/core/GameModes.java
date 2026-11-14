package dev.tiertagger.core;
import java.util.*;
public final class GameModes {
 private GameModes() {}
 public static final List<String> ALL=List.of("crystal","sword","axe","smp","pot","mace","uhc","nethpot","spear-mace","minecart","diamond-vanilla","debuff","elytra","speed","creeper","manhunt","diamond-smp","bow","bed","og-vanilla","trident","spear","modern-smp","diamond-cart","pufferfish","spleef","elytra-spear","diamond-survival","early-game","end-game","late-game","shield","diamond-op");
 public static String label(String mode) { return switch(mode){case "spear-mace"->"Spear Mace";case "diamond-vanilla"->"Diamond Vanilla";case "diamond-smp"->"Diamond SMP";case "og-vanilla"->"OG Vanilla";case "smp"->"SMP";case "uhc"->"UHC";default->java.util.Arrays.stream(mode.split("-")).map(s->s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1)).collect(java.util.stream.Collectors.joining(" "));}; }
 public static String mctiersKey(String mode) { return switch(mode) {case "crystal" -> "vanilla"; case "nethpot" -> "nethop"; default -> mode;}; }
}
