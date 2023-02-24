/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import scala.concurrent.Future
import org.apache.pekko.{ Done, NotUsed }
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import org.apache.pekko.stream.scaladsl.Source
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
