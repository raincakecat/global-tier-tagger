package dev.tiertagger.client;
import net.minecraft.network.chat.*;import net.minecraft.resources.Identifier;import java.util.*;
public final class Icons {
 public static final List<String> MAIN=List.of("crystal","sword","axe","smp","pot","mace","uhc","nethpot","spear-mace");
 public static final List<String> FIXED=List.of("minecart","og-vanilla","debuff","elytra","manhunt","trident","speed","diamond-vanilla","bed","creeper","diamond-smp","bow");
 public static final List<String> EXTRA=List.of("spear","modern-smp","diamond-cart","pufferfish","spleef","elytra-spear","diamond-survival","early-game","end-game","late-game","shield","diamond-op");
 public static Component icon(String mode,Preferences.Slot slot){int set="set2".equals(dev.tiertagger.core.IconPreferences.selected(mode,TierTagger.config.modeIcons,slot==null?Map.of():slot.iconOverrides))?1:0;return sample(mode,set);}
 public static Component sample(String mode,int set){int index=MAIN.indexOf(mode),code=index>=0?0xe000+set*9+index:FIXED.indexOf(mode)>=0?0xe020+FIXED.indexOf(mode):EXTRA.indexOf(mode)>=0?0xe040+EXTRA.indexOf(mode):-1;if(code<0)return Component.empty();return Component.literal(String.valueOf((char)code)).withStyle(s->s.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("multi_tier_tagger","icons"))).withColor(0xffffff));}
 private Icons(){}
}
