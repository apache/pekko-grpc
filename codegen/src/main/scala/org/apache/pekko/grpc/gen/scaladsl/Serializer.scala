/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.gen.scaladsl

import com.google.protobuf.Descriptors.{ Descriptor, MethodDescriptor }
import scalapb.compiler.DescriptorImplicits

case class Serializer(name: String, scalaType: String, init: String)

object Serializer {
  def apply(method: MethodDescriptor, messageType: Descriptor)(implicit ops: DescriptorImplicits): Serializer = {
    val name = if (method.getFile.getPackage == messageType.getFile.getPackage) {
      messageType.getName + "Serializer"
    } else {
      messageType.getFile.getPackage.replace('.', '_') + "_" + messageType.getName + "Serializer"
    }
    Serializer(name, Method.messageType(messageType),
      s"new ScalapbProtobufSerializer(${Method.messageType(messageType)}.messageCompanion)")
  }
}
