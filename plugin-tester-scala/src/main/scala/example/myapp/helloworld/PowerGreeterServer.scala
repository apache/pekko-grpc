/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.http.scaladsl.Http
import example.myapp.helloworld.grpc._

import scala.concurrent.{ ExecutionContext, Future }

class PowerGreeterServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    // Pekko boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      GreeterServicePowerApiHandler(new PowerGreeterServiceImpl())

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().newServerAt("127.0.0.1", 8081).bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}

//#full-server
