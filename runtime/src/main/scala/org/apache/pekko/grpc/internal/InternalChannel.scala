/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import scala.concurrent.Future
import org.apache.pekko
import pekko.{ Done, NotUsed }
import pekko.annotation.InternalApi
import pekko.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import pekko.stream.scaladsl.Source
import io.grpc.{ CallOptions, MethodDescriptor }

/**
 * INTERNAL API
 */
@InternalApi
abstract class InternalChannel {
  def invoke[I, O](
      request: I,
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      options: CallOptions): Future[O]
  def invokeWithMetadata[I, O](
      request: I,
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      options: CallOptions): Future[GrpcSingleResponse[O]]

  def invokeWithMetadata[I, O](
      source: Source[I, NotUsed],
      headers: MetadataImpl,
      descriptor: MethodDescriptor[I, O],
      streamingResponse: Boolean,
      options: CallOptions): Source[O, Future[GrpcResponseMetadata]]

  def shutdown(): Unit
  def done: Future[Done]
}
