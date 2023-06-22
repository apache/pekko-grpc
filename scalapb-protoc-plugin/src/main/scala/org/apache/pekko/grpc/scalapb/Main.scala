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

package org.apache.pekko.grpc.scalapb

import java.io.ByteArrayOutputStream

import scalapb.ScalaPbCodeGenerator

object Main extends App {
  val inBytes: Array[Byte] = {
    val baos = new ByteArrayOutputStream(math.max(64, System.in.available()))
    val buffer = Array.ofDim[Byte](32 * 1024)

    var bytesRead = System.in.read(buffer)
    while (bytesRead >= 0) {
      baos.write(buffer, 0, bytesRead)
      bytesRead = System.in.read(buffer)
    }
    baos.toByteArray
  }

  val outBytes = ScalaPbCodeGenerator.run(inBytes)

  System.out.write(outBytes)
  System.out.flush()
}
