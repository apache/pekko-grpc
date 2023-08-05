/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import example.myapp.helloworld.grpc.*;

class GreeterServiceImpl implements GreeterService {
  public CompletionStage<HelloReply> sayHello(HelloRequest request) {
    return CompletableFuture.completedFuture(HelloReply.newBuilder().setMessage("Hello, " + request.getName()).build());
  }

  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    throw new UnsupportedOperationException();
  }

  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    throw new UnsupportedOperationException();
  }

  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest request) {
    throw new UnsupportedOperationException();
  }
}
