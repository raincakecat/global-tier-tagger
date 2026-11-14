package dev.tiertagger.core;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class MinecraftIdentityResolverTest {
 @Test void validatesNamesDeduplicatesAndCachesPremiumIdentity() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var count=new AtomicInteger();server.createContext("/",e->{count.incrementAndGet();RestrictionClientTest.respond(e,200,"{\"id\":\"11111111222243338444555555555555\",\"name\":\"FixturePlayer\"}");});server.start();
  try(var resolver=new MinecraftIdentityResolver(RestrictionClientTest.base(server))){assertThrows(CompletionException.class,()->resolver.resolve("bad/name").join());assertEquals(0,count.get());var a=resolver.resolve("FixturePlayer");var b=resolver.resolve("fixtureplayer");assertEquals(RestrictionClientTest.ID,a.get().uuid());assertEquals(a.get(),b.get());resolver.resolve("FixturePlayer").get();assertEquals(1,count.get());assertThrows(ExecutionException.class,()->resolver.resolve("OtherPlayer").get());}finally{server.stop(0);}
 }
 @Test void missingAccountAndOutageFailWithoutIdentity() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->RestrictionClientTest.respond(e,e.getRequestURI().toString().contains("Missing")?404:503,"{}"));server.start();try(var resolver=new MinecraftIdentityResolver(RestrictionClientTest.base(server))){assertThrows(ExecutionException.class,()->resolver.resolve("Missing").get());assertThrows(ExecutionException.class,()->resolver.resolve("Outage").get());}finally{server.stop(0);}
 }
}
