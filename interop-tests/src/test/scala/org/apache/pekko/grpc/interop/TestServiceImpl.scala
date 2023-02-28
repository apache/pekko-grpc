/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcServiceException
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.stream.scaladsl.{ Flow, Source }

import com.google.protobuf.ByteString

import io.grpc.Status
import io.grpc.testing.integration.empty.Empty

import scala.concurrent.{ ExecutionContext, Future }

// Generated by our plugin
import io.grpc.testing.integration.messages._
import io.grpc.testing.integration.test.TestService

object TestServiceImpl {
  val parametersToResponseFlow: Flow[ResponseParameters, StreamingOutputCallResponse, NotUsed] =
    Flow[ResponseParameters].map { parameters =>
      StreamingOutputCallResponse(Some(Payload(body = ByteString.copyFrom(new Array[Byte](parameters.size)))))
    }
}

/**
 * Implementation of the generated service.
 *
 * Essentially porting the client code from [[io.grpc.testing.integration.TestServiceImpl]] against our API's
 *
 * The same implementation is also be found as part of the 'scripted' tests at
 * /sbt-plugin/src/sbt-test/gen-scala-server/00-interop/src/main/scala/org/apache/pekko/grpc/TestServiceImpl.scala
 */
class TestServiceImpl(implicit sys: ActorSystem) extends TestService {
  import TestServiceImpl._

  implicit val mat: Materializer = SystemMaterializer(sys).materializer
  implicit val ec: ExecutionContext = sys.dispatcher

  override def emptyCall(req: Empty) =
    Future.successful(Empty())

  override def unaryCall(req: SimpleRequest): Future[SimpleResponse] =
    req.responseStatus match {
      case None =>
        Future.successful(SimpleResponse(Some(Payload(body = ByteString.copyFrom(new Array[Byte](req.responseSize))))))
      case Some(requestStatus) =>
        val responseStatus = Status.fromCodeValue(requestStatus.code).withDescription(requestStatus.message)
        //  - Either one of the following works
        // Future.failed(new GrpcServiceException(responseStatus))
        throw new GrpcServiceException(responseStatus)
    }

  override def cacheableUnaryCall(in: SimpleRequest): Future[SimpleResponse] = ???

  override def fullDuplexCall(
      in: Source[StreamingOutputCallRequest, NotUsed]): Source[StreamingOutputCallResponse, NotUsed] =
    in.map(req => {
      req.responseStatus.foreach(reqStatus =>
        throw new GrpcServiceException(Status.fromCodeValue(reqStatus.code).withDescription(reqStatus.message)))
      req
    }).mapConcat(_.responseParameters.toList)
      .via(parametersToResponseFlow)

  override def halfDuplexCall(
      in: Source[StreamingOutputCallRequest, NotUsed]): Source[StreamingOutputCallResponse, NotUsed] = ???

  override def streamingInputCall(in: Source[StreamingInputCallRequest, NotUsed]): Future[StreamingInputCallResponse] =
    in.map(_.payload.map(_.body.size).getOrElse(0)).runFold(0)(_ + _).map { sum => StreamingInputCallResponse(sum) }

  override def streamingOutputCall(in: StreamingOutputCallRequest): Source[StreamingOutputCallResponse, NotUsed] =
    Source(in.responseParameters.toList).via(parametersToResponseFlow)

  override def unimplementedCall(in: Empty): Future[Empty] = ???
}
