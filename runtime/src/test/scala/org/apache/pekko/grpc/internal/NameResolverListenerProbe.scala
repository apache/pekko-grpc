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

package org.apache.pekko.grpc.internal

import java.util

import org.apache.pekko.grpc.GrpcServiceException
import io.grpc.{ Attributes, EquivalentAddressGroup, NameResolver, Status }

import scala.concurrent.Promise

class NameResolverListenerProbe extends NameResolver.Listener {
  private val promise = Promise[Seq[EquivalentAddressGroup]]()

  override def onAddresses(servers: util.List[EquivalentAddressGroup], attributes: Attributes): Unit = {
    import scala.collection.JavaConverters._
    promise.trySuccess(servers.asScala.toSeq)
  }

  override def onError(error: Status): Unit =
    promise.tryFailure(new GrpcServiceException(error))

  val future = promise.future
}
