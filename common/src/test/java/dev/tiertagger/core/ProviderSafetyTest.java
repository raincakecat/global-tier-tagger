package dev.tiertagger.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ProviderSafetyTest {
 static final UUID A=UUID.fromString("11111111-2222-4333-8444-555555555555"),B=UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),C=UUID.fromString("12345678-abcd-4abc-8abc-123456789abc");
 static URI base(HttpServer server){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");}
 static void await(BooleanSupplier done)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!done.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertTrue(done.getAsBoolean());}
 static void respond(com.sun.net.httpserver.HttpExchange e,int status,String body)throws java.io.IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}finally{e.close();}}
 static String profile(UUID id){return "{\"uuid\":\""+id+"\",\"rankings\":{\"sword\":{\"tier\":3,\"pos\":0}}}";}
 @Test void invalidServerAndOfflineUuidsNeverMakeAnyRankingRequest()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();server.createContext("/",e->{calls.incrementAndGet();respond(e,404,"{}");});server.start();
  try(var pvp=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis);var mc=new McTiersClient(base(server));var sub=new SubTiersClient(base(server));var tt=new TierTestsClient(base(server));var lunar=new LunarClient(base(server))){
   for(var provider:List.<RankingProvider>of(pvp,mc,sub,tt,lunar))for(UUID id:List.of(new UUID(0,0),UUID.nameUUIDFromBytes("OfflinePlayer:Fake".getBytes()),new UUID(1,2))){provider.request(id);provider.requestForeground(id);assertEquals(PlayerLookup.Phase.SKIPPED,provider.lookup(id).phase());assertTrue(provider.tier(id,"sword").isEmpty());}
   Thread.sleep(250);assertEquals(0,calls.get());
  }finally{server.stop(0);}
 }
 @Test void playerRejectionNeverPausesAnotherPlayerOrBecomesAnEmptyResult()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();List<Long> times=new CopyOnWriteArrayList<>();server.createContext("/",e->{calls.incrementAndGet();times.add(System.nanoTime());boolean reject=e.getRequestURI().getPath().endsWith(A.toString());respond(e,reject?422:200,reject?"{}":profile(B));});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){
   p.request(A);p.request(B);await(()->p.resolved(B));assertEquals(2,calls.get());assertEquals(0,p.retryAtMillis());assertEquals(PlayerLookup.Phase.FAILED,p.lookup(A).phase());assertFalse(p.resolved(A));assertTrue(p.lookup(A).reason().contains("422"));assertEquals(PlayerLookup.Phase.READY,p.lookup(B).phase());assertEquals("HT3",p.tier(B,"sword").orElseThrow());assertTrue(times.get(1)-times.get(0)>=TimeUnit.MILLISECONDS.toNanos(950));
   p.request(A);Thread.sleep(100);assertEquals(2,calls.get(),"Rejected identity has its own negative retry budget");
  }finally{server.stop(0);}
 }
 @Test void badIdentityResponseIsPerPlayerAndDoesNotHideOtherProfiles()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->respond(e,200,profile(B)));server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){p.request(A);p.request(B);await(()->p.resolved(B));assertTrue(p.lookup(A).failed());assertFalse(p.resolved(A));assertTrue(p.tier(A,"sword").isEmpty());assertEquals(0,p.retryAtMillis());assertEquals("Connected",p.status());}
  finally{server.stop(0);}
 }
 @Test void missingProfileIsCachedWithoutInventingRank()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();server.createContext("/",e->{calls.incrementAndGet();respond(e,404,"{}");});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){p.request(A);await(()->p.resolved(A));p.request(A);assertEquals(1,calls.get());assertEquals(PlayerLookup.Phase.READY,p.lookup(A).phase());assertTrue(p.tier(A,"sword").isEmpty());}
  finally{server.stop(0);}
 }
 @Test void queuedPlayersSurviveRateLimitAndResumeWithoutManualRequery()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();var clock=new AtomicLong(System.currentTimeMillis());
  server.createContext("/",e->{if(calls.incrementAndGet()==1){e.getResponseHeaders().add("Retry-After","120");respond(e,429,"{}");}else respond(e,200,profile(B));});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),clock::get)){p.request(A);p.request(B);await(()->p.lookup(A).failed());assertTrue(p.retryAtMillis()>=clock.get()+120000);assertEquals(PlayerLookup.Phase.RETRY_WAIT,p.lookup(B).phase());Thread.sleep(150);assertEquals(1,calls.get());long pause=p.retryAtMillis();clock.set(pause+1);await(()->p.resolved(B));assertEquals(2,calls.get());}
  finally{server.stop(0);}
 }
 @Test void foregroundProfileMovesAheadOfBackgroundQueue()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<String> paths=new CopyOnWriteArrayList<>();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  server.createContext("/",e->{String path=e.getRequestURI().getPath();paths.add(path);if(paths.size()==1){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}respond(e,200,profile(UUID.fromString(path.substring(1))));});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){p.request(A);assertTrue(entered.await(3,TimeUnit.SECONDS));p.request(B);p.request(C);p.requestForeground(C);release.countDown();await(()->p.resolved(C));assertEquals(List.of("/"+A,"/"+C),paths.subList(0,2));}
  finally{release.countDown();server.stop(0);}
 }
 @Test void disconnectCancelsOldGenerationWithoutOverwritingNewIdentity()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var executor=Executors.newCachedThreadPool();server.setExecutor(executor);var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  server.createContext("/",e->{int n=calls.incrementAndGet();if(n==1){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}try{respond(e,200,n==1?profile(A):profile(A).replace("\"tier\":3","\"tier\":1"));}catch(java.io.IOException ignored){}});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){p.request(A);assertTrue(entered.await(3,TimeUnit.SECONDS));p.clear();p.request(A);await(()->p.resolved(A));release.countDown();Thread.sleep(100);assertEquals("HT1",p.tier(A,"sword").orElseThrow());assertEquals(2,calls.get());}
  finally{release.countDown();server.stop(0);executor.shutdownNow();}
 }
 @Test void nexusKeepsSuccessfulModesWhenAnotherEndpointFails()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{boolean mace=e.getRequestURI().getPath().startsWith("/mace/");respond(e,mace?503:200,mace?"{}":"{\"uuid\":\""+A+"\",\"tier_id\":\"HT3\"}");});server.start();
  try(var p=new AdditionalProvider("nexus",base(server),System::currentTimeMillis)){p.request(A);await(()->p.lookup(A).phase()==PlayerLookup.Phase.PARTIAL);assertEquals("HT3",p.tier(A,"crystal").orElseThrow());assertFalse(p.resolved(A));assertTrue(p.tier(A,"mace").isEmpty());assertTrue(p.retryAtMillis()>System.currentTimeMillis());}
  finally{server.stop(0);}
 }
 @Test void bodyLimitCancelsOversizedResponses()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->respond(e,200,"x".repeat(200)));server.start();
  try(var http=HttpClient.newHttpClient()){assertThrows(java.io.IOException.class,()->http.send(HttpRequest.newBuilder(base(server)).GET().build(),HttpBodies.limited(64)));}
  finally{server.stop(0);}
 }
 @Test void foregroundRequestCanEnterAFullBackgroundQueue()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<String> paths=new CopyOnWriteArrayList<>();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  server.createContext("/",e->{String path=e.getRequestURI().getPath();paths.add(path);if(paths.size()==1){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}respond(e,200,profile(UUID.fromString(path.substring(1))));});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){p.request(A);assertTrue(entered.await(3,TimeUnit.SECONDS));for(int i=0;i<128;i++)p.request(UUID.randomUUID());p.requestForeground(C);release.countDown();await(()->p.resolved(C));assertEquals("/"+C,paths.get(1));}
  finally{release.countDown();server.stop(0);}
 }
 @Test void lowerAdvertisedLimitIncreasesSpacing()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<Long> times=new CopyOnWriteArrayList<>();server.createContext("/",e->{times.add(System.nanoTime());e.getResponseHeaders().add("X-RateLimit-Limit","30");respond(e,200,profile(UUID.fromString(e.getRequestURI().getPath().substring(1))));});server.start();
  try(var p=new AdditionalProvider("pvptiers",base(server),System::currentTimeMillis)){p.request(A);p.request(B);await(()->p.resolved(B));assertTrue(times.get(1)-times.get(0)>=TimeUnit.MILLISECONDS.toNanos(1950));}
  finally{server.stop(0);}
 }
 @Test void profileProgressSeparatesQueueCooldownAndActualFailure(){
  var ready=PlayerLookup.of(PlayerLookup.Phase.READY);var queued=PlayerLookup.of(PlayerLookup.Phase.QUEUED);var waiting=new PlayerLookup(PlayerLookup.Phase.RETRY_WAIT,"Rate limited",1000);var failure=new PlayerLookup(PlayerLookup.Phase.FAILED,"HTTP 422",1000);
  assertEquals("All tier lists checked",LookupProgress.describe(List.of(ready),true));assertEquals("No ranked modes returned",LookupProgress.describe(List.of(ready),false));assertTrue(LookupProgress.describe(List.of(ready,queued),true).contains("Queued 1"));assertTrue(LookupProgress.describe(List.of(ready,waiting),true).contains("Waiting to retry 1"));assertTrue(LookupProgress.describe(List.of(ready,failure),true).startsWith("Partial results"));assertTrue(LookupProgress.describe(List.of(failure),false).startsWith("Lookup incomplete"));
 }
 @Test void tierTestsValidatesRankOwnerAndMcTiersDoesNotCacheAnErrorAsUnranked(){
  String body="{\"success\":true,\"data\":[{\"user\":{\"minecraftUUID\":\""+A+"\"},\"gamemode\":{\"name\":\"CRYSTAL\"},\"tier\":\"C_PLUS\"}]}";
  assertEquals("C+",TierTestsClient.parse(body,A).get("crystal"));assertThrows(IllegalArgumentException.class,()->TierTestsClient.parse(body,B));assertThrows(IllegalArgumentException.class,()->TierTestsClient.parse(body.replace("minecraftUUID","missingIdentity"),A));assertThrows(IllegalArgumentException.class,()->McTiersParser.parse("{\"error\":\"Unavailable\"}"));assertTrue(McTiersParser.parse("{}").isEmpty());
 }
}
