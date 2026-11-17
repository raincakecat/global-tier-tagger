package dev.tiertagger.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NewProvidersTest {
 private static final UUID A=ProviderSafetyTest.A,B=ProviderSafetyTest.B,C=ProviderSafetyTest.C;
 private static String player(String name,String platform){return "{\"username\":\""+name+"\",\"platform\":\""+platform+"\",\"tiers\":[{\"gamemode\":\"mace-spear\",\"tier\":\"LT2\"},{\"gamemode\":\"nethertite\",\"tier\":\"HT3\"},{\"gamemode\":\"cart\",\"tier\":\"LT4\"}]}";}
 @Test void globalUsesCurrentRankAndRejectsWrongUuid(){
  String json="{\"uuid\":\""+A+"\",\"account_type\":\"premium\",\"gamemodes\":[{\"gamemode\":\"Diapot\",\"rank\":\"LT3\",\"peak_tier\":\"HT1\"},{\"gamemode\":\"Nethpot\",\"rank\":null,\"peak_tier\":\"HT2\"}]}";
  assertEquals(Map.of("pot","LT3"),ProviderParsers.parse("global",json,A));
  assertThrows(IllegalArgumentException.class,()->ProviderParsers.parse("global",json,B));
  assertThrows(IllegalArgumentException.class,()->ProviderParsers.parse("global",json.replace("LT3","bad"),A));
  assertTrue(ProviderParsers.parse("global",json.replace("premium","cracked"),A).isEmpty());
 }
 @Test void primeMapsItsModesAndRejectsDifferentNames(){
  assertEquals(Map.of("spear-mace","LT2","nethpot","HT3","minecart","LT4"),ProviderParsers.prime(player("TestA","premium"),"testa"));
  assertThrows(IllegalArgumentException.class,()->ProviderParsers.prime(player("TestA","premium"),"TestB"));
  assertTrue(ProviderParsers.prime(player("TestA","cracked"),"TestA").isEmpty());
 }
 @Test void primeBatchesCachesAndSeparatesIdentityFailures()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();
  server.createContext("/",e->{calls.incrementAndGet();assertEquals("GET",e.getRequestMethod());assertTrue(e.getRequestURI().getQuery().contains("names="));ProviderSafetyTest.respond(e,200,"{\"count\":2,\"items\":["+player("TestA","premium")+","+player("TestB","premium")+"],\"missing\":[\"testc\"]}");});server.start();
  try(var p=new PrimeClient(ProviderSafetyTest.base(server),name->CompletableFuture.completedFuture(new MinecraftIdentityResolver.Identity(A,name)))){
   p.identify(A,"TestA");p.identify(B,"TestB");p.identify(C,"TestC");p.request(A);p.request(B);p.request(C);
   ProviderSafetyTest.await(()->p.resolved(C));assertTrue(p.resolved(A));assertEquals("LT2",p.tier(A,"spear-mace").orElseThrow());assertTrue(p.lookup(B).failed());assertFalse(p.resolved(B));assertTrue(p.tier(C,"sword").isEmpty());assertEquals(0,p.retryAtMillis());
   p.request(A);p.request(C);Thread.sleep(200);assertEquals(1,calls.get());
  }finally{server.stop(0);}
 }
 @Test void incompletePrimeResponseIsNotAnUnrankedResult()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->ProviderSafetyTest.respond(e,200,"{\"count\":0,\"items\":[],\"missing\":[]}"));server.start();
  try(var p=new PrimeClient(ProviderSafetyTest.base(server),n->{throw new AssertionError("No identity call expected");})){p.identify(A,"TestA");p.request(A);ProviderSafetyTest.await(()->p.lookup(A).failed());assertFalse(p.resolved(A));}
  finally{server.stop(0);}
 }
 @Test void collapseUsesUuidBatchAndCachesLinkedAndUnlinkedResults()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();
  server.createContext("/",e->{calls.incrementAndGet();assertEquals("POST",e.getRequestMethod());JsonArray ids=JsonParser.parseString(new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("uuids");assertEquals(2,ids.size());ProviderSafetyTest.respond(e,200,"{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{\""+A.toString().replace("-","")+"\":{\"crystal\":\"LT1\"},\""+B.toString().replace("-","")+"\":{}}}");});server.start();
  try(var p=new CollapseClient(ProviderSafetyTest.base(server))){p.request(A);p.request(B);ProviderSafetyTest.await(()->p.resolved(B));assertEquals("LT1",p.tier(A,"crystal").orElseThrow());assertTrue(p.tier(B,"crystal").isEmpty());p.request(A);Thread.sleep(200);assertEquals(1,calls.get());assertEquals("collapse",p.id());assertEquals("Collapse Tiers connected",p.status());}
  finally{server.stop(0);}
 }
 @Test void newProvidersHaveInfoAndUnfinishedListsStayHidden(){
  for(String id:List.of("global","prime","collapse")){assertTrue(ProviderCatalog.find(id).supported());assertFalse(ProviderInformation.details(id).isEmpty());}
  for(String id:List.of("solar","astra","cbtl","central","crystalranked"))assertFalse(ProviderCatalog.find(id).supported());
  assertNull(ProviderCatalog.find("sm"));
 }
}
