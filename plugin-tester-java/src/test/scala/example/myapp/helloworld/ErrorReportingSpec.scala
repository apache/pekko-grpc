/*
 * Copyright (C) 2019-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import java.util.concurrent.CompletionStage

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.internal.GrpcProtocolNative
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.HttpEntity.{ Chunked, LastChunk }
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes }
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import example.myapp.helloworld.grpc.{ GreeterService, GreeterServiceHandlerFactory }
import io.grpc.Status
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class ErrorReportingSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val sys = ActorSystem()
  override implicit val patienceConfig = PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  "A gRPC server" should {
    val mat = implicitly[Materializer]

    val handler = GreeterServiceHandlerFactory.create(new GreeterServiceImpl(mat), sys)
    val binding = {
      import org.apache.pekko.http.javadsl.Http
      import org.apache.pekko.http.javadsl.model.{ HttpRequest, HttpResponse }

      Http(sys)
        .newServerAt("127.0.0.1", 0)
        .bind((req => handler(req)): org.apache.pekko.japi.function.Function[HttpRequest, CompletionStage[
            HttpResponse]])
        .toCompletableFuture
        .get
    }

    "respond with an 'unimplemented' gRPC error status when calling an unknown method" in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        entity = HttpEntity.empty(GrpcProtocolNative.contentType),
        uri = s"http://localhost:${binding.localAddress.getPort}/${GreeterService.name}/UnknownMethod")
      val response = Http().singleRequest(request).futureValue

      response.status should be(StatusCodes.OK)
      val trailers = allHeaders(response)
      trailers should contain(RawHeader("grpc-status", Status.Code.UNIMPLEMENTED.value().toString))
      trailers should contain(RawHeader("grpc-message", "Not implemented: UnknownMethod"))
    }

    "respond with an 'invalid argument' gRPC error status when calling an method without a request body" in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        entity = HttpEntity.empty(GrpcProtocolNative.contentType),
        uri = s"http://localhost:${binding.localAddress.getPort}/${GreeterService.name}/SayHello")
      val response = Http().singleRequest(request).futureValue

      response.status should be(StatusCodes.OK)
      allHeaders(response) should contain(RawHeader("grpc-status", Status.Code.INVALID_ARGUMENT.value().toString))
    }

    def allHeaders(response: HttpResponse) =
      response.entity match {
        case Chunked(_, chunks) =>
          chunks.runWith(Sink.last).futureValue match {
            case LastChunk(_, trailingHeaders) => response.headers ++ trailingHeaders
            case _                             => response.headers
          }
        case _ =>
          response.headers
      }
  }

  override def afterAll(): Unit =
    Await.result(sys.terminate(), 5.seconds)
}
