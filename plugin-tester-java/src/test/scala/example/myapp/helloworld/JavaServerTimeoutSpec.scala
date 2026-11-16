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

package example.myapp.helloworld

import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.jdk.FutureConverters._

import example.myapp.helloworld.grpc._
import io.grpc.Status

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcServerSettings
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcRequestHelpers, Identity }
import pekko.grpc.javadsl.GrpcExceptionHandler
import pekko.grpc.scaladsl.headers.`Timeout`
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.model.{ AttributeKeys, HttpEntity, HttpRequest, HttpResponse, Uri }
import pekko.stream.javadsl.Source
import pekko.stream.scaladsl.Sink
import pekko.stream.{ Materializer, SystemMaterializer }

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

/**
 * Drives a generated Java handler directly, so this covers the Java codegen wiring of the deadline
 * opt-out rather than only the `GrpcMarshalling` helper it calls.
 */
class JavaServerTimeoutSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem(
    "JavaServerTimeoutSpec",
    ConfigFactory
      .parseString("pekko.http.server.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication()))

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(50, org.scalatest.time.Millis))

  private val mat: Materializer = SystemMaterializer(system).materializer

  /**
   * A service whose reply is held back until the test completes `reply`, so the test decides whether
   * it arrives after the client's grpc-timeout rather than racing a timer.
   */
  private class HeldGreeter extends GreeterService {
    val entered = new AtomicBoolean(false)
    val reply = new CompletableFuture[HelloReply]()
    override def sayHello(in: HelloRequest): CompletionStage[HelloReply] = {
      entered.set(true)
      reply
    }
    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): CompletionStage[HelloReply] =
      new CompletableFuture[HelloReply]()
    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = Source.empty()
    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = Source.empty()
  }

  private implicit val writer: pekko.grpc.GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

  private def request(timeout: FiniteDuration): HttpRequest =
    GrpcRequestHelpers(
      Uri(s"http://localhost/${GreeterService.name}/SayHello"),
      List(`Timeout`(timeout)),
      pekko.stream.scaladsl.Source.single(HelloRequest.newBuilder().setName("Alice").build()))(
      GreeterService.Serializers.HelloRequestSerializer,
      writer,
      system)

  private def handle(service: HeldGreeter, settings: GrpcServerSettings): Future[HttpResponse] = {
    val handler = GreeterServiceHandlerFactory.partial(
      service,
      GreeterService.name,
      mat,
      GrpcExceptionHandler.defaultMapper,
      settings,
      system)
    handler(request(200.millis)).asScala.map(_.asInstanceOf[HttpResponse])(system.dispatcher)
  }

  /** Reads the grpc-status from the headers or, for a streamed reply, the trailer once the body is drained. */
  private def statusOf(response: HttpResponse): Option[String] = {
    val trailers = response.entity match {
      case HttpEntity.Chunked(_, chunks) =>
        chunks.runWith(Sink.last)(mat).futureValue match {
          case HttpEntity.LastChunk(_, trailingHeaders) => trailingHeaders
          case _                                        => Nil
        }
      case _ =>
        response.attribute(AttributeKeys.trailer).toSeq.flatMap(_.headers.map { case (k, v) => RawHeader(k, v) })
    }
    (response.headers ++ trailers).collectFirst { case h if h.is("grpc-status") => h.value }
  }

  "A generated Java handler" should {

    "answer DEADLINE_EXCEEDED once the client's grpc-timeout expires" in {
      val service = new HeldGreeter
      val response = handle(service, GrpcServerSettings(system)).futureValue

      withClue("the handler should have been entered, so this is the deadline and not a routing miss: ") {
        service.entered.get() shouldBe true
      }
      statusOf(response) shouldBe Some(Status.Code.DEADLINE_EXCEEDED.value.toString)
    }

    "complete the reply after the grpc-timeout when enforcing the deadline is turned off" in {
      val service = new HeldGreeter
      val response = handle(service, GrpcServerSettings(system).withEnforceDeadline(false))

      // the reply is still held back, so the call stays pending well past its 200 ms deadline
      pekko.pattern.after(400.millis)(Future.successful(()))(system).futureValue
      withClue("the handler should have been entered, so this is not a routing miss: ") {
        service.entered.get() shouldBe true
      }
      response.isCompleted shouldBe false

      service.reply.complete(HelloReply.newBuilder().setMessage("Hello, Alice").build())
      statusOf(response.futureValue) shouldBe Some(Status.Code.OK.value.toString)
    }
  }

  override def afterAll(): Unit =
    Await.result(system.terminate(), 5.seconds)
}
