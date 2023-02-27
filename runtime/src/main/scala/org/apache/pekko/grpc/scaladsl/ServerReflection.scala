/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.grpc.ServiceDescription
import org.apache.pekko.grpc.internal.ServerReflectionImpl
import org.apache.pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }

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
