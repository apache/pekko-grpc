/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.grpc.ServiceDescription
import pekko.grpc.internal.ServerReflectionImpl
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

@ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
object ServerReflection {
  @ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
  def apply(objects: List[ServiceDescription])(
      implicit sys: ClassicActorSystemProvider): HttpRequest => scala.concurrent.Future[HttpResponse] =
    ServerReflectionHandler.apply(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))

  @ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
  def partial(objects: List[ServiceDescription])(
      implicit sys: ClassicActorSystemProvider): PartialFunction[HttpRequest, scala.concurrent.Future[HttpResponse]] =
    ServerReflectionHandler.partial(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))
}
