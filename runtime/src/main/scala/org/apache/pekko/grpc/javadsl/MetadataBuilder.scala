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

package org.apache.pekko.grpc.javadsl

import java.lang.{ Iterable => jIterable }

import org.apache.pekko
import pekko.annotation.ApiMayChange

import scala.collection.JavaConverters._
import pekko.http.javadsl.model.HttpHeader
import pekko.http.scaladsl.model.{ HttpHeader => sHttpHeader }
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.util.ByteString
import pekko.grpc.scaladsl
import pekko.grpc.internal.JavaMetadataImpl

/**
 * This class provides an interface for constructing immutable Metadata instances.
 */
@ApiMayChange
class MetadataBuilder {
  private val delegate = new scaladsl.MetadataBuilder

  /**
   * Adds a string entry. The key must not end in the "-bin" binary suffix.
   * @param key The entry key.
   * @param value The entry value.
   * @return The updated builder.
   */
  def addText(key: String, value: String): MetadataBuilder = {
    delegate.addText(key, value)
    this
  }

  /**
   * Adds a binary entry. The key must end in the "-bin" binary suffix.
   * @param key The entry key.
   * @param value The entry value.
   * @return The updated builder.
   */
  def addBinary(key: String, value: ByteString): MetadataBuilder = {
    delegate.addBinary(key, value)
    this
  }

  /**
   * Builds the immutable metadata instance.
   * @return The instance.
   */
  def build(): Metadata =
    new JavaMetadataImpl(delegate.build())
}

@ApiMayChange
object MetadataBuilder {

  /**
   * @return An empty metadata instance.
   */
  val empty: Metadata = new JavaMetadataImpl(scaladsl.MetadataBuilder.empty)

  /**
   * Constructs a Metadata instance from a collection of HTTP headers.
   * @param headers The headers.
   * @return The metadata instance.
   */
  def fromHeaders(headers: jIterable[HttpHeader]): Metadata =
    new JavaMetadataImpl(scaladsl.MetadataBuilder.fromHeaders(headers.asScala.map(asScala).toList))

  /**
   * Converts from a javadsl.HttpHeader to a scaladsl.HttpHeader.
   * @param header A Java HTTP header.
   * @return An equivalent Scala HTTP header.
   */
  private def asScala(header: HttpHeader): sHttpHeader =
    header match {
      case s: sHttpHeader => s
      case _              => RawHeader(header.name, header.value)
    }
}
