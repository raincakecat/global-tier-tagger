package dev.tiertagger.core;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.net.*;import java.util.*;import java.util.concurrent.atomic.*;import com.sun.net.httpserver.HttpServer;
class ProviderRetryTest {
 @Test void retryUsesFailedIdentityAndHonorsCooldownWithoutInventingSuccess() throws Exception {
  UUID target=UUID.randomUUID(),preferred=UUID.randomUUID();AtomicInteger calls=new AtomicInteger();AtomicLong clock=new AtomicLong(System.currentTimeMillis());AtomicReference<String> requested=new AtomicReference<>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{requested.set(exchange.getRequestURI().getPath());int number=calls.incrementAndGet();int code=number==1?500:200;byte[] body=(code==500?"{\"error\":\"fixture\"}":"{\"_id\":\""+target+"\",\"data\":{\"SWORD\":{\"gamesPlayed\":1,\"grantedTier\":\"LT4\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try(var provider=new AdditionalProvider("pvphq",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),clock::get)){
   provider.request(target);long deadline=System.currentTimeMillis()+5000;while(!provider.status().contains("500")&&System.currentTimeMillis()<deadline)Thread.sleep(10);assertTrue(provider.status().contains("Request failed (HTTP 500)"));assertEquals(0,provider.lastSuccessMillis());assertFalse(provider.resolved(target));assertTrue(provider.retryAtMillis()>=clock.get()+60000);
   provider.requestRetry(preferred);Thread.sleep(100);assertEquals(1,calls.get());clock.addAndGet(60001);provider.requestRetry(preferred);deadline=System.currentTimeMillis()+5000;while(!provider.status().equals("Connected")&&System.currentTimeMillis()<deadline)Thread.sleep(10);assertEquals("/"+target,requested.get());assertEquals(Optional.of("LT4"),provider.tier(target,"sword"));assertEquals(clock.get(),provider.lastSuccessMillis());
  }finally{server.stop(0);}
 }
 @Test void legacyProvidersHonorLongHttpDateCooldownAndKeepItAcrossDisconnect() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();String retry=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusHours(3).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
  server.createContext("/",exchange->{calls.incrementAndGet();exchange.getResponseHeaders().add("Retry-After",retry);byte[] body="{}".getBytes();exchange.sendResponseHeaders(429,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
  try(var lunar=new LunarClient(base);var subtiers=new SubTiersClient(base)){
   for(RankingProvider provider:java.util.List.of(lunar,subtiers)){provider.request(UUID.randomUUID());long deadline=System.currentTimeMillis()+5000;while(!provider.status().contains("429")&&System.currentTimeMillis()<deadline)Thread.sleep(10);assertTrue(provider.status().contains("429"));assertTrue(provider.retryAtMillis()-System.currentTimeMillis()>10798000,"three-hour HTTP-date is not shortened");long until=provider.retryAtMillis();provider.clear();assertEquals(until,provider.retryAtMillis(),"server changes preserve the server's pause");provider.requestRetry(UUID.randomUUID());}
   Thread.sleep(200);assertEquals(2,calls.get(),"manual retry cannot bypass either provider's pause");
  }finally{server.stop(0);}
 }

}
