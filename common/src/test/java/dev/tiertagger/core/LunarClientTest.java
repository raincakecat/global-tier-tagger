package dev.tiertagger.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LunarClientTest {
 @Test void incompleteBatchOnlyFailsTheMissingPlayer() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);UUID good=UUID.randomUUID(),missing=UUID.randomUUID();
  server.createContext("/",e->{byte[] body=("{\"schemaVersion\":1,\"cacheSeconds\":120,\"players\":{\""+good.toString().replace("-","")+"\":{\"sword\":\"HT2\"}}}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});server.start();
  try(var p=new LunarClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))){p.request(good);p.request(missing);ProviderSafetyTest.await(()->p.lookup(missing).failed());assertTrue(p.resolved(good));assertFalse(p.resolved(missing));assertEquals("HT2",p.tier(good,"sword").orElseThrow());assertEquals(0,p.retryAtMillis());assertEquals("Lunar Tiers connected",p.status());}
  finally{server.stop(0);}
 }
 @Test void decodeRejectsPartialOrInvalidResponses(){
  UUID id=UUID.fromString("12345678-1234-4234-8234-123456789abc");String key=id.toString().replace("-","");
  String good="{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{\""+key+"\":{\"spear-mace\":\"HT3\",\"sword\":\"LT2\"}}}";
  assertEquals(Map.of("spear-mace","HT3","sword","LT2"),LunarClient.decode(good,List.of(id)).get(id));
  assertThrows(IllegalArgumentException.class,()->LunarClient.decode(good.replace("LT2","FAKE"),List.of(id)));
  assertThrows(IllegalArgumentException.class,()->LunarClient.decode("{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{}}",List.of(id)));
 }
 @Test void sendsBatchesAndCachesResults() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();
  server.createContext("/v1/ranks/lookup",exchange->{try{
   calls.incrementAndGet();JsonArray ids=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("uuids");
   if(ids.size()>100)throw new AssertionError("batch too large");JsonObject players=new JsonObject();for(JsonElement id:ids){JsonObject ranks=new JsonObject();ranks.addProperty("sword","HT3");players.add(id.getAsString(),ranks);}
   JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("cacheSeconds",60);root.add("players",players);byte[] bytes=root.toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
  }finally{exchange.close();}});server.start();
  try(var client=new LunarClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/ranks/lookup"))){
   List<UUID> ids=new ArrayList<>();for(int i=0;i<101;i++){UUID id=new UUID(0x4000L,0x8000000000000000L|i);ids.add(id);client.request(id);}
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(client.tier(ids.getLast(),"sword").isEmpty()&&System.nanoTime()<deadline)Thread.sleep(30);
   assertEquals("HT3",client.tier(ids.getLast(),"sword").orElseThrow());assertEquals(2,calls.get());
   client.request(ids.getFirst());Thread.sleep(200);assertEquals(2,calls.get());client.clear();assertTrue(client.tier(ids.getFirst(),"sword").isEmpty());
  }finally{server.stop(0);}
 }
 @Test void avoidsDuplicateRequestsWhileLookupIsInFlight() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
  server.createContext("/",exchange->{try{
   calls.incrementAndGet();String id=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("uuids").get(0).getAsString();
   entered.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS);
   byte[] body=("{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{\""+id+"\":{}}}").getBytes(StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
  }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});server.start();
  try(var client=new LunarClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))){
   UUID id=UUID.randomUUID();client.request(id);assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   client.request(id);release.countDown();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
   while(!client.status().equals("Lunar Tiers connected")&&System.nanoTime()<deadline)Thread.sleep(20);
   assertEquals("Lunar Tiers connected",client.status());Thread.sleep(250);assertEquals(1,calls.get());
  }finally{release.countDown();server.stop(0);}
 }

}
