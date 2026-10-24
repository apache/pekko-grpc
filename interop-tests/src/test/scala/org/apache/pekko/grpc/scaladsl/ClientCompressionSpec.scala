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

import java.util.concurrent.atomic.AtomicReference

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcClientSettings
import pekko.grpc.scaladsl.headers.`Message-Encoding`
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.stream.scaladsl.{ Flow, Source }
import pekko.testkit.TestKit
import pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

object ClientCompressionSpec {
  final case class ReceivedRequest(encoding: Option[String], compressedFlag: Byte)
}

class ClientCompressionSpecNetty extends ClientCompressionSpec("netty")
class ClientCompressionSpecPekkoHttp extends ClientCompressionSpec("pekko-http")

/**
 * Checks what the client sends: the `grpc-encoding` header and the compressed flag of the first
 * request frame, as received by the server.
 */
abstract class ClientCompressionSpec(backend: String)
    extends TestKit(
      ActorSystem(
        s"ClientCompressionSpec-$backend",
        ConfigFactory.parseString("pekko.http.server.enable-http2 = on").withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {
  import ClientCompressionSpec.ReceivedRequest

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(10, org.scalatest.time.Millis))

  implicit val ec: ExecutionContext = system.dispatcher

  private val lastRequest = new AtomicReference[Promise[ReceivedRequest]]()

  private val handler: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler(new GreeterService {
      override def sayHello(in: HelloRequest): Future[HelloReply] =
        Future.successful(HelloReply(s"Hello, ${in.name}"))
      override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] =
        in.runFold(Seq.empty[String])(_ :+ _.name).map(names => HelloReply(s"Hello, ${names.mkString(", ")}"))
      override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???
      override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
    })

  private val recordingHandler: HttpRequest => Future[HttpResponse] = { request =>
    val encoding = `Message-Encoding`.findIn(request.headers)
    val promise = lastRequest.get()
    val recordFirstByte = Flow[ByteString].map { bytes =>
      if (bytes.nonEmpty) promise.trySuccess(ReceivedRequest(encoding, bytes(0)))
      bytes
    }
    handler(request.withEntity(request.entity.transformDataBytes(recordFirstByte)))
  }

  private val binding = Http().newServerAt("127.0.0.1", 0).bind(recordingHandler).futureValue

  private def client(compression: Option[String]): GreeterServiceClient = {
    val settings = GrpcClientSettings
      .connectToServiceAt("127.0.0.1", binding.localAddress.getPort)
      .withTls(false)
      .withBackend(backend)
    GreeterServiceClient(compression.fold(settings)(settings.withCompression))
  }

  private def expectRequest[T](call: => Future[T]): (T, ReceivedRequest) = {
    val promise = Promise[ReceivedRequest]()
    lastRequest.set(promise)
    val result = call.futureValue
    (result, promise.future.futureValue)
  }

  override def afterAll(): Unit = {
    binding.unbind().futureValue
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  s"A $backend client" should {

    "not compress requests by default" in {
      val greeter = client(None)
      try {
        val (reply, received) = expectRequest(greeter.sayHello(HelloRequest("Alice")))
        reply shouldBe HelloReply("Hello, Alice")
        received.encoding.getOrElse("identity") shouldBe "identity"
        received.compressedFlag shouldBe 0
      } finally greeter.close().futureValue
    }

    Seq("gzip", "deflate").foreach { encoding =>
      s"compress unary requests with $encoding" in {
        val greeter = client(Some(encoding))
        try {
          val (reply, received) = expectRequest(greeter.sayHello(HelloRequest("Alice")))
          reply shouldBe HelloReply("Hello, Alice")
          received.encoding shouldBe Some(encoding)
          received.compressedFlag shouldBe 1
        } finally greeter.close().futureValue
      }

      s"compress streamed requests with $encoding" in {
        val greeter = client(Some(encoding))
        try {
          val (reply, received) = expectRequest(
            greeter.itKeepsTalking(Source(List("Alice", "Bob")).map(HelloRequest(_))))
          reply shouldBe HelloReply("Hello, Alice, Bob")
          received.encoding shouldBe Some(encoding)
          received.compressedFlag shouldBe 1
        } finally greeter.close().futureValue
      }
    }
  }
}
