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

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ AbstractGrpcProtocol, Deflate, GrpcProtocolNative }
import pekko.grpc.scaladsl.headers.{ `Message-Accept-Encoding`, `Message-Encoding` }
import pekko.http.scaladsl.model.{ HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import pekko.util.ByteString
import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration._

class DeflateCompressionSpec
    extends TestKit(ActorSystem("DeflateCompressionSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(10, org.scalatest.time.Millis))

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  private val handler: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler(new GreeterService {
      override def sayHello(in: HelloRequest): Future[HelloReply] =
        Future.successful(HelloReply(s"Hello, ${in.name}"))
      override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???
      override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???
      override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
    })

  private def sayHelloRequest(acceptEncoding: String): HttpRequest = {
    val frame = AbstractGrpcProtocol.encodeFrameData(
      Deflate.compress(ByteString.fromArrayUnsafe(HelloRequest("Alice").toByteArray)),
      Deflate.isCompressed,
      isTrailer = false)
    HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("http://localhost/helloworld.GreeterService/SayHello"),
      headers = List(`Message-Encoding`(Deflate.name), `Message-Accept-Encoding`(acceptEncoding)),
      entity = HttpEntity.Strict(GrpcProtocolNative.contentType, frame))
  }

  private def reply(response: HttpResponse, encoding: String): HelloReply = {
    val reader = GrpcProtocolNative.newReader(pekko.grpc.internal.Codecs.detect(Some(encoding)).get)
    val bytes = response.entity.dataBytes.via(reader.dataFrameDecoder).runWith(Sink.head).futureValue
    HelloReply.parseFrom(bytes.toArrayUnsafe())
  }

  "A gRPC server" should {

    "decompress a deflate request and compress the response with deflate when the client prefers it" in {
      val response = handler(sayHelloRequest("deflate, gzip")).futureValue

      response.status shouldBe StatusCodes.OK
      `Message-Encoding`.findIn(response.headers) shouldBe Some("deflate")
      reply(response, "deflate") shouldBe HelloReply("Hello, Alice")
    }

    "compress the response with the first encoding the client accepts" in {
      val response = handler(sayHelloRequest("gzip, deflate")).futureValue

      response.status shouldBe StatusCodes.OK
      `Message-Encoding`.findIn(response.headers) shouldBe Some("gzip")
      reply(response, "gzip") shouldBe HelloReply("Hello, Alice")
    }
  }
}
