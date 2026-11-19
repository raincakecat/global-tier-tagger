package dev.tiertagger.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

/** Bounds bytes during transfer, and completes only when the entire body arrives. */
final class HttpBodies {
 static HttpResponse.BodyHandler<byte[]> limited(int maximum){return limited(maximum,n->{});}
 static HttpResponse.BodyHandler<byte[]> limited(int maximum,java.util.function.LongConsumer received){return info->new HttpResponse.BodySubscriber<>(){
  final CompletableFuture<byte[]> body=new CompletableFuture<>();
  final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return body;}
  public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
  public void onNext(List<ByteBuffer> buffers){
   for(var buffer:buffers){received.accept(buffer.remaining());if(buffer.remaining()>maximum-bytes.size()){subscription.cancel();body.completeExceptionally(new IOException("Response exceeded size limit"));return;}byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
   subscription.request(1);
  }
  public void onError(Throwable error){body.completeExceptionally(error);}
  public void onComplete(){body.complete(bytes.toByteArray());}
 };}
 private HttpBodies(){}
}
