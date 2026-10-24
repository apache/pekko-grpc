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
import pekko.grpc.GrpcClientSettings
import pekko.http.scaladsl.Http
import pekko.pattern
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

class PerCallDeadlineSpecNetty extends PerCallDeadlineSpec("netty")
class PerCallDeadlineSpecPekkoHttp extends PerCallDeadlineSpec("pekko-http")

abstract class PerCallDeadlineSpec(backend: String)
    extends TestKit(
      ActorSystem(
        s"PerCallDeadlineSpec-$backend",
        ConfigFactory.parseString("pekko.http.server.enable-http2 = on").withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(10, org.scalatest.time.Millis))

  implicit val ec: ExecutionContext = system.dispatcher

  private val slowReplyDelay = 500.millis

  private val service = new GreeterService {
    override def sayHello(in: HelloRequest): Future[HelloReply] =
      in.name match {
        case "never" => Promise[HelloReply]().future
        case "slow"  => pattern.after(slowReplyDelay)(Future.successful(HelloReply("Hello slow")))
        case name    => Future.successful(HelloReply(s"Hello $name"))
      }
    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???
    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
      Source.single(HelloReply("first")).concat(Source.maybe[HelloReply]).mapMaterializedValue(_ => NotUsed)
    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
  }

  private val binding = Http().newServerAt("127.0.0.1", 0).bind(GreeterServiceHandler(service)).futureValue

  private def clientSettings: GrpcClientSettings =
    GrpcClientSettings
      .connectToServiceAt("127.0.0.1", binding.localAddress.getPort)
      .withTls(false)
      .withBackend(backend)

  private val client = GreeterServiceClient(clientSettings)

  private val clientWithShortDeadline = GreeterServiceClient(clientSettings.withDeadline(100.millis))

  override def afterAll(): Unit = {
    client.close().futureValue
    clientWithShortDeadline.close().futureValue
    binding.unbind().futureValue
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  private def statusCode(failure: Throwable): Code =
    failure match {
      case e: StatusRuntimeException => e.getStatus.getCode
      case other                     => fail(s"Expected a StatusRuntimeException but got $other")
    }

  s"A per-call deadline - $backend" should {

    "fail a unary call that does not complete in time" in {
      // #per-call-deadline
      val reply: Future[HelloReply] =
        client.sayHello().setDeadline(200.millis).invoke(HelloRequest("never"))
      // #per-call-deadline
      statusCode(reply.failed.futureValue) shouldBe Code.DEADLINE_EXCEEDED
    }

    "fail a unary call with metadata that does not complete in time" in {
      val failure =
        client.sayHello().setDeadline(200.millis).invokeWithMetadata(HelloRequest("never")).failed.futureValue
      statusCode(failure) shouldBe Code.DEADLINE_EXCEEDED
    }

    "fail a server streaming call that does not complete in time" in {
      val failure =
        client
          .itKeepsReplying()
          .setDeadline(200.millis)
          .invoke(HelloRequest("never"))
          .runWith(Sink.ignore)
          .failed
          .futureValue
      statusCode(failure) shouldBe Code.DEADLINE_EXCEEDED
    }

    "override the deadline of the client settings" in {
      clientWithShortDeadline
        .sayHello()
        .setDeadline(5.seconds)
        .invoke(HelloRequest("slow"))
        .futureValue shouldBe HelloReply("Hello slow")
    }

    "start when the call is invoked rather than when it is set" in {
      val builder = client.sayHello().setDeadline(slowReplyDelay * 2)
      // wait until a deadline counted from setDeadline would already have expired
      pattern.after(slowReplyDelay * 3)(Future.unit).futureValue
      builder.invoke(HelloRequest("Alice")).futureValue shouldBe HelloReply("Hello Alice")
    }
  }
}
