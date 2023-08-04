/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package example.myapp.echo

import scala.concurrent.Future

import example.myapp.echo.grpc._

class EchoServiceImpl extends EchoService {
  def echo(in: EchoMessage): Future[EchoMessage] = Future.successful(in)
}
