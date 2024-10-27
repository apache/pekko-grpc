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

package org.apache.pekko.grpc.interop

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol
import pekko.grpc.internal.{ GrpcEntityHelpers, GrpcProtocolNative, GrpcResponseHelpers, Identity }
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.model.{ AttributeKeys, HttpEntity, HttpHeader, Trailer }
import pekko.http.scaladsl.server.{ Directive0, Directives, Route }
import pekko.stream.Materializer
import pekko.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages.{ SimpleRequest, StreamingOutputCallRequest }
import io.grpc.testing.integration.test.{ TestService, TestServiceHandler, TestServiceMarshallers }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Promise }

object PekkoHttpServerProviderScala extends PekkoHttpServerProvider with Directives {
  val label: String = "pekko-grpc server scala"
  val pendingCases =
    Set()

  val server: PekkoGrpcServerScala = PekkoGrpcServerScala(implicit sys => {
    val testServiceImpl = new TestServiceImpl()
    val testServiceHandler = TestServiceHandler(testServiceImpl)

    val route: Route = (pathPrefix(TestService.name) & echoHeaders) {
      handleWith(testServiceHandler)
      //  The "status_code_and_message" test can be solved either using the 'customStatusRoute' here or
      //  throwing an exception on the service code  and handling it on the appropriate GrpcMarshalling
      //  handler as demoed in 'TestServiceImpl'.
      //  customStatusRoute(testServiceImpl) ~ handleWith(testServiceHandler)
    }

    Route.toFunction(Route.seal(route))
  })

  // Directive to implement the 'custom_metadata' test
  val echoHeaders: Directive0 = extractRequest.flatMap(request => {
    val initialHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-initial")
    val trailingHeaderToEcho = request.headers.find(_.name() == "x-grpc-test-echo-trailing-bin")

    mapResponseHeaders(h => h ++ initialHeaderToEcho) & mapTrailingResponseHeaders(h => h ++ trailingHeaderToEcho)
  })

  // Route to pass the 'status_code_and_message' test
  def customStatusRoute(testServiceImpl: TestServiceImpl)(implicit mat: Materializer, system: ActorSystem): Route = {
    implicit val ec: ExecutionContext = mat.executionContext
    implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

    import TestServiceMarshallers._

    pathPrefix("UnaryCall") {
      entity(as[SimpleRequest]) { req =>
        val simpleResponse = testServiceImpl.unaryCall(req)

        req.responseStatus match {
          case None =>
            complete(simpleResponse)
          case Some(responseStatus) =>
            mapTrailingResponseHeaders(_ =>
              GrpcEntityHelpers.statusHeaders(
                Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))) {
              complete(simpleResponse)
            }
        }
      }
    } ~ pathPrefix("FullDuplexCall") {
      entity(as[Source[StreamingOutputCallRequest, NotUsed]]) { source =>
        val status = Promise[Status]()

        val effectingSource = source
          .map { requestElement =>
            requestElement.responseStatus match {
              case None =>
                status.trySuccess(Status.OK)
              case Some(responseStatus) =>
                status.trySuccess(Status.fromCodeValue(responseStatus.code).withDescription(responseStatus.message))
            }
            requestElement
          }
          .watchTermination()((NotUsed, f) => {
            f.foreach(_ => status.trySuccess(Status.OK))
            NotUsed
          })

        complete(GrpcResponseHelpers(testServiceImpl.fullDuplexCall(effectingSource), status.future))
      }
    }
  }

  // TODO move to runtime library or even pekko-http
  def mapTrailingResponseHeaders(f: immutable.Seq[HttpHeader] => immutable.Seq[HttpHeader]): Directive0 =
    mapResponse(response =>
      response.entity match {
        case HttpEntity.Chunked(contentType, data) =>
          response.withEntity(
            HttpEntity.Chunked(
              contentType,
              data.map {
                case chunk: HttpEntity.Chunk => chunk
                case last: HttpEntity.LastChunk =>
                  HttpEntity.LastChunk(last.extension, f(last.trailer))
              }))
        case _ =>
          response
            .attribute(AttributeKeys.trailer)
            .map(trailer => Trailer(f(trailer.headers.map((RawHeader.apply _).tupled))))
            .fold(response)(response.addAttribute(AttributeKeys.trailer, _))
      })
}
