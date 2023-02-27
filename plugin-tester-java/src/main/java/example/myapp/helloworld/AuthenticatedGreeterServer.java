/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;

import org.apache.pekko.http.javadsl.model.StatusCodes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.Materializer;

import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;

import static org.apache.pekko.http.javadsl.server.Directives.*;

class AuthenticatedGreeterServer {
  public static void main(String[] args) throws Exception {
    // important to enable HTTP/2 in ActorSystem's config
    Config conf = ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
            .withFallback(ConfigFactory.defaultApplication());

    // ActorSystem Boot
    ActorSystem sys = ActorSystem.create("HelloWorld", conf);

    run(sys).thenAccept(binding -> {
      System.out.println("gRPC server bound to: " + binding.localAddress());
    });

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
    Materializer mat = SystemMaterializer.get(sys).materializer();

    //#http-route
    // A Route to authenticate with
    Route authentication = path("login", () ->
      get(() ->
        complete("Psst, please use token XYZ!")
      )
    );
    //#http-route

    //#grpc-route
    // Instantiate implementation
    GreeterService impl = new GreeterServiceImpl(mat);
    Function<HttpRequest, CompletionStage<HttpResponse>> handler = GreeterServiceHandlerFactory.create(impl, sys);

    // As a Route
    Route handlerRoute = handle(handler);
    //#grpc-route

    //#grpc-protected
    // Protect the handler route
    Route protectedHandler =
      headerValueByName("token", token -> {
        if ("XYZ".equals(token)) {
          return handlerRoute;
        } else {
          return complete(StatusCodes.UNAUTHORIZED);
        }
      });
    //#grpc-protected

    //#combined
    Route finalRoute = concat(
      authentication,
      protectedHandler
    );

    return Http.get(sys)
      .newServerAt("127.0.0.1", 8090)
      .bind(finalRoute);
    //#combined
  }
}
