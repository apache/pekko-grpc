/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.javadsl

import java.util
import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.grpc.javadsl.ServiceHandler.{ concatOrNotFound, unsupportedMediaType }
import pekko.http.javadsl.marshalling.Marshaller
import pekko.http.javadsl.model.{ HttpRequest, HttpResponse }
import pekko.http.javadsl.server.Route
import pekko.http.javadsl.server.directives.RouteAdapter
import pekko.http.scaladsl.marshalling.{ Marshaller => sMarshaller, ToResponseMarshaller }
import pekko.grpc.scaladsl
import pekko.http.scaladsl.server.directives.MarshallingDirectives
import pekko.japi.function.{ Function => JFunction }
import pekko.stream.Materializer
import pekko.stream.javadsl.{ Keep, Sink, Source }
import pekko.util.ConstantFun
import ch.megard.pekko.http.cors.javadsl.settings.CorsSettings
import ch.megard.pekko.http.cors.javadsl.CorsDirectives

@ApiMayChange
object WebHandler {

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - The generated handler supports the `application/grpc-web` and `application/grpc-web-text` media types.
   *  - CORS is implemented for handled servives, including pre-flight requests and request enforcement.
   *  - If the request s not a CORS pre-flight request, and has an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  def grpcWebHandler(
      handlers: util.List[JFunction[HttpRequest, CompletionStage[HttpResponse]]],
      as: ClassicActorSystemProvider,
      mat: Materializer): JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    grpcWebHandler(handlers, as, mat, scaladsl.WebHandler.defaultCorsSettings)

  // Adapt Marshaller.futureMarshaller(fromResponse) to javadsl
  private implicit val csResponseMarshaller: ToResponseMarshaller[CompletionStage[HttpResponse]] = {
    import scala.compat.java8.FutureConverters._
    // HACK: Only known way to lift this to the scaladsl.model types required for MarshallingDirectives.handleWith
    Marshaller.asScalaToResponseMarshaller(
      Marshaller
        .fromScala(sMarshaller.futureMarshaller(sMarshaller.opaque(ConstantFun.scalaIdentityFunction[HttpResponse])))
        .compose[CompletionStage[HttpResponse]](_.toScala))
  }

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bind` for the generated partial function handlers:
   *  - The generated handler supports the `application/grpc-web` and `application/grpc-web-text` media types.
   *  - CORS is implemented for handled servives, including pre-flight requests and request enforcement.
   *  - If the request s not a CORS pre-flight request, and has an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  def grpcWebHandler(
      handlers: util.List[JFunction[HttpRequest, CompletionStage[HttpResponse]]],
      as: ClassicActorSystemProvider,
      mat: Materializer,
      corsSettings: CorsSettings): JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    import scala.collection.JavaConverters._

    val servicesHandler = concatOrNotFound(handlers.asScala.toList: _*)
    val servicesRoute = RouteAdapter(MarshallingDirectives.handleWith(servicesHandler.apply(_)))
    val handler = asyncHandler(CorsDirectives.cors(corsSettings, () => servicesRoute), as, mat)
    (req: HttpRequest) =>
      if (scaladsl.ServiceHandler.isGrpcWebRequest(req) /*|| scaladsl.WebHandler.isCorsPreflightRequest(req)*/ )
        handler(req)
      else unsupportedMediaType
  }

  // Java version of Route.asyncHandler
  private def asyncHandler(
      route: Route,
      as: ClassicActorSystemProvider,
      mat: Materializer): HttpRequest => CompletionStage[HttpResponse] = {
    val sealedFlow =
      route
        .seal()
        .flow(as.classicSystem, mat)
        .toMat(Sink.head[HttpResponse](), Keep.right[NotUsed, CompletionStage[HttpResponse]])
    (req: HttpRequest) => Source.single(req).runWith(sealedFlow, mat)
  }
}
