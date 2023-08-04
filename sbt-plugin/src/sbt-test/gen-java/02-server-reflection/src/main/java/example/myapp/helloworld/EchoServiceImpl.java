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
import example.myapp.helloworld.grpc.*;

class EchoServiceImpl implements EchoService {
  public CompletionStage<HelloRequest> echo(HelloRequest request) {
    return CompletableFuture.completedFuture(request);
  }
}
