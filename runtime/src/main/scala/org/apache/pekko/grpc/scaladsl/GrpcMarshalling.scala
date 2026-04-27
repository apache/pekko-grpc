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

package org.apache.pekko.grpc.scaladsl

import io.grpc.Status

import scala.annotation.nowarn
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalApi
import pekko.grpc._
import pekko.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import pekko.grpc.internal._
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, Uri }
import pekko.http.scaladsl.util.FastFuture
import pekko.stream.Materializer
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

object GrpcMarshalling {
  def unmarshal[T](req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[T] = {
    negotiated(
      req,
      (r, _) => {
        implicit val reader: GrpcProtocolReader = r
        unmarshal(req.entity)
      }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def unmarshalStream[T](
      req: HttpRequest)(implicit u: ProtobufSerializer[T], mat: Materializer): Future[Source[T, NotUsed]] = {
    negotiated(
      req,
      (r, _) => {
        implicit val reader: GrpcProtocolReader = r
        unmarshalStream(req.entity)
      }).getOrElse(throw new GrpcServiceException(Status.UNIMPLEMENTED))
  }

  def negotiated[T](req: HttpRequest, f: (GrpcProtocolReader, GrpcProtocolWriter) => Future[T]): Option[Future[T]] =
    GrpcProtocol.negotiate(req).map {
      case (Success(reader), writer) => f(reader, writer)
      case (Failure(ex), _)          => Future.failed(ex)
    }

  def unmarshal[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): Future[T] = {
    data.via(reader.dataFrameDecoder).map(u.deserialize).runWith(SingleParameterSink())
  }
  def unmarshal[T](
      entity: HttpEntity)(implicit u: ProtobufSerializer[T], mat: Materializer, reader: GrpcProtocolReader): Future[T] =
    entity match {
      case HttpEntity.Strict(_, data) => Future.fromTry(Try(u.deserialize(reader.decodeSingleFrame(data))))
      case _                          => unmarshal(entity.dataBytes)
    }

  def unmarshalStream[T](data: Source[ByteString, Any])(
      implicit u: ProtobufSerializer[T],
      @nowarn("msg=is never used") mat: Materializer,
      reader: GrpcProtocolReader): Future[Source[T, NotUsed]] = {
    Future.successful(
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(reader.dataFrameDecoder)
        .map(u.deserialize)
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage))
  }

  def unmarshalStream[T](entity: HttpEntity)(
      implicit u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): Future[Source[T, NotUsed]] =
    unmarshalStream(entity.dataBytes)

  def marshal[T](
      e: T = Identity,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers.responseForSingleElement(e, eHandler)

  def marshalStream[T](
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    GrpcResponseHelpers(e, eHandler)
  }

  @InternalApi
  def handleUnary[In, Out](
      entity: HttpEntity,
      implementation: In => Future[Out],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit u: ProtobufSerializer[In],
      m: ProtobufSerializer[Out],
      mat: Materializer,
      reader: GrpcProtocolReader,
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      ec: ExecutionContext): Future[HttpResponse] = {
    entity match {
      case HttpEntity.Strict(_, data) =>
        try {
          val in = u.deserialize(reader.decodeSingleFrame(data))
          invokeUnary(in, implementation, eHandler)
        } catch {
          case NonFatal(ex) => unaryExceptionHandler(eHandler)(system, writer)(ex)
        }
      case _ =>
        val requestFuture = unmarshal[In](entity)(u, mat, reader)
        requestFuture.value match {
          case Some(Success(in)) => invokeUnary(in, implementation, eHandler)
          case Some(Failure(ex)) => unaryExceptionHandler(eHandler)(system, writer)(ex)
          case None              =>
            val exceptionHandler = unaryExceptionHandler(eHandler)
            requestFuture
              .flatMap(in => invokeUnary(in, implementation, eHandler))
              .recoverWith(exceptionHandler)
        }
    }
  }

  @inline private def invokeUnary[In, Out](
      in: In,
      implementation: In => Future[Out],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[Out],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      ec: ExecutionContext): Future[HttpResponse] =
    try handleUnaryResponse(implementation(in), eHandler)
    catch {
      case NonFatal(ex) => unaryExceptionHandler(eHandler)(system, writer)(ex)
    }

  @inline private def handleUnaryResponse[Out](
      responseFuture: Future[Out],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[Out],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      ec: ExecutionContext): Future[HttpResponse] =
    responseFuture.value match {
      case Some(Success(out)) => marshalUnaryResponse(out, eHandler)
      case Some(Failure(ex))  => unaryExceptionHandler(eHandler)(system, writer)(ex)
      case None               =>
        val exceptionHandler = unaryExceptionHandler(eHandler)
        responseFuture.map(out => marshal[Out](out, eHandler)(m, writer, system)).recoverWith(exceptionHandler)
    }

  @inline private def marshalUnaryResponse[Out](
      out: Out,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[Out],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): Future[HttpResponse] =
    try FastFuture.successful(marshal[Out](out, eHandler)(m, writer, system))
    catch {
      case NonFatal(ex) => unaryExceptionHandler(eHandler)(system, writer)(ex)
    }

  @inline private def unaryExceptionHandler(eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    GrpcExceptionHandler.from(eHandler(system.classicSystem))

  @InternalApi
  def marshalRequest[T](
      uri: Uri,
      e: T,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpRequest =
    marshalStreamRequest(uri, Source.single(e), eHandler)

  @InternalApi
  def marshalStreamRequest[T](
      uri: Uri,
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpRequest =
    GrpcRequestHelpers(uri, List.empty, e, eHandler)

}
