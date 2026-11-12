/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc.scaladsl

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

import io.grpc.Status
import org.apache.pekko
import pekko.{ Done, NotUsed }
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol.GrpcProtocolWriter
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcResponseHelpers, Identity }
import pekko.grpc.scaladsl.headers.`Timeout`
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import com.google.protobuf.wrappers.StringValue
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * A client stops waiting once its own deadline expires, so the server bounds the call by the
 * client's `grpc-timeout` rather than keeping a reply open that nobody is going to read.
 */
class ServerDeadlineSpec
    extends TestKit(ActorSystem("ServerDeadlineSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val patience: PatienceConfig =
    PatienceConfig(10.seconds, Span(50, org.scalatest.time.Millis))
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val writer: GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
  implicit val serializer: ScalapbProtobufSerializer[StringValue] =
    new ScalapbProtobufSerializer(StringValue.messageCompanion)

  private def statusOf(response: HttpResponse): Option[String] =
    response.headers.collectFirst { case h if h.is("grpc-status") => h.value }

  "withServerDeadline" should {

    "leave a request with no grpc-timeout unbounded" in {
      val request = HttpRequest()
      val slow = pekko.pattern.after(300.millis)(Future.successful(HttpResponse()))(system)

      val response = GrpcMarshalling.withServerDeadline(request, slow).futureValue

      response.status shouldBe StatusCodes.OK
      statusOf(response) shouldBe None
    }

    "complete a response that beats the deadline unchanged" in {
      val request = HttpRequest().withHeaders(`Timeout`(5.seconds))

      val response = GrpcMarshalling.withServerDeadline(request, Future.successful(HttpResponse())).futureValue

      response.status shouldBe StatusCodes.OK
      statusOf(response) shouldBe None
    }

    "fail a response that misses the deadline with DEADLINE_EXCEEDED" in {
      val request = HttpRequest().withHeaders(`Timeout`(100.millis))
      // never completes, standing in for a handler that outlives the client
      val stuck = Promise[HttpResponse]().future

      val response = GrpcMarshalling.withServerDeadline(request, stuck).futureValue

      statusOf(response) shouldBe Some(Status.Code.DEADLINE_EXCEEDED.value.toString)
    }

    "not wait for the handler once the deadline has passed" in {
      val request = HttpRequest().withHeaders(`Timeout`(100.millis))
      val slow = pekko.pattern.after(5.seconds)(Future.successful(HttpResponse()))(system)

      val started = System.nanoTime()
      val response = GrpcMarshalling.withServerDeadline(request, slow).futureValue
      val elapsed = (System.nanoTime() - started).nanos

      statusOf(response) shouldBe Some(Status.Code.DEADLINE_EXCEEDED.value.toString)
      withClue(s"returned after $elapsed: ") {
        elapsed should be < 5.seconds
      }
    }

    "end a streamed body that outlives the deadline with DEADLINE_EXCEEDED and cancel its source" in {
      val request = HttpRequest().withHeaders(`Timeout`(300.millis))
      val cancelled = Promise[Done]()
      // one frame, then nothing until cancelled, standing in for a server stream that outlives the client
      val body = Source
        .single(StringValue("first"))
        .concat(Source.maybe[StringValue])
        .watchTermination { (_, done) =>
          done.onComplete(_ => cancelled.trySuccess(Done))
          NotUsed
        }

      val response =
        GrpcMarshalling.withServerDeadline(request, Future.successful(GrpcResponseHelpers(body))).futureValue
      val chunks = response.entity.asInstanceOf[HttpEntity.Chunked].chunks.runWith(Sink.seq).futureValue

      chunks should have size 2
      chunks.last match {
        case HttpEntity.LastChunk(_, trailer) =>
          trailer.collectFirst { case h if h.is("grpc-status") => h.value } shouldBe
          Some(Status.Code.DEADLINE_EXCEEDED.value.toString)
        case other => fail(s"Expected trailers but got $other")
      }
      cancelled.future.futureValue shouldBe Done
    }

    "leave a streamed body that completes in time unchanged" in {
      val request = HttpRequest().withHeaders(`Timeout`(5.seconds))
      val body = Source(List(StringValue("first"), StringValue("second")))

      val response =
        GrpcMarshalling.withServerDeadline(request, Future.successful(GrpcResponseHelpers(body))).futureValue
      val chunks = response.entity.asInstanceOf[HttpEntity.Chunked].chunks.runWith(Sink.seq).futureValue

      chunks should have size 3
      chunks.last match {
        case HttpEntity.LastChunk(_, trailer) =>
          trailer.collectFirst { case h if h.is("grpc-status") => h.value } shouldBe
          Some(Status.Code.OK.value.toString)
        case other => fail(s"Expected trailers but got $other")
      }
    }

    "ignore a malformed grpc-timeout rather than refusing to serve" in {
      val request = HttpRequest().withHeaders(RawHeader("grpc-timeout", "not a timeout"))

      val response = GrpcMarshalling.withServerDeadline(request, Future.successful(HttpResponse())).futureValue

      response.status shouldBe StatusCodes.OK
      statusOf(response) shouldBe None
    }
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
