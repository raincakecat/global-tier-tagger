package dev.tiertagger.core;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class SearchAliasesTest {
 @Test void sharedModeAliasesRespectModeIdentity(){assertTrue(SearchAliases.mode("crystal","vanilla"));assertTrue(SearchAliases.mode("nethpot","neth op"));assertTrue(SearchAliases.mode("nethpot","Netherite Potion"));assertTrue(SearchAliases.mode("pot","dia pot"));assertFalse(SearchAliases.mode("sword","vanilla"));assertTrue(SearchAliases.mode("spear-mace","spear mace"));}
 @Test void providerNamesDomainsAndSpacingFindSameProvider(){assertTrue(SearchAliases.provider(ProviderCatalog.find("mctiers"),"MC tiers"));assertTrue(SearchAliases.provider(ProviderCatalog.find("mctiers"),"mctiers.net"));assertTrue(SearchAliases.provider(ProviderCatalog.find("tiertests"),"tiertests.com"));assertFalse(SearchAliases.provider(ProviderCatalog.find("lunar"),"mctiers.net"));}
}
