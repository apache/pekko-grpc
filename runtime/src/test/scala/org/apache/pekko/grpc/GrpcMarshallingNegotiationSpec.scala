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

package org.apache.pekko.grpc

import java.util.concurrent.{ CompletableFuture, TimeUnit }

import scala.concurrent.Future

import io.grpc.{ Status, StatusException }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol.GrpcProtocolReader
import pekko.grpc.internal.{ AbstractGrpcProtocol, GrpcProtocolNative }
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest }
import pekko.testkit.TestKit
import pekko.util.ByteString

class GrpcMarshallingNegotiationSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers {

  private val Limit = 64 * 1024

  private val request =
    HttpRequest(entity = HttpEntity.Strict(GrpcProtocolNative.contentType, ByteString.empty))

  /** A frame header: 1 byte flags + 4 bytes big-endian length. */
  private def frameHeader(length: Int): ByteString =
    ByteString(0.toByte, (length >>> 24).toByte, (length >>> 16).toByte, (length >>> 8).toByte, length.toByte)

  private def scalaReader(negotiated: Option[Future[GrpcProtocolReader]]): GrpcProtocolReader =
    negotiated.flatMap(_.value).get.get

  private def javaReader(
      negotiated: java.util.Optional[java.util.concurrent.CompletionStage[GrpcProtocolReader]]): GrpcProtocolReader =
    negotiated.get.toCompletableFuture.get(3, TimeUnit.SECONDS)

  /** The readers the two DSLs negotiate for `settings`. */
  private def readers(settings: GrpcServerSettings): Seq[GrpcProtocolReader] =
    Seq(
      scalaReader(scaladsl.GrpcMarshalling.negotiated(request, settings, (reader, _) => Future.successful(reader))),
      javaReader(
        javadsl.GrpcMarshalling.negotiated(request, settings,
          (reader, _) => CompletableFuture.completedFuture(reader))))

  /** The readers the two DSLs negotiate when no settings are passed. */
  private def defaultReaders(): Seq[GrpcProtocolReader] =
    Seq(
      scalaReader(scaladsl.GrpcMarshalling.negotiated(request, (reader, _) => Future.successful(reader))),
      javaReader(
        javadsl.GrpcMarshalling.negotiated(request, (reader, _) => CompletableFuture.completedFuture(reader))))

  private def frame(size: Int): ByteString = {
    val data = ByteString(new Array[Byte](size))
    frameHeader(size) ++ data
  }

  "GrpcMarshalling.negotiated" should {

    "build a reader bounded by the settings' maximum inbound message size" in {
      val settings = GrpcServerSettings(system).withMaxInboundMessageSize(Limit)

      readers(settings).foreach { reader =>
        val status = intercept[StatusException](reader.decodeSingleFrame(frame(Limit + 1))).getStatus

        status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
        status.getDescription should include(s"Frame length ${Limit + 1}")
      }
    }

    "build a reader bounded by the built-in default when no settings are passed" in {
      defaultReaders().foreach { reader =>
        // the frame the bounded settings above reject is accepted here, so the limit really
        // comes from the settings rather than from a value baked into the negotiation
        reader.decodeSingleFrame(frame(Limit + 1)).size shouldBe (Limit + 1)

        val tooLarge = AbstractGrpcProtocol.DefaultMaxInboundMessageSize + 1
        intercept[StatusException](
          reader.decodeSingleFrame(frameHeader(tooLarge))).getStatus.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
      }
    }
  }
}
