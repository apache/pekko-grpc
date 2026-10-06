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

import org.apache.pekko.annotation.ApiMayChange

/**
 * Writes an element directly into a pre-allocated frame buffer, so that the serialized form does not have to be
 * copied into the frame after serialization.
 *
 * @since 2.0.0
 */
@ApiMayChange
trait DeferredDataWriter[T] {

  /**
   * Compute the size of the serialized form of the given element.
   */
  def serializedSize(t: T): Int

  /**
   * Serialize the given element into the given frame, starting at the given offset.
   * @param t the element to serialize.
   * @param frame a preallocated frame buffer, which will be at least of size offset + serializedSize(t)
   * @param offset the offset to place the serialized data of the element at.
   */
  def serializeTo(t: T, frame: Array[Byte], offset: Int): Unit
}
