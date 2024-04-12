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

// #full-server
package example.myapp.helloworld;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;

class GreeterServer {
  public static void main(String[] args) throws Exception {
    // important to enable HTTP/2 in ActorSystem's config
    Config conf =
        ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
            .withFallback(ConfigFactory.defaultApplication());

    // ActorSystem Boot
    ActorSystem sys = ActorSystem.create("HelloWorld", conf);

    run(sys)
        .thenAccept(
            binding -> System.out.println("gRPC server bound to: " + binding.localAddress()));

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
    Materializer mat = SystemMaterializer.get(sys).materializer();

    // Instantiate implementation
    GreeterService impl = new GreeterServiceImpl(mat);

    return Http.get(sys)
        .newServerAt("127.0.0.1", 8090)
        .bind(GreeterServiceHandlerFactory.create(impl, sys));
  }
}
// #full-server
