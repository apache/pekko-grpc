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

package example.myapp.echo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import example.myapp.echo.grpc.*;

public class EchoServiceImpl implements EchoService {

  @Override
  public CompletionStage<EchoMessage> echo(EchoMessage in) {
    return CompletableFuture.completedFuture(in);
  }
}

