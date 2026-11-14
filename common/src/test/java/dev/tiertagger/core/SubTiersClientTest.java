package dev.tiertagger.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SubTiersClientTest {
 private static final UUID ID=UUID.fromString("00000000-0000-4000-8000-000000000001");
 @Test void convertsModesAndTiersWithoutInventingMissingRanks(){
  String json="{\"uuid\":\""+ID+"\",\"rankings\":{\"minecart\":{\"tier\":4,\"pos\":0},\"dia_smp\":{\"tier\":5,\"pos\":1},\"dia_crystal\":{\"tier\":2,\"pos\":0}}}";
  assertEquals(Map.of("minecart","HT4","diamond-smp","LT5","diamond-vanilla","HT2"),SubTiersClient.parse(json,ID));
  assertThrows(IllegalArgumentException.class,()->SubTiersClient.parse(json,UUID.randomUUID()));
  assertThrows(IllegalArgumentException.class,()->SubTiersClient.parse(json.replace("\"pos\":1","\"pos\":2"),ID));
 }
 @Test void cachesSuccessfulAndMissingProfiles() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger requests=new AtomicInteger();
  server.createContext("/",exchange->{try{requests.incrementAndGet();String id=exchange.getRequestURI().getPath().substring(1);if(id.endsWith("0002")){exchange.sendResponseHeaders(404,-1);return;}
   byte[] body=("{\"uuid\":\""+id+"\",\"rankings\":{\"minecart\":{\"tier\":4,\"pos\":0}}}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
  }finally{exchange.close();}});server.start();
  try(var client=new SubTiersClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))){
   client.request(ID);long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
   while(client.tier(ID,"minecart").isEmpty()&&System.nanoTime()<end)Thread.sleep(30);
   assertEquals("HT4",client.tier(ID,"minecart").orElseThrow());client.request(ID);assertEquals(1,requests.get());
   UUID missing=UUID.fromString("00000000-0000-4000-8000-000000000002");client.request(missing);while(requests.get()<2&&System.nanoTime()<end)Thread.sleep(30);
   assertEquals(2,requests.get());Thread.sleep(100);assertTrue(client.tier(missing,"minecart").isEmpty());client.request(missing);Thread.sleep(100);assertEquals(2,requests.get());
   client.clear();assertTrue(client.tier(ID,"minecart").isEmpty());
  }finally{server.stop(0);}
 }
}
