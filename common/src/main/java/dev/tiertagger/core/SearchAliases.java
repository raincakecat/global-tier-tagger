package dev.tiertagger.core;
import java.util.Locale;
public final class SearchAliases {
 private SearchAliases(){}
 private static String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");}
 public static boolean mode(String mode,String query){String q=normalize(query);if(normalize(GameModes.label(mode)).contains(q)||normalize(mode).contains(q))return true;String aliases=switch(mode){case "crystal"->"vanilla crystalpvp cpvp";case "nethpot"->"nethop netheritepotion netheritepot neth";case "pot"->"potion diamondpot diapot";case "uhc"->"enderpearl pearl";case "smp"->"hardcore";case "minecart"->"cart";case "spear-mace"->"spearmace";default->"";};for(String alias:aliases.split(" "))if(!alias.isEmpty()&&normalize(alias).contains(q))return true;return false;}
 public static boolean provider(ProviderCatalog.Provider p,String query){String q=normalize(query);if(normalize(p.name()).contains(q)||normalize(p.id()).contains(q))return true;for(String suffix:new String[]{"com","net","org"})if(q.endsWith(suffix)&&q.length()>suffix.length()){String plain=q.substring(0,q.length()-suffix.length());if(normalize(p.name()).equals(plain)||normalize(p.id()).equals(plain))return true;}return false;}
}
