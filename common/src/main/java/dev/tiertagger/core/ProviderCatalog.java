package dev.tiertagger.core;
import java.util.*;
public final class ProviderCatalog {
 public record Provider(String id,String name,String unavailable,List<String> discords) {public boolean supported(){return unavailable==null;}}
 public static final List<Provider> ALL=List.of(
  new Provider("mctiers","McTiers",null,List.of("https://discord.gg/mctiers","https://discord.gg/cpvp","https://discord.gg/potionpvp","https://discord.gg/swordtiers","https://discord.gg/axepvp","https://discord.gg/uhcpvp","https://discord.gg/smptiers","https://discord.gg/nethop")),
  new Provider("pvptiers","PvP Tiers",null,List.of("https://discord.gg/tiers","https://discord.gg/diapot","https://discord.gg/uhctiers","https://discord.gg/neth","https://discord.gg/axe","https://discord.gg/smppvp","https://discord.gg/crystaltl","https://discord.gg/macepvp")),
  new Provider("subtiers","SubTiers",null,List.of("https://discord.gg/ds27ah8Y83","https://discord.gg/R24fAbQqqR","https://discord.gg/F2UPwC95r","https://discord.gg/XnaTaeABPr","https://discord.gg/PZPj3dmHWT","https://discord.gg/qWdXfsyYsk","https://discord.gg/Ffw6qSX44y","https://discord.gg/xWHaAbA7Zs","https://discord.gg/kb6vzbfBGf","https://discord.gg/Zm7eXXmqdp","https://discord.gg/yGkssAapmC","https://discord.gg/dn9YUKYbKH")),
  new Provider("lucnoxity","Lucnoxity",null,List.of("https://discord.gg/luctierlist","https://discord.gg/lucsubtierlist")),
  new Provider("tiertests","tiertests.com",null,List.of("https://discord.gg/WUBmbSf6MK")),
  new Provider("mcpvp","MCPVP.com",null,List.of("https://discord.gg/mcpvpcom")),
  new Provider("pvphq","PvPHQ",null,List.of("https://discord.gg/pvphq")),
  new Provider("nova","Nova Tiers",null,List.of("https://discord.gg/kFbD9jPpjR")),
  new Provider("prestige","Prestige Tiers",null,List.of("https://discord.gg/wHJQPCEjRz","https://discord.gg/9f7A9K8zFM")),
  new Provider("lunar","Lunar Tiers",null,List.of("https://discord.gg/zZ6h4a8fmW","https://discord.gg/mKPrbkh9xg")),
  new Provider("nexus","Nexus Tiers",null,List.of("https://discord.gg/6jpDGQCXSK","https://discord.gg/7aAXT8jn9K","https://discord.gg/KXE5f2SkP7","https://discord.gg/s6p83gCAm8")),
  new Provider("jptiers","JP Tiers",null,List.of("https://discord.gg/J3WS26NAfY")),
  new Provider("tiermc","TierMC",null,List.of("https://discord.gg/VswgH4CT5c","https://discord.gg/rYvJZUbX5R","https://discord.gg/87VuPEDxu7","https://discord.gg/kpBB29ZAnG","https://discord.gg/VhsdMPTT5y","https://discord.gg/XWD5VPwmPe","https://discord.gg/VD9DmsnrM8","https://discord.gg/AjbMab5BVf")),
  new Provider("crack","Crack Tiers",null,List.of("https://discord.gg/qypJ4GZv23")),
  new Provider("global","Global Tierlist",null,List.of("https://discord.gg/qaKa93AeUE")),
  new Provider("prime","PrimeTiers",null,List.of("https://discord.gg/HRKfDUS5YQ")),
  new Provider("collapse","Collapse Tiers",null,List.of()),
  new Provider("solar","Solar Tiers","UUID identity mapping not supplied",List.of()),
  new Provider("astra","AstraTiers","UUID identity mapping not supplied",List.of("https://discord.gg/ZYfQYvNkP7")),
  new Provider("cbtl","Combat Tierlist","UUID identity mapping not supplied",List.of()),
  new Provider("central","Central Tierlist","API hostname currently does not resolve",List.of()),
  new Provider("crystalranked","Crystal Ranked","Working API endpoint not supplied",List.of()));
 public static final List<Provider> SUPPORTED=ALL.stream().filter(Provider::supported).toList();
 public static Provider find(String id){return ALL.stream().filter(p->p.id.equals(id)).findFirst().orElse(null);}
 public static String name(String id){Provider p=find(id);return p==null?"Off":p.name;}
 public static String discordLabel(String url){return switch(url){
case "https://discord.gg/zZ6h4a8fmW" -> "Crystal";
case "https://discord.gg/mKPrbkh9xg" -> "All other game modes";
case "https://discord.gg/luctierlist" -> "Main TierList Community";
case "https://discord.gg/lucsubtierlist" -> "SubTierList Community";
case "https://discord.gg/mcpvpcom" -> "Community";
case "https://discord.gg/pvphq" -> "Community";
case "https://discord.gg/WUBmbSf6MK" -> "Community";
case "https://discord.gg/wHJQPCEjRz" -> "SubTiers";
case "https://discord.gg/9f7A9K8zFM" -> "Main tier list";
case "https://discord.gg/6jpDGQCXSK" -> "Crystal / Vanilla";
case "https://discord.gg/7aAXT8jn9K" -> "Mace";
case "https://discord.gg/KXE5f2SkP7" -> "Spear Mace";
case "https://discord.gg/s6p83gCAm8" -> "SMP";
case "https://discord.gg/ds27ah8Y83" -> "Diamond SMP";
case "https://discord.gg/R24fAbQqqR" -> "Diamond Vanilla";
case "https://discord.gg/F2UPwC95r" -> "Cart / Minecart";
case "https://discord.gg/XnaTaeABPr" -> "Bow";
case "https://discord.gg/PZPj3dmHWT" -> "Debuff";
case "https://discord.gg/qWdXfsyYsk" -> "Bed";
case "https://discord.gg/Ffw6qSX44y" -> "Speed";
case "https://discord.gg/xWHaAbA7Zs" -> "Manhunt";
case "https://discord.gg/kb6vzbfBGf" -> "OG Vanilla";
case "https://discord.gg/Zm7eXXmqdp" -> "Creeper";
case "https://discord.gg/yGkssAapmC" -> "Trident";
case "https://discord.gg/dn9YUKYbKH" -> "Elytra";
case "https://discord.gg/tiers" -> "Sword";
case "https://discord.gg/diapot" -> "Pot";
case "https://discord.gg/uhctiers" -> "UHC";
case "https://discord.gg/neth" -> "NethPot";
case "https://discord.gg/axe" -> "Axe and Shield";
case "https://discord.gg/smppvp" -> "SMP";
case "https://discord.gg/crystaltl" -> "Crystal";
case "https://discord.gg/macepvp" -> "Mace";
case "https://discord.gg/VswgH4CT5c" -> "Crystal";
case "https://discord.gg/rYvJZUbX5R" -> "SMP";
case "https://discord.gg/87VuPEDxu7" -> "Pot";
case "https://discord.gg/kpBB29ZAnG" -> "NethPot";
case "https://discord.gg/VhsdMPTT5y" -> "Axe";
case "https://discord.gg/XWD5VPwmPe" -> "Mace";
case "https://discord.gg/VD9DmsnrM8" -> "UHC";
case "https://discord.gg/AjbMab5BVf" -> "Sword";
case "https://discord.gg/mctiers" -> "Main / Mace";
case "https://discord.gg/cpvp" -> "Crystal";
case "https://discord.gg/potionpvp" -> "Pot";
case "https://discord.gg/swordtiers" -> "Sword";
case "https://discord.gg/axepvp" -> "Axe";
case "https://discord.gg/uhcpvp" -> "UHC";
case "https://discord.gg/smptiers" -> "SMP";
case "https://discord.gg/nethop" -> "Nethpot";
default -> "Community Discord";};}
 private ProviderCatalog(){}
}
