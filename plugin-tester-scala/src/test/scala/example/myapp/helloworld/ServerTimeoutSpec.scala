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

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc._
import io.grpc.Status
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.scaladsl.headers.`Timeout`
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.stream.scaladsl.Source
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.junit.JUnitRunner

/**
 * Drives a generated handler directly, so this covers the codegen wiring rather than only the
 * `GrpcMarshalling` helper it calls.
 */
@RunWith(classOf[JUnitRunner])
class ServerTimeoutSpec extends Matchers with AnyWordSpecLike with BeforeAndAfterAll with ScalaFutures {

  implicit val patience: PatienceConfig = PatienceConfig(10.seconds, Span(50, org.scalatest.time.Millis))

  implicit val system: ActorSystem = ActorSystem(
    "ServerTimeoutSpec",
    ConfigFactory
      .parseString("pekko.http.server.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication()))

  implicit val ec: ExecutionContext = system.dispatcher

  /** A service whose reply never arrives, standing in for one that outlives the client. */
  private class StuckGreeter(val entered: AtomicBoolean = new AtomicBoolean(false)) extends GreeterService {
    override def sayHello(in: HelloRequest): Future[HelloReply] = {
      entered.set(true)
      Promise[HelloReply]().future
    }
    override def itKeepsTalking(in: Source[HelloRequest, pekko.NotUsed]): Future[HelloReply] =
      Promise[HelloReply]().future
    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, pekko.NotUsed] = Source.empty
    override def streamHellos(in: Source[HelloRequest, pekko.NotUsed]): Source[HelloReply, pekko.NotUsed] =
      Source.empty
  }

  private implicit val writer: pekko.grpc.GrpcProtocol.GrpcProtocolWriter =
    pekko.grpc.internal.GrpcProtocolNative.newWriter(pekko.grpc.internal.Identity)

  private def request(timeout: Option[FiniteDuration]): HttpRequest =
    pekko.grpc.internal.GrpcRequestHelpers(
      pekko.http.scaladsl.model.Uri(s"http://localhost/${GreeterService.name}/SayHello"),
      timeout.map(t => `Timeout`(t)).toList,
      Source.single(HelloRequest("Alice")))(
      GreeterService.Serializers.HelloRequestSerializer,
      writer,
      system)

  private def statusOf(response: HttpResponse): Option[String] =
    (response.headers ++ response.attribute(pekko.http.scaladsl.model.AttributeKeys.trailer).toSeq.flatMap(
      _.headers.map { case (k, v) => pekko.http.scaladsl.model.headers.RawHeader(k, v) }))
      .collectFirst { case h if h.is("grpc-status") => h.value }

  "A generated handler" should {

    "answer DEADLINE_EXCEEDED once the client's grpc-timeout expires" in {
      val service = new StuckGreeter
      val handler = GreeterServiceHandler(service)

      val response = handler(request(Some(200.millis))).futureValue

      withClue("the handler should have been entered, so this is the deadline and not a routing miss: ") {
        service.entered.get() shouldBe true
      }
      statusOf(response) shouldBe Some(Status.Code.DEADLINE_EXCEEDED.value.toString)
    }

    "not answer early when the request carries no grpc-timeout" in {
      val handler = GreeterServiceHandler(new StuckGreeter)

      val response = handler(request(None))

      // nothing bounds it, so it is still pending well after the deadline above would have hit
      pekko.pattern.after(500.millis)(Future.successful(()))(system).futureValue
      response.isCompleted shouldBe false
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
