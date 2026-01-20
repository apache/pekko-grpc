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

import grpc.reflection.v1.reflection.{ ServerReflection, ServerReflectionResponse }
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
