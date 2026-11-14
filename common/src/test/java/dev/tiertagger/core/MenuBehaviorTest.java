package dev.tiertagger.core;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MenuBehaviorTest {
 @Test void listScrollClampsAndScrollbarReachesLastRows(){var s=new ScrollWindow();s.layout(1000,7);s.wheel(-1);assertEquals(2,s.first());s.drag(10000,140);assertEquals(993,s.first());assertEquals(126,s.thumbOffset(140));s.drag(-100,140);assertEquals(0,s.first());s.first(500);s.layout(3,7);assertEquals(0,s.first());assertEquals(0,s.maximum());}
 @Test void acceptsHttpDateAndSecondsRetryAfter(){assertEquals(120,RetryAfter.seconds("120",60));assertEquals(60,RetryAfter.seconds("invalid",60));String date=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(120).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);assertTrue(RetryAfter.seconds(date,60)>=119);}
 @Test void httpFailureReasonsAreSpecific(){assertEquals("Error: Request failed (HTTP 503)",ProviderStatus.http(503).label());assertEquals("Error: Rate limited (HTTP 429)",ProviderStatus.http(429).label());assertEquals("Error: Request timed out",ProviderStatus.failure(new java.net.http.HttpTimeoutException("fixture")).label());}
 @Test void connectionLabelsNeverInventSuccess(){assertEquals("Connected",ProviderStatus.fromLegacy("Lunar Tiers connected").label());assertEquals("Not checked",ProviderStatus.fromLegacy("Waiting for players").label());assertEquals(ProviderStatus.State.ERROR,ProviderStatus.fromLegacy("Unavailable").state());assertEquals(ProviderStatus.State.CHECKING,ProviderStatus.fromLegacy("Checking").state());}
}
