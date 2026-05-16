/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.Optional

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalApi
import pekko.grpc._
import pekko.grpc.internal._
import pekko.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import pekko.http.javadsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import pekko.japi.function.{ Function => JFunction }
import pekko.stream.Materializer
import pekko.stream.javadsl.Source
import pekko.util.ByteString

import scala.annotation.nowarn
import scala.util.control.NonFatal

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
    entity match {
      case strict: pekko.http.scaladsl.model.HttpEntity.Strict =>
        completedOrFailed(u.deserialize(reader.decodeSingleFrame(strict.data)))
      case _ =>
        unmarshal(entity.getDataBytes, u, mat, reader)
    }

  def unmarshalStream[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      @nowarn("msg=is never used") mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[Source[T, NotUsed]] = {
    CompletableFuture.completedFuture[Source[T, NotUsed]](
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(reader.dataFrameDecoder)
        .map(u.deserialize)
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage)
        .mapMaterializedValue(_ => NotUsed))
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

  @InternalApi
  def handleUnaryResponse[Out](
      response: CompletionStage[Out],
      m: ProtobufSerializer[Out],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: JFunction[ActorSystem, JFunction[Throwable, Trailers]]): CompletionStage[HttpResponse] =
    try {
      response match {
        case future: CompletableFuture[_] if future.isDone =>
          try completedResponse(marshal(completedValue[Out](future), m, writer, system, eHandler))
          catch {
            case NonFatal(error) => handleUnaryFailure(error, writer, system, eHandler)
          }
        case _ =>
          response
            .thenApply(out => marshal(out, m, writer, system, eHandler))
            .exceptionally(error => GrpcExceptionHandler.standard(error, eHandler, writer, system))
      }
    } catch {
      case NonFatal(error) => handleUnaryFailure(error, writer, system, eHandler)
    }

  @InternalApi
  def handleUnaryFailure(error: Throwable): CompletionStage[HttpResponse] =
    if (NonFatal(error)) failure(error)
    else throw error

  @InternalApi
  def handleUnaryFailure(
      error: Throwable,
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: JFunction[ActorSystem, JFunction[Throwable, Trailers]]): CompletionStage[HttpResponse] =
    if (NonFatal(error)) completedResponse(GrpcExceptionHandler.standard(error, eHandler, writer, system))
    else throw error

  private def completedResponse(response: HttpResponse): CompletableFuture[HttpResponse] =
    CompletableFuture.completedFuture(response)

  private def failure[R](error: Throwable): CompletableFuture[R] = {
    val future: CompletableFuture[R] = new CompletableFuture()
    future.completeExceptionally(error)
    future
  }

  private def completedOrFailed[R](value: => R): CompletionStage[R] =
    try CompletableFuture.completedFuture(value)
    catch {
      case NonFatal(error) => failure(error)
    }

  private def completedValue[T](future: CompletableFuture[_]): T =
    future.asInstanceOf[CompletableFuture[T]].getNow(null.asInstanceOf[T])
}
