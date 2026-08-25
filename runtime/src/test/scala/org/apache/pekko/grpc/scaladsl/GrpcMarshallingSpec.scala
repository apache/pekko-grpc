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

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.util.Success

import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalapbAny }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import pekko.grpc.internal.{ AbstractGrpcProtocol, GrpcProtocolNative, Identity }
import pekko.http.scaladsl.model.HttpEntity
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.util.{ ByteString => PekkoByteString }

class GrpcMarshallingSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("GrpcMarshallingSpec")
  private implicit val mat: Materializer = SystemMaterializer(system).materializer
  private implicit val serializer: ScalapbProtobufSerializer[ScalapbAny] =
    new ScalapbProtobufSerializer(ScalapbAny)
  private implicit val reader: GrpcProtocolReader = GrpcProtocolNative.newReader(Identity)
  private implicit val writer: GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

  /**
   * An ExecutionContext that refuses to run anything, so that a transform which needs to be
   * scheduled fails loudly instead of silently completing on another thread.
   */
  private object NoDispatch extends ExecutionContext {
    override def execute(runnable: Runnable): Unit =
      throw new AssertionError("transform was scheduled on an ExecutionContext instead of completing directly")
    override def reportFailure(cause: Throwable): Unit = throw cause
  }

  private val message = ScalapbAny("type.googleapis.com/test", ByteString.copyFromUtf8("payload"))

  private def strictEntity(data: PekkoByteString): HttpEntity.Strict =
    HttpEntity.Strict(GrpcProtocolNative.contentType, data)

  private val validEntity =
    strictEntity(AbstractGrpcProtocol.encodeFrameData(serializer.serialize(message), isCompressed = false,
      isTrailer = false))

  // A single zero byte is an invalid protobuf tag, so deserialization of this frame fails.
  private val corruptEntity =
    strictEntity(
      AbstractGrpcProtocol.encodeFrameData(PekkoByteString(0), isCompressed = false, isTrailer = false))

  override protected def afterAll(): Unit = Await.result(system.terminate(), 10.seconds)

  "The scaladsl GrpcMarshalling" should {

    "complete unmarshal of a strict entity directly, without scheduling" in {
      val unmarshalled = GrpcMarshalling.unmarshal[ScalapbAny](validEntity)

      unmarshalled.value should be(Some(Success(message)))
      // A generated handler chains further transforms onto this future; they must not need a dispatch either.
      unmarshalled.flatMap(a => GrpcMarshalling.unmarshal[ScalapbAny](validEntity).map(_ => a)(NoDispatch))(
        NoDispatch).value should be(Some(Success(message)))
    }

    "complete a failed unmarshal of a strict entity directly, without scheduling" in {
      val unmarshalled = GrpcMarshalling.unmarshal[ScalapbAny](corruptEntity)

      unmarshalled.value.map(_.isFailure) should be(Some(true))
      unmarshalled.recover { case _ => message }(NoDispatch).value should be(Some(Success(message)))
    }

    "complete unmarshalStream directly, without scheduling" in {
      val unmarshalled = GrpcMarshalling.unmarshalStream[ScalapbAny](validEntity)

      unmarshalled.value.map(_.isSuccess) should be(Some(true))
      unmarshalled.map(_ => message)(NoDispatch).value should be(Some(Success(message)))
    }

    "complete the exception handler response directly, without scheduling" in {
      val handled =
        GrpcExceptionHandler.from(GrpcExceptionHandler.defaultMapper(system))(system, writer)(
          new RuntimeException("boom"))

      handled.value.map(_.isSuccess) should be(Some(true))
      handled.map(_.status.intValue)(NoDispatch).value should be(Some(Success(200)))
      handled.value.get.get.getHeader("grpc-status").get.value should be("13")
    }

    "complete the not-found and unsupported-media-type responses directly, without scheduling" in {
      ServiceHandler.notFound.map(_.status.intValue)(NoDispatch).value should be(Some(Success(404)))
      ServiceHandler.unsupportedMediaType.map(_.status.intValue)(NoDispatch).value should be(Some(Success(415)))
    }
  }
}
