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

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcResponseHelpers, Identity }
import pekko.grpc.scaladsl.MetadataStatus
import pekko.testkit.TestKit
import io.grpc.{ Status, StatusRuntimeException }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcServiceExceptionSpec
    extends TestKit(ActorSystem("GrpcServiceExceptionSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "GrpcServiceException" should {

    "be created from a StatusRuntimeException without trailers" in {
      val exception = GrpcServiceException(new StatusRuntimeException(Status.UNAVAILABLE))

      exception.status.getCode shouldBe Status.Code.UNAVAILABLE
      exception.metadata.asList shouldBe empty
      // used to fail with a NullPointerException when writing the trailers
      GrpcResponseHelpers.status(Trailers(exception.status, exception.metadata))
    }

    "expose the rich error status when created with details" in {
      val exception = GrpcServiceException(com.google.rpc.Code.INVALID_ARGUMENT, "bad request", Seq.empty)

      exception.metadata shouldBe a[MetadataStatus]
      val metadataStatus = exception.metadata.asInstanceOf[MetadataStatus]
      metadataStatus.code shouldBe com.google.rpc.Code.INVALID_ARGUMENT.getNumber
      metadataStatus.message shouldBe "bad request"
    }
  }

  "The javadsl default exception mapper" should {

    "map a StatusRuntimeException without trailers" in {
      val trailers = javadsl.GrpcExceptionHandler.defaultMapper(system)(new StatusRuntimeException(Status.UNAVAILABLE))

      trailers.status.getCode shouldBe Status.Code.UNAVAILABLE
      trailers.metadata.asList shouldBe empty
      // used to fail with a NullPointerException when writing the trailers
      GrpcResponseHelpers.status(trailers)
    }
  }
}
