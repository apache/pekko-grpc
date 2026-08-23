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

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.{ KeyFactory, KeyStore, SecureRandom }
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLHandshakeException }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

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
 * The pekko-http backend gets hostname verification from
 * `ConnectionContext.httpsClient(SSLContext)`, which sets the `https` endpoint identification
 * algorithm. Nothing here sets it explicitly, so this spec pins the behaviour: it is the guard
 * against that connection context being swapped for one that leaves verification off.
 *
 * `/certs/localhost-server.crt` carries a single `DNS:localhost` SAN, so reaching the same server
 * as `127.0.0.1` is a hostname mismatch and nothing else - same server, same trust store.
 */
class HostnameVerificationSpec
    extends TestKit(ActorSystem())
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val patience: PatienceConfig =
    PatienceConfig(10.seconds, Span(100, org.scalatest.time.Millis))

  private def resourceBytes(path: String): Array[Byte] = {
    val in = getClass.getResourceAsStream(path)
    try {
      // no InputStream.readAllBytes here, this branch still builds on JDK 8
      val out = new ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var read = in.read(buffer)
      while (read != -1) {
        out.write(buffer, 0, read)
        read = in.read(buffer)
      }
      out.toByteArray
    } finally in.close()
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

  private val binding =
    Http()
      .newServerAt("127.0.0.1", 0)
      .enableHttps(ConnectionContext.httpsServer(serverSslContext()))
      .bind(_ => Future.successful(HttpResponse(StatusCodes.OK)))
      .futureValue

  /** Runs a real TLS handshake through the connection context the client code builds. */
  private def handshake(host: String): Try[HttpResponse] = {
    val settings = GrpcClientSettings
      .connectToServiceAt(host, binding.localAddress.getPort)
      .withTrustManager(SSLContextUtils.trustManagerFromResource("/certs/rootCA.crt"))
    val context = PekkoHttpClientUtils.connectionContext(settings)
    val connection = Http().outgoingConnectionHttps(host, binding.localAddress.getPort, context)
    Try(Source.single(HttpRequest(uri = "/")).via(connection).runWith(Sink.head).futureValue)
  }

  "The pekko-http client connection context" should {

    "accept a certificate matching the requested hostname" in {
      handshake("localhost") match {
        case Success(response) => response.status shouldBe StatusCodes.OK
        case Failure(e)        => fail(s"expected the handshake to succeed, got [$e]")
      }
    }

    "reject a certificate that does not match the requested hostname" in {
      // the regression guard: with endpoint identification unset this handshake succeeds,
      // because the certificate is otherwise valid and trusted
      handshake("127.0.0.1") match {
        case Success(response) => fail(s"expected the handshake to fail, got [$response]")
        case Failure(e) =>
          val causes = Iterator.iterate(e)(_.getCause).takeWhile(_ ne null).toList
          withClue(causes.mkString(", ")) {
            causes.exists(_.isInstanceOf[SSLHandshakeException]) shouldBe true
          }
      }
    }
  }

  override def afterAll(): Unit = {
    binding.terminate(5.seconds).futureValue
    super.afterAll()
  }
}
