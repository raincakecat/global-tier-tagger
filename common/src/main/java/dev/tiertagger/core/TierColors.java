package dev.tiertagger.core;
/** MCTiers' published tier palette, separate from the menu accent. */
public final class TierColors {
 private TierColors(){}
 public static int color(String tier){String t=tier.startsWith("R")?tier.substring(1):tier;if(t.matches("MT[1-5]")){int high=color("HT"+t.charAt(2)),low=color("LT"+t.charAt(2));return ((high&0xfefefe)+(low&0xfefefe))>>1;}return switch(t){case "HT1"->0xe8ba3a;case "LT1"->0xd5b355;case "HT2"->0xc4d3e7;case "LT2"->0xa0a7b2;case "HT3"->0xf89f5a;case "LT3"->0xc67b42;case "HT4"->0x81749a;case "LT4"->0x655b79;case "HT5"->0x8f82a8;case "LT5"->0x655b79;case "HT6"->0x615570;case "MT6"->0x574c63;case "LT6","ST6"->0x4d4358;default->0xb6bbc5;};}
}
