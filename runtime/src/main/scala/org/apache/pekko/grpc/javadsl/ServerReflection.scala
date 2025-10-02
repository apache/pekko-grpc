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

package org.apache.pekko.grpc.javadsl

import java.util.Collection
import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.grpc.ServiceDescription
import pekko.grpc.internal.ServerReflectionImpl
import pekko.http.javadsl.model.{ HttpRequest, HttpResponse }

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

@ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
object ServerReflection {
  @ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
  def create(
      objects: Collection[ServiceDescription],
      sys: ClassicActorSystemProvider): pekko.japi.function.Function[HttpRequest, CompletionStage[HttpResponse]] = {
    import scala.jdk.CollectionConverters._
    val delegate = ServerReflectionHandler.apply(
      ServerReflectionImpl(objects.asScala.map(_.descriptor).toSeq, objects.asScala.map(_.name).toList))(sys)
    import scala.jdk.FutureConverters._
    // implicit val ec = sys.classicSystem.dispatcher
    request =>
      delegate
        .apply(request.asInstanceOf[pekko.http.scaladsl.model.HttpRequest])
        .asJava
        .asInstanceOf[CompletionStage[HttpResponse]]
  }
}
