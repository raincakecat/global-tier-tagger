package dev.tiertagger.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class TrafficReductionTest {
 @Test void repeatedTabScansAndReconnectsReuseOnePrimeBatch()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();Map<String,UUID> identities=new HashMap<>();
  server.createContext("/",e->{calls.incrementAndGet();String query=URLDecoder.decode(e.getRequestURI().getRawQuery(),java.nio.charset.StandardCharsets.UTF_8);String[] names=query.substring("names=".length()).split(",");assertEquals(50,names.length);JsonArray items=new JsonArray();for(String name:names){JsonObject p=new JsonObject();p.addProperty("username",name);p.addProperty("platform","premium");p.add("tiers",new JsonArray());items.add(p);}ProviderSafetyTest.respond(e,200,"{\"count\":50,\"items\":"+items+",\"missing\":[]}");});server.start();
  try(var p=new PrimeClient(ProviderSafetyTest.base(server),n->java.util.concurrent.CompletableFuture.completedFuture(new MinecraftIdentityResolver.Identity(identities.get(n),n)))){
   for(int i=0;i<50;i++){String name="Player"+i;UUID id=UUID.randomUUID();identities.put(name,id);p.identify(id,name);p.requestBackground(id,false);}
   ProviderSafetyTest.await(()->identities.values().stream().allMatch(p::resolved));
   for(int scan=0;scan<100;scan++)for(UUID id:identities.values())p.requestBackground(id,scan%2==0);
   p.suspend();for(UUID id:identities.values())p.requestBackground(id,false);Thread.sleep(600);assertEquals(1,calls.get());assertTrue(identities.values().stream().allMatch(p::resolved));
  }finally{server.stop(0);}
 }
 @Test void cachedEmptyAndRankedResultsSurviveServerChanges()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
  server.createContext("/",e->{calls.incrementAndGet();ProviderSafetyTest.respond(e,200,ProviderSafetyTest.profile(ProviderSafetyTest.A));});server.start();
  try(var p=new AdditionalProvider("pvptiers",ProviderSafetyTest.base(server),System::currentTimeMillis)){
   p.request(ProviderSafetyTest.A);ProviderSafetyTest.await(()->p.resolved(ProviderSafetyTest.A));p.suspend();p.request(ProviderSafetyTest.A);Thread.sleep(150);assertEquals(1,calls.get());assertEquals("HT3",p.tier(ProviderSafetyTest.A,"sword").orElseThrow());
  }finally{server.stop(0);}
  var batch=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var batchCalls=new AtomicInteger();
  batch.createContext("/",e->{batchCalls.incrementAndGet();var players=new JsonObject();for(var id:JsonParser.parseString(new String(e.getRequestBody().readAllBytes())).getAsJsonObject().getAsJsonArray("uuids"))players.add(id.getAsString(),new JsonObject());ProviderSafetyTest.respond(e,200,"{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":"+players+"}");});batch.start();
  try(var p=new CollapseClient(ProviderSafetyTest.base(batch))){p.request(ProviderSafetyTest.A);ProviderSafetyTest.await(()->p.resolved(ProviderSafetyTest.A));p.suspend();p.requestBackground(ProviderSafetyTest.A,false);Thread.sleep(600);assertTrue(p.resolved(ProviderSafetyTest.A));assertTrue(p.tier(ProviderSafetyTest.A,"crystal").isEmpty());assertEquals(1,batchCalls.get());}
  finally{batch.stop(0);}
 }
 @Test void distantTabRefreshWaitsLongerButKeepsRankVisible()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();var clock=new AtomicLong(System.currentTimeMillis());
  server.createContext("/",e->{calls.incrementAndGet();ProviderSafetyTest.respond(e,200,ProviderSafetyTest.profile(ProviderSafetyTest.A));});server.start();
  try(var p=new AdditionalProvider("pvptiers",ProviderSafetyTest.base(server),clock::get)){
   p.requestBackground(ProviderSafetyTest.A,false);ProviderSafetyTest.await(()->p.resolved(ProviderSafetyTest.A));
   clock.addAndGet(300_001);p.requestBackground(ProviderSafetyTest.A,false);Thread.sleep(100);assertEquals(1,calls.get());assertTrue(p.tier(ProviderSafetyTest.A,"sword").isPresent());
   p.requestBackground(ProviderSafetyTest.A,true);ProviderSafetyTest.await(()->calls.get()==2);ProviderSafetyTest.await(()->p.lastSuccessMillis()==clock.get());
   clock.addAndGet(900_001);p.requestBackground(ProviderSafetyTest.A,false);ProviderSafetyTest.await(()->calls.get()==3);
  }finally{server.stop(0);}
 }
 @Test void suspendCancelsDelayedBatchesAndExplicitLookupStillWorks()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
  server.createContext("/",e->{calls.incrementAndGet();ProviderSafetyTest.respond(e,200,"{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{\""+ProviderSafetyTest.A.toString().replace("-","")+"\":{}}}");});server.start();
  try(var p=new CollapseClient(ProviderSafetyTest.base(server))){p.request(ProviderSafetyTest.A);p.suspend();Thread.sleep(650);assertEquals(0,calls.get());p.requestForeground(ProviderSafetyTest.A);ProviderSafetyTest.await(()->p.resolved(ProviderSafetyTest.A));assertEquals(1,calls.get());}
  finally{server.stop(0);}
 }
 @Test void repeatedFailuresBackOffAndLocalCountersMeasureResponses()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();var clock=new AtomicLong(System.currentTimeMillis());
  server.createContext("/",e->{calls.incrementAndGet();ProviderSafetyTest.respond(e,503,"service unavailable");});server.start();var before=TrafficStats.snapshot("pvptiers");
  try(var p=new AdditionalProvider("pvptiers",ProviderSafetyTest.base(server),clock::get)){
   p.request(ProviderSafetyTest.A);ProviderSafetyTest.await(()->p.lookup(ProviderSafetyTest.A).failed());long first=p.retryAtMillis();assertEquals(60_000,first-clock.get());
   clock.set(first+1);p.requestRetry(ProviderSafetyTest.A);ProviderSafetyTest.await(()->calls.get()==2);ProviderSafetyTest.await(()->p.retryAtMillis()>clock.get());assertEquals(120_000,p.retryAtMillis()-clock.get());
   var after=TrafficStats.snapshot("pvptiers");assertEquals(2,after.requests()-before.requests());assertEquals(38,after.responseBodyBytes()-before.responseBodyBytes());p.suspend();assertTrue(p.retryAtMillis()>clock.get());
  }finally{server.stop(0);}
 }
}
