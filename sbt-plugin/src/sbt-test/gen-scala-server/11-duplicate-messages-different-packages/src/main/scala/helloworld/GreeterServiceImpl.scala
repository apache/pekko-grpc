/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package helloworld

import scala.concurrent.Future

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.Source

class GreeterServiceImpl extends GreeterService {
  override def sayHello(in: HelloRequest): Future[HelloReply] = ???
  override def sayHelloA(in: a.HelloRequest): Future[HelloReply] = ???
  override def sayHelloB(in: b.HelloRequest): Future[HelloReply] = ???
}
