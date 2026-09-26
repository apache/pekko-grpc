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

package org.apache.pekko.grpc.internal

import java.nio.charset.StandardCharsets.UTF_8
import java.security.{ KeyFactory, KeyStore, SecureRandom }
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLEngine, SSLHandshakeException }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import com.typesafe.config.ConfigFactory
import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.{ GrpcClientSettings, SSLContextUtils }
import pekko.http.scaladsl.{ ConnectionContext, Http }
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Covers `minimum-tls-version` end to end: the protocol filtering itself, the `SSLContext`
 * wrapper built from it, that the floor reaches the pekko-http connection context on both the
 * verifying and the opt-out path, and that a floor above what the server offers actually
 * fails the handshake.
 */
class MinimumTlsVersionSpec
    extends TestKit(ActorSystem())
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val patience: PatienceConfig =
    PatienceConfig(10.seconds, Span(100, org.scalatest.time.Millis))

  private def resourceBytes(path: String): Array[Byte] = {
    val in = getClass.getResourceAsStream(path)
    try in.readAllBytes()
    finally in.close()
  }

  private def serverSslContext(): SSLContext = {
    val cert = CertificateFactory
      .getInstance("X.509")
      .generateCertificate(getClass.getResourceAsStream("/certs/localhost-server.crt"))
    val pem = new String(resourceBytes("/certs/localhost-server.key"), UTF_8)
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replaceAll("\\s", "")
    val key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder.decode(pem)))

    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null, null)
    keyStore.setKeyEntry("server", key, Array.emptyCharArray, Array(cert))
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, Array.emptyCharArray)

    val ctx = SSLContext.getInstance("TLS")
    ctx.init(kmf.getKeyManagers, null, new SecureRandom)
    ctx
  }

  /** A server that will only ever negotiate TLSv1.2. */
  private val binding = {
    val ctx = serverSslContext()
    val engineCreator = () => {
      val engine: SSLEngine = ctx.createSSLEngine()
      engine.setUseClientMode(false)
      engine.setEnabledProtocols(Array("TLSv1.2"))
      engine
    }
    Http()
      .newServerAt("127.0.0.1", 0)
      .enableHttps(ConnectionContext.httpsServer(engineCreator))
      .bind(_ => Future.successful(HttpResponse(StatusCodes.OK)))
      .futureValue
  }

  private def settingsFor(minimumTlsVersion: Option[String]): GrpcClientSettings = {
    val base = GrpcClientSettings
      .connectToServiceAt("localhost", binding.localAddress.getPort)
      .withTrustManager(SSLContextUtils.trustManagerFromResource("/certs/rootCA.crt"))
    minimumTlsVersion.fold(base)(base.withMinimumTlsVersion)
  }

  /**
   * The TLS versions an engine will negotiate.
   *
   * Netty additionally enables `SSLv2Hello`, which is a ClientHello wire format rather than a
   * protocol that can be negotiated, so it says nothing about the version floor.
   */
  private def tlsVersionsOf(engine: SSLEngine): Seq[String] =
    engine.getEnabledProtocols.toSeq.filterNot(_ == "SSLv2Hello")

  /** Runs a real TLS handshake through the connection context our client code builds. */
  private def handshake(minimumTlsVersion: Option[String]): Try[HttpResponse] = {
    val context = PekkoHttpClientUtils.connectionContext(settingsFor(minimumTlsVersion), system.log)
    val connection = Http().outgoingConnectionHttps("localhost", binding.localAddress.getPort, context)
    Try(Source.single(HttpRequest(uri = "/")).via(connection).runWith(Sink.head).futureValue)
  }

  /** Settings built from the `"*"` client defaults, with `overrides` layered on top. */
  private def settingsFromConfig(overrides: String): GrpcClientSettings = {
    val clientConfig = ConfigFactory
      .parseString(s"""host = "example.com"
                      |port = 443
                      |$overrides""".stripMargin)
      .withFallback(ConfigFactory.load().getConfig("""pekko.grpc.client."*""""))
    GrpcClientSettings.fromConfig(clientConfig)(system)
  }

  "SSLContextUtils.enabledProtocols" should {

    "keep every known version at or above the floor" in {
      SSLContextUtils.enabledProtocols(SSLContext.getDefault, "TLSv1.2").toSet shouldBe
      Set("TLSv1.2", "TLSv1.3")
    }

    "drop versions below the floor" in {
      SSLContextUtils.enabledProtocols(SSLContext.getDefault, "TLSv1.3").toSet shouldBe Set("TLSv1.3")
    }

    "never widen to a version below TLSv1.2, even though it reads the supported set" in {
      // It reads getSupportedSSLParameters rather than getDefaultSSLParameters, so that a
      // version the JDK supports but does not enable by default stays reachable. That is not
      // observable on a JDK whose default set is already TLSv1.2 + TLSv1.3, but the widening
      // it allows must never reach below the floor: the supported set also carries TLSv1.1,
      // TLSv1, SSLv3 and SSLv2Hello, and the known-version list is what keeps those out.
      val supported = SSLContext.getDefault.getSupportedSSLParameters.getProtocols.toSet
      withClue(s"supported: ${supported.mkString(", ")}") {
        supported should contain("TLSv1.1")
      }
      SSLContextUtils.enabledProtocols(SSLContext.getDefault, "TLSv1.2").toSet shouldBe
      Set("TLSv1.2", "TLSv1.3")
    }

    "reject a version it does not know" in {
      val e = intercept[IllegalArgumentException] {
        SSLContextUtils.enabledProtocols(SSLContext.getDefault, "TLSv1.1")
      }
      e.getMessage should include("Unknown TLS version 'TLSv1.1'")
    }

    "filter a caller-supplied protocol list" in {
      SSLContextUtils
        .enabledProtocols(Array("SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"), "TLSv1.3")
        .toSeq shouldBe Seq("TLSv1.3")
    }

    "fail when nothing survives the floor" in {
      intercept[IllegalStateException] {
        SSLContextUtils.enabledProtocols(Array("TLSv1", "TLSv1.1"), "TLSv1.2")
      }
    }
  }

  "SSLContextUtils.withMinimumTlsVersion" should {

    "constrain engines created without a peer" in {
      val ctx = SSLContextUtils.withMinimumTlsVersion(SSLContext.getDefault, "TLSv1.3")

      ctx.createSSLEngine().getEnabledProtocols.toSeq shouldBe Seq("TLSv1.3")
    }

    "constrain engines created for a peer, which is the one gRPC uses" in {
      val ctx = SSLContextUtils.withMinimumTlsVersion(SSLContext.getDefault, "TLSv1.3")

      ctx.createSSLEngine("example.com", 443).getEnabledProtocols.toSeq shouldBe Seq("TLSv1.3")
    }

    "survive the engine reconfiguration pekko-http performs on top of it" in {
      // ConnectionContext.httpsClient(SSLContext) sets client mode and the endpoint
      // identification algorithm through setSSLParameters, which could have reset protocols
      val ctx = SSLContextUtils.withMinimumTlsVersion(SSLContext.getDefault, "TLSv1.3")
      val engine = ctx.createSSLEngine("example.com", 443)
      engine.setUseClientMode(true)
      val params = engine.getSSLParameters
      params.setEndpointIdentificationAlgorithm("https")
      engine.setSSLParameters(params)

      engine.getEnabledProtocols.toSeq shouldBe Seq("TLSv1.3")
      engine.getSSLParameters.getEndpointIdentificationAlgorithm shouldBe "https"
    }
  }

  "The pekko-http client SSLContext" should {

    "be unconstrained when no floor is configured" in {
      val ctx = PekkoHttpClientUtils.sslContextFor(settingsFor(None))

      ctx.createSSLEngine("example.com", 443).getEnabledProtocols should contain("TLSv1.2")
    }

    "carry the floor when one is configured" in {
      val ctx = PekkoHttpClientUtils.sslContextFor(settingsFor(Some("TLSv1.3")))

      ctx.createSSLEngine("example.com", 443).getEnabledProtocols.toSeq shouldBe Seq("TLSv1.3")
    }

    "carry the floor on the verify-hostname = false path too" in {
      // the floor lives in sslContextFor, so it has to reach the engine the opt-out builds
      val settings = settingsFor(Some("TLSv1.3")).withVerifyHostname(false)
      val engine =
        PekkoHttpClientUtils.insecureSslEngineCreator(PekkoHttpClientUtils.sslContextFor(settings))("h", 1)

      engine.getEnabledProtocols.toSeq shouldBe Seq("TLSv1.3")
    }
  }

  "A client with a minimum TLS version" should {

    "complete the handshake when the server offers a version at the floor" in {
      handshake(Some("TLSv1.2")) match {
        case Success(response) => response.status shouldBe StatusCodes.OK
        case Failure(e)        => fail(s"expected the handshake to succeed, got [$e]")
      }
    }

    "fail the handshake when the server offers nothing at or above the floor" in {
      // the server above only enables TLSv1.2, so requiring TLSv1.3 leaves no common version.
      // This is the guard: without the floor applied the handshake below succeeds on TLSv1.2.
      handshake(Some("TLSv1.3")) match {
        case Success(response) => fail(s"expected the handshake to fail, got [$response]")
        case Failure(e)        =>
          val causes = Iterator.iterate(e: Throwable)(_.getCause).takeWhile(_ ne null).toList
          withClue(causes.mkString(", ")) {
            causes.exists(_.isInstanceOf[SSLHandshakeException]) shouldBe true
          }
      }
    }

    "connect over TLSv1.2 when no floor is configured" in {
      handshake(None) match {
        case Success(response) => response.status shouldBe StatusCodes.OK
        case Failure(e)        => fail(s"expected the handshake to succeed, got [$e]")
      }
    }
  }

  "The netty client SslContext" should {

    "not be built when nothing beyond the defaults is configured" in {
      // grpc-java supplies its own client context in that case
      NettyClientUtils.nettySslContext(settingsFromConfig("")) shouldBe None
    }

    "be built from the floor alone, with otherwise stock trust settings" in {
      // the regression guard: this used to fall into a `case (None, None) => builder` branch
      // that returned before minimum-tls-version was ever consulted, so the setting was
      // silently ignored on the default backend in its most common configuration
      val settings = settingsFromConfig("""minimum-tls-version = "TLSv1.3"""")
      val sslContext = NettyClientUtils.nettySslContext(settings)

      withClue("no SslContext was built, so the floor cannot have been applied: ") {
        sslContext shouldBe defined
      }
      val engine = sslContext.get.newEngine(ByteBufAllocator.DEFAULT, "example.com", 443)
      tlsVersionsOf(engine) shouldBe Seq("TLSv1.3")
    }

    "carry the floor alongside a trust manager" in {
      val settings = settingsFor(Some("TLSv1.3"))
      val engine =
        NettyClientUtils.nettySslContext(settings).get.newEngine(ByteBufAllocator.DEFAULT, "example.com", 443)

      tlsVersionsOf(engine) shouldBe Seq("TLSv1.3")
    }

    "leave protocols to the JDK when no floor is configured" in {
      val engine =
        NettyClientUtils.nettySslContext(settingsFor(None)).get.newEngine(ByteBufAllocator.DEFAULT, "example.com", 443)

      tlsVersionsOf(engine) should contain("TLSv1.2")
    }
  }

  "GrpcClientSettings" should {

    "leave the floor unset by default" in {
      settingsFromConfig("").minimumTlsVersion shouldBe None
    }

    "read the floor from configuration" in {
      settingsFromConfig("""minimum-tls-version = "TLSv1.3"""").minimumTlsVersion shouldBe Some("TLSv1.3")
    }

    "expose the floor with otherwise stock TLS settings" in {
      // the netty backend used to skip the setting entirely in exactly this shape, because
      // no trust manager and no ssl-provider meant it never looked at minimumTlsVersion
      val settings = settingsFromConfig("""minimum-tls-version = "TLSv1.3"""")

      settings.minimumTlsVersion shouldBe Some("TLSv1.3")
      settings.trustManager shouldBe None
      settings.sslProvider shouldBe None
      settings.sslContext shouldBe None
    }

    "keep the floor through withMinimumTlsVersion" in {
      GrpcClientSettings
        .connectToServiceAt("example.com", 443)
        .withMinimumTlsVersion("TLSv1.3")
        .minimumTlsVersion shouldBe Some("TLSv1.3")
    }
  }

  override def afterAll(): Unit = {
    binding.terminate(5.seconds).futureValue
    super.afterAll()
  }
}
