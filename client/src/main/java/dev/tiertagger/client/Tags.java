package dev.tiertagger.client;
import dev.tiertagger.core.*;import net.minecraft.network.chat.*;import java.util.*;
public final class Tags {
 private Tags(){}
 private static TagSelection.Slot slot(Preferences.Slot value){return value==null||"off".equals(value.provider)?null:new TagSelection.Slot(value.provider,value.modeOverride==null&&value.provider.equals("subtiers")?TierTagger.config.preferredSubtiersMode:value.modeOverride);}
 public static Component decorate(UUID id,Component original,boolean tab){
  if(original==null||TierTagger.config==null||TierTagger.ranks==null)return original;
  Preferences c=TierTagger.config;if(!c.enabled||(tab?!c.tab:!c.nametags))return original;
  MutableComponent result=Component.empty();int count=0;for(Preferences.Slot selected:tab?new Preferences.Slot[]{c.primary}:new Preferences.Slot[]{c.primary,c.secondary,c.tertiary}){if(selected==null||selected.provider.equals("off"))continue;String mode=selected.modeOverride==null?(selected.provider.equals("subtiers")?c.preferredSubtiersMode:c.sharedMode):selected.modeOverride;var tier=TierTagger.ranks.tier(selected.provider,id,mode);if(tier.isEmpty())continue;if(count++>0)result.append(Component.literal(" | ").withColor(0xaaaaaa));Component icon=Icons.icon(mode,selected);if(!icon.getString().isEmpty())result.append(icon).append(Component.literal(" "));result.append(Component.literal(tier.get()).withColor(TierColors.color(tier.get())));}
  return count==0?original:result.append(Component.literal(" | ").withColor(0xaaaaaa)).append(original.copy());
 }
 private static Preferences.Slot find(TagSelection.Tag tag,Preferences c){for(Preferences.Slot s:new Preferences.Slot[]{c.primary,c.secondary,c.tertiary})if(s.provider.equals(tag.provider())&&(s.modeOverride==null?(s.provider.equals("subtiers")?c.preferredSubtiersMode:c.sharedMode):s.modeOverride).equals(tag.mode()))return s;return c.primary;}
}
