/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.grpc.GrpcProtocol
import org.apache.pekko.grpc.internal.{ GrpcProtocolWeb, GrpcProtocolWebText }
import org.apache.pekko.http.javadsl.{ model => jmodel }
import org.apache.pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }

import scala.concurrent.Future

@ApiMayChange
object ServiceHandler {

  private[scaladsl] val notFound: Future[HttpResponse] = Future.successful(HttpResponse(StatusCodes.NotFound))

  private[scaladsl] val unsupportedMediaType: Future[HttpResponse] =
    Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))

  private def matchesVariant(variants: Set[GrpcProtocol])(request: jmodel.HttpRequest) =
    variants.exists(_.mediaTypes.contains(request.entity.getContentType.mediaType))

  private[grpc] val isGrpcWebRequest: jmodel.HttpRequest => Boolean = matchesVariant(
    Set(GrpcProtocolWeb, GrpcProtocolWebText))

  def concatOrNotFound(
      handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] =
    concat(handlers: _*).orElse { case _ => notFound }

  def concat(handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*)
      : PartialFunction[HttpRequest, Future[HttpResponse]] =
    handlers.foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {
      case (acc, pf) => acc.orElse(pf)
    }
}
