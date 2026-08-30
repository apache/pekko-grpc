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

import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.cors.scaladsl.model.HttpOriginMatcher
import pekko.http.cors.scaladsl.settings.CorsSettings
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers._
import pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * `defaultCorsSettings` allows any origin and sets `Access-Control-Allow-Credentials: true`,
 * so a browser will send cookies on a cross-origin call and let the calling page read the
 * response. `allow-credentials-from-any-origin = false` drops the credentials permission.
 */
class WebHandlerCorsSpec
    extends TestKit(ActorSystem("WebHandlerCorsSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val patience: PatienceConfig =
    PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  private val foreignOrigin = HttpOrigin("https://evil.example")

  private def systemWith(allowCredentialsFromAnyOrigin: Boolean): ActorSystem =
    ActorSystem(
      "configured",
      ConfigFactory
        .parseString(
          s"pekko.grpc.server.grpc-web.allow-credentials-from-any-origin = $allowCredentialsFromAnyOrigin")
        .withFallback(ConfigFactory.load()))

  private def respond(sys: ActorSystem, settings: CorsSettings, request: HttpRequest): HttpResponse = {
    val handler = WebHandler.grpcWebHandler({
      case _: HttpRequest => Future.successful(HttpResponse())
    })(sys, settings)
    handler(request).futureValue
  }

  private def actualRequest =
    HttpRequest(HttpMethods.POST, "/foo.Service/Method").withHeaders(Origin(foreignOrigin))

  private def preflightRequest =
    HttpRequest(HttpMethods.OPTIONS, "/foo.Service/Method")
      .withHeaders(Origin(foreignOrigin), `Access-Control-Request-Method`(HttpMethods.POST))

  private def allowCredentials(response: HttpResponse): Boolean =
    response.headers.exists(h => h.is("access-control-allow-credentials") && h.value == "true")

  private def allowOrigin(response: HttpResponse): Option[String] =
    response.headers.collectFirst { case h if h.is("access-control-allow-origin") => h.value }

  "The default grpc-web CORS settings" should {

    "be reported as allowing credentials from any origin" in {
      WebHandler.allowsCredentialsFromAnyOrigin(WebHandler.defaultCorsSettings) shouldBe true
    }

    "not be reported once the origins are restricted" in {
      val restricted =
        WebHandler.defaultCorsSettings.withAllowedOrigins(HttpOriginMatcher(HttpOrigin("https://good.example")))

      WebHandler.allowsCredentialsFromAnyOrigin(restricted) shouldBe false
    }

    "not be reported when credentials are not allowed" in {
      WebHandler.allowsCredentialsFromAnyOrigin(
        WebHandler.defaultCorsSettings.withAllowCredentials(false)) shouldBe false
    }
  }

  "With allow-credentials-from-any-origin = true (the default)" should {
    lazy val sys = systemWith(allowCredentialsFromAnyOrigin = true)

    "echo a foreign origin and allow credentials, as before" in {
      val response = respond(sys, WebHandler.defaultCorsSettings, actualRequest)

      allowOrigin(response) shouldBe Some(foreignOrigin.value)
      allowCredentials(response) shouldBe true
    }

    "allow credentials on the preflight too" in {
      val response = respond(sys, WebHandler.defaultCorsSettings, preflightRequest)

      allowCredentials(response) shouldBe true
    }
  }

  "With allow-credentials-from-any-origin = false" should {
    lazy val sys = systemWith(allowCredentialsFromAnyOrigin = false)

    "not allow credentials to a foreign origin" in {
      val response = respond(sys, WebHandler.defaultCorsSettings, actualRequest)

      allowCredentials(response) shouldBe false
      // the wildcard is what makes a browser withhold cookies rather than send them
      allowOrigin(response) shouldBe Some("*")
    }

    "not allow credentials on the preflight either" in {
      val response = respond(sys, WebHandler.defaultCorsSettings, preflightRequest)

      allowCredentials(response) shouldBe false
    }

    "leave settings that restrict the origins alone" in {
      // credentials are safe once the caller has said which origins may use them, so the
      // setting must not take them away
      val restricted =
        WebHandler.defaultCorsSettings.withAllowedOrigins(HttpOriginMatcher(foreignOrigin))
      val response = respond(sys, restricted, actualRequest)

      allowCredentials(response) shouldBe true
      allowOrigin(response) shouldBe Some(foreignOrigin.value)
    }
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
