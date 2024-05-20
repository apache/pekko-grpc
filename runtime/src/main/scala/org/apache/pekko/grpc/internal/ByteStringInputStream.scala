/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.io.{ ByteArrayInputStream, InputStream, SequenceInputStream }
import java.lang.invoke.{ MethodHandles, MethodType }

import scala.util.Try

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import pekko.util.ByteString.ByteString1C
import pekko.util.ByteString.ByteStrings
import pekko.util.ccompat.JavaConverters._

/** INTERNAL API */
@InternalApi
private[internal] object ByteStringInputStream {

  private val byteStringInputStreamMethodTypeOpt = Try {
    val lookup = MethodHandles.publicLookup()
    val inputStreamMethodType = MethodType.methodType(classOf[InputStream])
    lookup.findVirtual(classOf[ByteString], "asInputStream", inputStreamMethodType)
  }.toOption

  def apply(bs: ByteString): InputStream = bs match {
    case cs: ByteString1C =>
      getInputStreamUnsafe(cs)
    case _ =>
      if (byteStringInputStreamMethodTypeOpt.isDefined) {
        byteStringInputStreamMethodTypeOpt.get.invoke(bs).asInstanceOf[InputStream]
      } else {
        legacyConvert(bs)
      }
  }

  private def legacyConvert(bs: ByteString): InputStream = bs match {
    case bss: ByteStrings =>
      new SequenceInputStream(bss.bytestrings.iterator.map(legacyConvert).asJavaEnumeration)
    case _ =>
      getInputStreamUnsafe(bs)
  }

  private def getInputStreamUnsafe(bs: ByteString): InputStream =
    new ByteArrayInputStream(bs.toArrayUnsafe())

}
