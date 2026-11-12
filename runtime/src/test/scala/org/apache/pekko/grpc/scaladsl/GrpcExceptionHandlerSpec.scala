/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.{ GrpcProtocol, GrpcServiceException, Trailers }
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcResponseHelpers, Identity }
import pekko.grpc.scaladsl.GrpcExceptionHandler.defaultMapper
import pekko.http.scaladsl.model.HttpEntity._
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.util.FastFuture
import pekko.testkit.TestKit
import io.grpc.{ Status, StatusRuntimeException }
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ ExecutionContext, ExecutionException, Future, Promise }

class GrpcExceptionHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("Test")
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))
  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

  val expected: Function[Throwable, Status] = {
    case e: ExecutionException =>
      if (e.getCause == null) Status.INTERNAL
      else expected(e.getCause)
    case grpcException: GrpcServiceException => grpcException.status
    case e: StatusRuntimeException           => e.getStatus
    // the message is not returned to the caller: it is whatever the service implementation
    // happened to throw with, so it is logged instead
    case _: NotImplementedError           => Status.UNIMPLEMENTED
    case _: UnsupportedOperationException => Status.UNIMPLEMENTED
    case _                                => Status.INTERNAL
  }

  val otherTypes: Seq[Throwable] = Seq(
    new GrpcServiceException(status = Status.DEADLINE_EXCEEDED),
    new NotImplementedError(),
    new UnsupportedOperationException(),
    new NullPointerException(),
    new RuntimeException(),
    new StatusRuntimeException(io.grpc.Status.DEADLINE_EXCEEDED))

  val executionExceptions: Seq[Throwable] =
    otherTypes.map(new ExecutionException(_)) :+ new ExecutionException("doh", null)

  "defaultMapper" should {
    (otherTypes ++ executionExceptions).foreach { e =>
      val exp = expected(e)
      s"Map $e to $exp" in {
        val status = defaultMapper(system)(e).status
        // according to io.grpc.Status.equals javadoc, equals is not well-defined for Status instances, so
        // comparing code and description explicitly
        status.getCode shouldBe exp.getCode
        status.getDescription shouldBe exp.getDescription
      }
    }
  }

  "defaultMapper" should {

    "not return the message of a NotImplementedError to the caller" in {
      val status = defaultMapper(system)(new NotImplementedError("internal detail")).status

      status.getCode shouldBe Status.UNIMPLEMENTED.getCode
      status.getDescription shouldBe null
    }

    "not return the message of an UnsupportedOperationException to the caller" in {
      val status = defaultMapper(system)(new UnsupportedOperationException("internal detail")).status

      status.getCode shouldBe Status.UNIMPLEMENTED.getCode
      status.getDescription shouldBe null
    }

    "still return a description that was set deliberately" in {
      // a GrpcServiceException is how a service says what to tell the caller, and is what the
      // generated handlers raise for an unknown method
      val deliberate = new GrpcServiceException(Status.UNIMPLEMENTED.withDescription("Not implemented: SayHello"))

      val status = defaultMapper(system)(deliberate).status

      status.getCode shouldBe Status.UNIMPLEMENTED.getCode
      status.getDescription shouldBe "Not implemented: SayHello"
    }
  }

  "default(defaultMapper)" should {
    (otherTypes ++ executionExceptions).foreach { e =>
      s"Correctly map $e" in {
        val exp = GrpcResponseHelpers.status(defaultMapper(system)(e))
        val expChunks = getChunks(exp)
        val act = GrpcExceptionHandler.from(defaultMapper(system))(system, writer)(e).futureValue
        val actChunks = getChunks(act)
        // Following is because aren't equal
        act.status shouldBe exp.status
        actChunks.toString shouldEqual expChunks.toString
      }
    }
  }

  "recover" should {
    implicit val ec: ExecutionContext = system.dispatcher

    val ok = HttpResponse()

    /** An exception handler that counts how often it is constructed. */
    class CountingHandler(mapper: PartialFunction[Throwable, Trailers]) extends (ActorSystem => PartialFunction[
              Throwable,
              Trailers]) {
      val constructed = new AtomicInteger()
      override def apply(sys: ActorSystem): PartialFunction[Throwable, Trailers] = {
        constructed.incrementAndGet()
        mapper
      }
    }

    val aborted: PartialFunction[Throwable, Trailers] = {
      case _: IllegalStateException => Trailers(Status.ABORTED)
    }

    def grpcStatus(response: HttpResponse): Option[String] =
      response.headers.collectFirst { case h if h.is("grpc-status") => h.value }

    "return an already successful response as is, without constructing the exception handler" in {
      val handler = new CountingHandler(aborted)
      val response = Future.successful(ok)

      (GrpcExceptionHandler.recover(response, handler) should be).theSameInstanceAs(response)
      handler.constructed.get shouldBe 0
    }

    "map an already failed response with the exception handler" in {
      val handler = new CountingHandler(aborted)

      val recovered = GrpcExceptionHandler.recover(Future.failed(new IllegalStateException("boom")), handler)

      recovered.isCompleted shouldBe true
      grpcStatus(recovered.futureValue) shouldBe Some(Status.Code.ABORTED.value.toString)
      handler.constructed.get shouldBe 1
    }

    "fall back on the default mapper for an already failed response the handler does not match" in {
      val recovered =
        GrpcExceptionHandler.recover(
          Future.failed(new GrpcServiceException(Status.NOT_FOUND)),
          new CountingHandler(aborted))

      grpcStatus(recovered.futureValue) shouldBe Some(Status.Code.NOT_FOUND.value.toString)
    }

    "fail the response if the exception handler throws for an already failed response" in {
      val throwing: ActorSystem => PartialFunction[Throwable, Trailers] = _ => throw new RuntimeException("bad handler")

      val recovered = GrpcExceptionHandler.recover(Future.failed(new IllegalStateException("boom")), throwing)

      recovered.failed.futureValue.getMessage shouldBe "bad handler"
    }

    "not map an already failed response with a fatal error" in {
      val handler = new CountingHandler(aborted)
      // FastFuture keeps the fatal error as is, where a Promise would box it in an ExecutionException
      val response = FastFuture.failed[HttpResponse](new InterruptedException("fatal"))

      (GrpcExceptionHandler.recover(response, handler) should be).theSameInstanceAs(response)
      handler.constructed.get shouldBe 0
    }

    "map a pending response that later fails" in {
      val handler = new CountingHandler(aborted)
      val pending = Promise[HttpResponse]()

      val recovered = GrpcExceptionHandler.recover(pending.future, handler)
      recovered.isCompleted shouldBe false
      handler.constructed.get shouldBe 0

      pending.failure(new IllegalStateException("boom"))
      grpcStatus(recovered.futureValue) shouldBe Some(Status.Code.ABORTED.value.toString)
      handler.constructed.get shouldBe 1
    }

    "pass through a pending response that later succeeds, without constructing the exception handler" in {
      val handler = new CountingHandler(aborted)
      val pending = Promise[HttpResponse]()

      val recovered = GrpcExceptionHandler.recover(pending.future, handler)
      pending.success(ok)

      (recovered.futureValue should be).theSameInstanceAs(ok)
      handler.constructed.get shouldBe 0
    }
  }

  def getChunks(resp: HttpResponse): Seq[ChunkStreamPart] =
    (resp.entity match {
      case Chunked(_, chunks) =>
        chunks.runFold(Seq.empty[ChunkStreamPart]) { case (seq, chunk) => seq :+ chunk }
      case _ => Future.successful(Seq.empty[ChunkStreamPart])
    }).futureValue

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
