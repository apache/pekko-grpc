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

import java.util.concurrent.ConcurrentLinkedQueue

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcClientSettings
import pekko.grpc.internal.Deflate
import pekko.grpc.scaladsl.headers.{ `Message-Accept-Encoding`, `Message-Encoding` }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class DeflateResponseClientSpecNetty extends DeflateResponseClientSpec("netty")
class DeflateResponseClientSpecPekkoHttp extends DeflateResponseClientSpec("pekko-http")

/**
 * A real client of each backend receiving deflate-compressed responses from a generated handler.
 *
 * Both clients list gzip before deflate in `grpc-accept-encoding`, so the server would answer with
 * gzip. To get deflate responses through the real client code, the server side keeps only `deflate`
 * from what the client advertised, after checking that the client did advertise it.
 */
abstract class DeflateResponseClientSpec(backend: String)
    extends TestKit(
      ActorSystem(
        s"DeflateResponseClientSpec-$backend",
        ConfigFactory.parseString("pekko.http.server.enable-http2 = on").withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(10, org.scalatest.time.Millis))

  implicit val ec: ExecutionContext = system.dispatcher

  private val advertised = new ConcurrentLinkedQueue[Seq[String]]()
  private val responseEncodings = new ConcurrentLinkedQueue[Option[String]]()

  private val greeter: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler(new GreeterService {
      override def sayHello(in: HelloRequest): Future[HelloReply] =
        Future.successful(HelloReply(s"Hello, ${in.name}"))
      override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???
      override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
        Source(1 to 3).map(i => HelloReply(s"Hello, ${in.name} $i"))
      override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
    })

  private val handler: HttpRequest => Future[HttpResponse] = { request =>
    val clientEncodings = `Message-Accept-Encoding`.findIn(request.headers).toSeq
    advertised.add(clientEncodings)
    val deflateOnly =
      request.withHeaders(
        request.headers.filterNot(_.is(`Message-Accept-Encoding`.lowercaseName)) :+
        `Message-Accept-Encoding`(clientEncodings.filter(_ == Deflate.name).mkString(",")))
    greeter(deflateOnly).map { response =>
      responseEncodings.add(`Message-Encoding`.findIn(response.headers))
      response
    }
  }

  private val binding = Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue

  private val client =
    GreeterServiceClient(
      GrpcClientSettings
        .connectToServiceAt("127.0.0.1", binding.localAddress.getPort)
        .withTls(false)
        .withBackend(backend))

  override def afterAll(): Unit = {
    client.close().futureValue
    binding.unbind().futureValue
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  private def checkDeflateWasUsed(): Unit = {
    advertised.asScala.toSeq should not be empty
    all(advertised.asScala.toSeq) should contain(Deflate.name)
    responseEncodings.asScala.toSeq should not be empty
    all(responseEncodings.asScala.toSeq) shouldBe Some(Deflate.name)
  }

  s"The $backend client" should {

    "decode a deflate-compressed unary response" in {
      advertised.clear()
      responseEncodings.clear()

      client.sayHello(HelloRequest("Alice")).futureValue shouldBe HelloReply("Hello, Alice")

      checkDeflateWasUsed()
    }

    "decode a deflate-compressed server stream" in {
      advertised.clear()
      responseEncodings.clear()

      client.itKeepsReplying(HelloRequest("Bob")).runWith(Sink.seq).futureValue shouldBe
      (1 to 3).map(i => HelloReply(s"Hello, Bob $i"))

      checkDeflateWasUsed()
    }
  }
}
