/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.grpc.{ ProtobufSerializer, Trailers }
import org.apache.pekko.grpc.GrpcProtocol.GrpcProtocolWriter
import org.apache.pekko.http.scaladsl.model.HttpEntity.ChunkStreamPart
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.NotUsed
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.grpc.scaladsl.GrpcExceptionHandler
import org.apache.pekko.grpc.scaladsl.headers._
import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.{ HttpEntity, HttpHeader, HttpMethods, HttpRequest, TransferEncodings, Uri }

import scala.collection.immutable

@InternalApi
object GrpcRequestHelpers {

  def apply[T](
      uri: Uri,
      headers: immutable.Seq[HttpHeader],
      e: Source[T, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpRequest =
    request(uri, headers, GrpcEntityHelpers(e, trail = Source.empty, eHandler))

  private def request[T](uri: Uri, headers: immutable.Seq[HttpHeader], entity: Source[ChunkStreamPart, NotUsed])(
      implicit writer: GrpcProtocolWriter): HttpRequest = {
    HttpRequest(
      uri = uri,
      method = HttpMethods.POST,
      // FIXME issue #1382 gzip shouldn't be included by default in Message-Accept-Encoding.
      headers = immutable.Seq(
        `Message-Encoding`(writer.messageEncoding.name),
        `Message-Accept-Encoding`(Codecs.supportedCodecs.map(_.name).mkString(",")),
        model.headers.TE(TransferEncodings.trailers)) ++ headers,
      entity = HttpEntity.Chunked(writer.contentType, entity))
  }

}
