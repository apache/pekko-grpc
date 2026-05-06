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
import pekko.http.scaladsl.server._

/**
 * Provides directives to support serving of gRPC services.
 */
object GrpcDirectives {
  import Directives._
  import pekko.grpc.GrpcProtocol
  import pekko.grpc.internal.{ GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText }
  import pekko.http.scaladsl.model.{ ContentTypeRange, MediaType }

  /**
   * Wraps the inner route, passing only standard gRPC (i.e. not grpc-web) requests.
   *
   * @since 2.0.0
   */
  def grpc: Directive0 = grpc(GrpcProtocolNative)

  /**
   * Wraps the inner route, passing only gRPC-Web requests.
   *
   * @since 2.0.0
   */
  def grpcWeb: Directive0 = grpc(GrpcProtocolWeb, GrpcProtocolWebText)

  /**
   * Wraps the inner route, passing requests for all gRPC protocols.
   *
   * Unlike a combined grpc | grpcWeb directive, this will provide a single rejection specifying all supported protocols.
   *
   * @since 2.0.0
   */
  def grpcAll: Directive0 = grpc(GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText)

  /**
   * Wraps the inner route, passing requests only for a specific set of Grpc protocols.
   * @param protocols the protocols to accept and pass to the inner route.
   */
  private def grpc(protocols: GrpcProtocol*): Directive0 = {
    val acceptedMediaTypes = protocols.flatMap(_.mediaTypes).map(_.asInstanceOf[MediaType]).toSet
    extractRequest.flatMap { request =>
      if (acceptedMediaTypes.contains(request.entity.contentType.mediaType))
        pass
      else
        reject(
          UnsupportedRequestContentTypeRejection(
            acceptedMediaTypes.map(mt => ContentTypeRange(mt)),
            Some(request.entity.contentType)
          )
        )
    }
  }

}
