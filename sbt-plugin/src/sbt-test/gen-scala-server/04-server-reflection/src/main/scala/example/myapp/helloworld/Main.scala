/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package example.myapp.helloworld

import com.typesafe.config._

import scala.concurrent._

import org.apache.pekko
import pekko.actor._
import pekko.stream._

//#server-reflection
import org.apache.pekko
import pekko.http.scaladsl._
import pekko.http.scaladsl.model._

import pekko.grpc.scaladsl.ServiceHandler
import pekko.grpc.scaladsl.ServerReflection

import example.myapp.helloworld.grpc._

//#server-reflection

object Main extends App {
  val conf = ConfigFactory
    .parseString("pekko.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())
  implicit val sys = ActorSystem("HelloWorld", conf)

  implicit val ec: ExecutionContext = sys.dispatcher

  // #server-reflection
  // Create service handler with a fallback to a Server Reflection handler.
  // `.withServerReflection` is a convenience method that contacts a partial
  // function of the provided service with a reflection handler for that
  // same service.
  val greeter: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler.withServerReflection(new GreeterServiceImpl())

  // Bind service handler servers to localhost:8080
  val binding = Http().bindAndHandleAsync(
    greeter,
    interface = "127.0.0.1",
    port = 8080,
    connectionContext = HttpConnectionContext())
  // #server-reflection

  // report successful binding
  binding.foreach { binding =>
    println(s"gRPC server bound to: ${binding.localAddress}")
  }

  // #server-reflection-manual-concat
  // Create service handlers
  val greeterPartial: PartialFunction[HttpRequest, Future[HttpResponse]] =
    GreeterServiceHandler.partial(new GreeterServiceImpl(), "greeting-prefix")
  val echoPartial: PartialFunction[HttpRequest, Future[HttpResponse]] =
    EchoServiceHandler.partial(new EchoServiceImpl())
  // Create the reflection handler for multiple services
  val reflection =
    ServerReflection.partial(List(GreeterService, EchoService))

  // Concatenate the partial functions into a single handler
  val handler =
    ServiceHandler.concatOrNotFound(
      greeterPartial,
      echoPartial,
      reflection)
  // #server-reflection-manual-concat

}
