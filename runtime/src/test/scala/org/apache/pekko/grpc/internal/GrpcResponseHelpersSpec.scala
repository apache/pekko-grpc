/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.grpc.internal

import grpc.reflection.v1alpha.reflection.{ ServerReflection, ServerReflectionResponse }
import io.grpc.Status
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.GrpcProtocol
import org.apache.pekko.grpc.scaladsl.{ headers, ScalapbProtobufSerializer }
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.testkit.TestKit
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

final class GrpcResponseHelpersSpec extends TestKit(ActorSystem("GrpcResponseHelpersSpec")) with AnyWordSpecLike {
  "GrpcResponseHelpers" should {

    /**
     * Pre-announcing trailers: See https://www.rfc-editor.org/rfc/rfc7230 #Section 4.4
     * Certain reverse proxies (e.g. tyk) will not behave correctly if trailers are not pre-announced and
     * errors are not reported with trailer-only optimization.
     */
    "pre-announce trailers in the headers" in {
      implicit val serializer: ScalapbProtobufSerializer[ServerReflectionResponse] =
        ServerReflection.Serializers.ServerReflectionResponseSerializer
      implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
      val response =
        GrpcResponseHelpers(
          e = Source.single(ServerReflectionResponse()),
          trail = Source.single(GrpcEntityHelpers.trailer(Status.OK))
        )

      val preAnnouncedTrailers = headers.`Trailer`.findIn(response.headers)

      preAnnouncedTrailers shouldBe Some(Seq(headers.`Status`.name))
    }
  }
}
