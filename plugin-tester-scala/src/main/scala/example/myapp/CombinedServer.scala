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

package example.myapp

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.grpc.scaladsl.ServerReflection
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld._
import example.myapp.echo._
import example.myapp.echo.grpc._
import example.myapp.helloworld.grpc.GreeterService

//#concatOrNotFound
import org.apache.pekko.grpc.scaladsl.ServiceHandler

//#concatOrNotFound

//#grpc-web
import org.apache.pekko.grpc.scaladsl.WebHandler

//#grpc-web

object CombinedServer {
  def main(args: Array[String]): Unit = {
    // important to enable HTTP/2 in ActorSystem's config
    val conf = ConfigFactory
      .parseString("pekko.http.server.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val sys: ActorSystem = ActorSystem("HelloWorld", conf)
    implicit val ec: ExecutionContext = sys.dispatcher

    // #concatOrNotFound
    // explicit types not needed but included in example for clarity
    val greeterService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      example.myapp.helloworld.grpc.GreeterServiceHandler.partial(new GreeterServiceImpl())
    val echoService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      EchoServiceHandler.partial(new EchoServiceImpl)
    val reflectionService = ServerReflection.partial(List(GreeterService, EchoService))
    val serviceHandlers: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(greeterService, echoService, reflectionService)

    Http()
      .newServerAt("127.0.0.1", 8080)
      .bind(serviceHandlers)
      // #concatOrNotFound
      .foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    // #grpc-web
    val grpcWebServiceHandlers = WebHandler.grpcWebHandler(greeterService, echoService)

    Http()
      .newServerAt("127.0.0.1", 8081)
      .bind(grpcWebServiceHandlers)
      // #grpc-web
      .foreach { binding => println(s"gRPC-Web server bound to: ${binding.localAddress}") }
  }
}
