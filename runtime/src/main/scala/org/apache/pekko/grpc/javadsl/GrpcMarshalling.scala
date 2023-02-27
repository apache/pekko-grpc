/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.Optional
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.grpc._
import org.apache.pekko.grpc.internal._
import org.apache.pekko.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import org.apache.pekko.http.javadsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import org.apache.pekko.japi.{ Function => JFunction }
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Source
import org.apache.pekko.util.ByteString
import scala.annotation.nowarn

object GrpcMarshalling {

  def negotiated[T](
      req: HttpRequest,
      f: (GrpcProtocolReader, GrpcProtocolWriter) => CompletionStage[T]): Optional[CompletionStage[T]] =
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeReader, writer) =>
          maybeReader.map(reader => f(reader, writer)).fold[CompletionStage[T]](failure, identity)
      }
      .fold(Optional.empty[CompletionStage[T]])(Optional.of)

  def unmarshal[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[T] =
    data.via(reader.dataFrameDecoder).map(u.deserialize).runWith(SingleParameterSink.create[T](), mat)

  def unmarshal[T](
      entity: HttpEntity,
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[T] =
    unmarshal(entity.getDataBytes, u, mat, reader)

  def unmarshalStream[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      @nowarn("cat=unused-params") mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[Source[T, NotUsed]] = {
    CompletableFuture.completedFuture[Source[T, NotUsed]](
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(reader.dataFrameDecoder)
        .map(japiFunction(u.deserialize))
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage)
        .mapMaterializedValue(japiFunction(_ => NotUsed)))
  }
  def unmarshalStream[T](
      entity: HttpEntity,
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[Source[T, NotUsed]] =
    unmarshalStream(entity.getDataBytes, u, mat, reader)

  def marshal[T](
      e: T,
      m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: JFunction[ActorSystem, JFunction[Throwable, Trailers]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    GrpcResponseHelpers.responseForSingleElement(e, scalaAnonymousPartialFunction(eHandler))(m, writer, system)

  def marshalStream[T](
      e: Source[T, NotUsed],
      m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: JFunction[ActorSystem, JFunction[Throwable, Trailers]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    GrpcResponseHelpers(e.asScala, scalaAnonymousPartialFunction(eHandler))(m, writer, system)

  private def failure[R](error: Throwable): CompletableFuture[R] = {
    val future: CompletableFuture[R] = new CompletableFuture()
    future.completeExceptionally(error)
    future
  }
}
