/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.concurrent.CompletionStage;

//#import
import org.apache.pekko.grpc.javadsl.ServiceHandler;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;

//#import

//#grpc-web
import org.apache.pekko.grpc.javadsl.WebHandler;

//#grpc-web

import example.myapp.helloworld.*;
import example.myapp.helloworld.grpc.*;
import example.myapp.echo.*;
import example.myapp.echo.grpc.*;

class CombinedServer {
  public static void main(String[] args) {
      // important to enable HTTP/2 in ActorSystem's config
      Config conf = ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());
      ActorSystem sys = ActorSystem.create("HelloWorld", conf);
      Materializer mat = SystemMaterializer.get(sys).materializer();

      //#concatOrNotFound
      Function<HttpRequest, CompletionStage<HttpResponse>> greeterService =
          GreeterServiceHandlerFactory.create(new GreeterServiceImpl(mat), sys);
      Function<HttpRequest, CompletionStage<HttpResponse>> echoService =
        EchoServiceHandlerFactory.create(new EchoServiceImpl(), sys);
      @SuppressWarnings("unchecked")
      Function<HttpRequest, CompletionStage<HttpResponse>> serviceHandlers =
        ServiceHandler.concatOrNotFound(greeterService, echoService);

      Http.get(sys)
          .newServerAt("127.0.0.1", 8090)
          .bind(serviceHandlers)
      //#concatOrNotFound
      .thenAccept(binding -> {
        System.out.println("gRPC server bound to: " + binding.localAddress());
      });

      //#grpc-web
      Function<HttpRequest, CompletionStage<HttpResponse>> grpcWebServiceHandlers =
          WebHandler.grpcWebHandler(Arrays.asList(greeterService, echoService), sys, mat);

      Http.get(sys)
        .newServerAt("127.0.0.1", 8090)
        .bind(grpcWebServiceHandlers)
      //#grpc-web
      .thenAccept(binding -> {
          System.out.println("gRPC-Web server bound to: " + binding.localAddress());
      });

  }
}
