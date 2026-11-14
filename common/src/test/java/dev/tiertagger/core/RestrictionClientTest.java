package dev.tiertagger.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RestrictionClientTest {
 @Test void nonJsonRateLimitStillHasItsRealFailureCodeAndInvalidUuidNeverQueries() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();server.createContext("/",e->{calls.incrementAndGet();e.getResponseHeaders().add("Retry-After","120");respond(e,429,"<html>rate limited</html>");});server.start();
  try(var client=new RestrictionClient(base(server))){assertThrows(ExecutionException.class,()->client.lookup(new UUID(0,0),1).get());assertEquals(0,calls.get());ExecutionException error=assertThrows(ExecutionException.class,()->client.lookup(ID,1).get(5,TimeUnit.SECONDS));assertInstanceOf(RestrictionClient.Failure.class,error.getCause());assertEquals("RATE_LIMITED",((RestrictionClient.Failure)error.getCause()).code);assertTrue(client.cooldownSeconds()>=119);}
  finally{server.stop(0);}
 }
 static final UUID ID=UUID.fromString("11111111-2222-4333-8444-555555555555");
 static JsonObject body(UUID id,int page){return JsonParser.parseString("""
 {"data":{"player":{"uuid":"%s","username":"FixturePlayer","nameUnavailable":false,"profileUrl":"https://restricttiers.raincakecat.com/player/%s"},"counts":{"active":1,"past":1},"active":[{"community":"Fixture community","reason":"A deliberately long example restriction reason","categories":["Fixture"],"durationDays":null,"restrictedAt":null,"expiresAt":null,"archivedAt":null,"verification":"needs_review","sourceUrl":"https://example.org/source"}],"past":[],"pagination":{"page":%d,"pageSize":50,"totalPages":2,"nextPage":%s,"nextUrl":null},"limits":{"activeMaximum":100,"pastPerPage":50,"maximumPage":10000,"activeTruncated":true,"pastTruncated":true,"hasMorePast":true,"beyondPageLimit":false},"sample":true,"notice":"Fixture only"},"meta":{"apiVersion":"1","requestId":"fixture-id","respondedAt":"2026-11-12T12:00:00Z"}}
 """.formatted(id.toString().replace("-",""),id,page,page==1?"2":"null")).getAsJsonObject();}
 static void respond(com.sun.net.httpserver.HttpExchange exchange,int code,String body) throws java.io.IOException {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
 static URI base(HttpServer server){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");}
 @Test void preservesRecordFlagsAndUnknownDatesAndRejectsWrongIdentity(){var result=RestrictionClient.parse(body(ID,1),ID,1);assertTrue(result.sample());assertTrue(result.activeTruncated());assertTrue(result.hasMorePast());assertTrue(result.pastTruncated());assertNull(result.active().getFirst().durationDays());assertNull(result.active().getFirst().expiresAt());assertEquals("needs_review",result.active().getFirst().verification());assertEquals(2,result.nextPage());assertThrows(IllegalArgumentException.class,()->RestrictionClient.parse(body(ID,1),UUID.randomUUID(),1));assertThrows(IllegalArgumentException.class,()->RestrictionClient.parse(body(ID,1),ID,2));assertThrows(RuntimeException.class,()->RestrictionClient.parse(new JsonObject(),ID,1));}
 @Test void emptyResultsAreDistinctFromUnavailable(){JsonObject root=body(ID,1);root.getAsJsonObject("data").getAsJsonObject("counts").addProperty("active",0);root.getAsJsonObject("data").add("active",new JsonArray());assertEquals(0,RestrictionClient.parse(root,ID,1).activeCount());assertThrows(IllegalArgumentException.class,()->RestrictionClient.safeUrl("javascript:alert(1)"));}
 @Test void deduplicatesAndCachesAndUsesOnlyPublicUuidGet() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();server.createContext("/",e->{calls.incrementAndGet();assertEquals("GET",e.getRequestMethod());assertNull(e.getRequestHeaders().getFirst("Authorization"));assertTrue(e.getRequestURI().toString().contains(ID.toString().replace("-","")));respond(e,200,body(ID,1).toString());});server.start();
  try(var client=new RestrictionClient(base(server))){var a=client.lookup(ID,1);var b=client.lookup(ID,1);assertEquals(ID,a.get(5,TimeUnit.SECONDS).uuid());assertEquals(ID,b.get(5,TimeUnit.SECONDS).uuid());client.lookup(ID,1).get();assertEquals(1,calls.get());assertThrows(CompletionException.class,()->client.lookup(ID,0).join());}finally{server.stop(0);}
 }
 @Test void queueRespectsLowerAdvertisedLimitAndStatusSharesBudget() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<Long> times=new CopyOnWriteArrayList<>();server.createContext("/",e->{times.add(System.nanoTime());e.getResponseHeaders().set("X-RateLimit-Limit","30");respond(e,200,e.getRequestURI().getPath().equals("/status")?"{\"data\":{\"status\":\"healthy\",\"checkedAt\":\"2026-11-12T12:00:00Z\",\"scope\":\"fixture\"},\"meta\":{\"apiVersion\":\"1\",\"requestId\":\"fixture\"}}":body(ID,1).toString());});server.start();
  try(var client=new RestrictionClient(base(server))){client.lookup(ID,1).get(5,TimeUnit.SECONDS);client.checkStatus().get(5,TimeUnit.SECONDS);assertTrue(TimeUnit.NANOSECONDS.toMillis(times.get(1)-times.get(0))>=1900);}finally{server.stop(0);}
 }
 @Test void rateLimitPausesQueueAndFailureNeverBecomesClean() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();List<Long> times=new CopyOnWriteArrayList<>();server.createContext("/",e->{times.add(System.nanoTime());if(calls.incrementAndGet()==1){e.getResponseHeaders().set("Retry-After","2");respond(e,429,"{\"error\":{\"code\":\"RATE_LIMITED\",\"retryAfter\":2},\"meta\":{\"requestId\":\"limit\"}}");}else respond(e,503,"{\"error\":{},\"meta\":{\"requestId\":\"outage\"}}");});server.start();
  try(var client=new RestrictionClient(base(server))){var failure=assertThrows(ExecutionException.class,()->client.lookup(ID,1).get(5,TimeUnit.SECONDS));assertEquals("RATE_LIMITED",((RestrictionClient.Failure)failure.getCause()).code);assertThrows(ExecutionException.class,()->client.lookup(ID,2).get(5,TimeUnit.SECONDS));assertTrue(TimeUnit.NANOSECONDS.toMillis(times.get(1)-times.get(0))>=1900);}finally{server.stop(0);}
 }
 @Test void cancellationSkipsQueuedWorkWithoutCancellingOtherSubscribers() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();server.createContext("/",e->{calls.incrementAndGet();entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}respond(e,200,body(ID,1).toString());});server.start();
  try(var client=new RestrictionClient(base(server))){var a=client.lookup(ID,1);assertTrue(entered.await(3,TimeUnit.SECONDS));var b=client.lookup(ID,1);a.cancel(false);var cancelled=client.lookup(ID,2);cancelled.cancel(false);release.countDown();assertEquals(ID,b.get(5,TimeUnit.SECONDS).uuid());Thread.sleep(1100);assertEquals(1,calls.get());client.cancelPending();assertTrue(cancelled.isCancelled());}finally{release.countDown();server.stop(0);}
 }
 @Test void parsesHistoryWithoutAppendingRepeatedActiveRecords(){var first=RestrictionClient.parse(body(ID,1),ID,1);var second=RestrictionClient.parse(body(ID,2),ID,2);assertEquals(1,first.active().size());assertEquals(1,second.active().size());assertEquals(2,second.page());assertNull(second.nextPage());}
 @Test void interactiveStatusRunsBeforeQueuedScan() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<String> paths=new CopyOnWriteArrayList<>();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);server.createContext("/",e->{paths.add(e.getRequestURI().getPath());if(paths.size()==1){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}respond(e,200,e.getRequestURI().getPath().equals("/status")?"{\"data\":{\"status\":\"healthy\",\"checkedAt\":\"fixture\",\"scope\":\"fixture\"}}":body(ID,1).toString());});server.start();
  try(var client=new RestrictionClient(base(server))){var first=client.lookup(ID,1);assertTrue(entered.await(3,TimeUnit.SECONDS));var scan=client.lookupScan(UUID.randomUUID());var status=client.checkStatus();release.countDown();first.get(5,TimeUnit.SECONDS);status.get(5,TimeUnit.SECONDS);assertEquals("/status",paths.get(1));scan.cancel(false);}finally{release.countDown();server.stop(0);}
 }

 @Test void omittedOptionalDatesAndDurationAreUnknown(){var json=body(ID,1);var record=json.getAsJsonObject("data").getAsJsonArray("active").get(0).getAsJsonObject();record.remove("durationDays");record.remove("restrictedAt");record.remove("expiresAt");record.remove("archivedAt");var result=RestrictionClient.parse(json,ID,1);var entry=result.active().getFirst();assertNull(entry.durationDays());assertNull(entry.restrictedAt());assertNull(entry.expiresAt());assertNull(entry.archivedAt());}

}
