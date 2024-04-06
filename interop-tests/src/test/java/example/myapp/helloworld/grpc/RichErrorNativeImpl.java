/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld.grpc;

import com.google.rpc.Code;
import com.google.rpc.error_details.LocalizedMessage;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.NotUsed;
import org.apache.pekko.grpc.GrpcServiceException;
import org.apache.pekko.stream.javadsl.Source;
import scala.collection.JavaConverters;

public class RichErrorNativeImpl implements GreeterService {

  // #rich_error_model_unary
  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {

    ArrayList<scalapb.GeneratedMessage> ar = new ArrayList<>();
    ar.add(LocalizedMessage.of("EN", "The password!"));

    GrpcServiceException exception =
        GrpcServiceException.apply(
            Code.INVALID_ARGUMENT, "What is wrong?", JavaConverters.asScalaBuffer(ar).toSeq());

    CompletableFuture<HelloReply> future = new CompletableFuture<>();
    future.completeExceptionally(exception);
    return future;
  }
  // #rich_error_model_unary

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    return null;
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
    return null;
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    return null;
  }
}
