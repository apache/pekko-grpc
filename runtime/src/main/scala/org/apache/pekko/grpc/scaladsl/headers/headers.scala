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

package org.apache.pekko.grpc.scaladsl.headers

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.scaladsl.model.HttpHeader
import pekko.http.scaladsl.model.headers.{ ModeledCustomHeader, ModeledCustomHeaderCompanion }
import pekko.http.javadsl.{ model => jm }

import scala.collection.compat.immutable.ArraySeq
import scala.collection.immutable
import scala.annotation.nowarn
import scala.util.Try
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

/**
 * Simple CSV parser for HTTP header values. Not meant to be a full CSV parser,
 * just enough to parse the headers we care about.
 */
private object SimpleCSVParser {
  def parse(value: String): Array[String] = value.split(',').map(_.trim)
}

@ApiMayChange
final class `Message-Accept-Encoding`(override val value: String)
    extends ModeledCustomHeader[`Message-Accept-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  @nowarn("msg=the inferred type changes")
  override val companion = `Message-Accept-Encoding`

  lazy val values: Array[String] = SimpleCSVParser.parse(value)
}

@ApiMayChange
object `Message-Accept-Encoding` extends ModeledCustomHeaderCompanion[`Message-Accept-Encoding`] {
  override val name = "grpc-accept-encoding"
  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Message-Accept-Encoding`] =
    Try(new `Message-Accept-Encoding`(value))

  def findIn(headers: Iterable[jm.HttpHeader]): Array[String] =
    headers.collectFirst {
      case h if h.is(name) => SimpleCSVParser.parse(h.value())
    }.getOrElse(Array.empty)

  /** Java API */
  def findIn(headers: java.lang.Iterable[jm.HttpHeader]): Array[String] = {
    import scala.jdk.CollectionConverters._
    findIn(headers.asScala)
  }
}

@ApiMayChange
final class `Message-Encoding`(encoding: String) extends ModeledCustomHeader[`Message-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  @nowarn("msg=the inferred type changes")
  override val companion = `Message-Encoding`
  override def value: String = encoding
}

@ApiMayChange
object `Message-Encoding` extends ModeledCustomHeaderCompanion[`Message-Encoding`] {
  override val name = "grpc-encoding"
  override val lowercaseName: String = super.lowercaseName

  override def parse(encoding: String): Try[`Message-Encoding`] = Try(new `Message-Encoding`(encoding))

  def findIn(headers: Iterable[jm.HttpHeader]): Option[String] =
    headers.collectFirst { case h if h.is(name) => h.value() }

  /** Java API */
  def findIn(headers: java.lang.Iterable[jm.HttpHeader]): Option[String] = {
    import scala.jdk.CollectionConverters._
    findIn(headers.asScala)
  }
}

final class `Status`(code: Int) extends ModeledCustomHeader[`Status`] {
  override def renderInRequests = false
  override def renderInResponses = true
  @nowarn("msg=the inferred type changes")
  override val companion = `Status`

  override def value() = code.toString
}

object `Status` extends ModeledCustomHeaderCompanion[`Status`] {
  override val name = "grpc-status"
  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Status`] = Try(new `Status`(Integer.parseInt(value)))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[Int] =
    headers.collectFirst { case h if h.is(name) => Integer.parseInt(h.value()) }
}

/**
 * The `grpc-timeout` request header: how long the client is prepared to wait for a reply.
 *
 * The wire format is a positive integer of at most 8 digits followed by a unit character, as
 * per https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md - `H`ours, `M`inutes,
 * `S`econds, `m`illiseconds, `u`microseconds, `n`anoseconds.
 */
@ApiMayChange
final class `Timeout`(val timeout: FiniteDuration) extends ModeledCustomHeader[`Timeout`] {
  override def renderInRequests = true
  override def renderInResponses = false
  @nowarn("msg=the inferred type changes")
  override val companion = `Timeout`

  // Nanoseconds keep the value exact for any duration the client can express, but the field is
  // capped at 8 digits, so fall back to coarser units as the value grows.
  override def value(): String = {
    val nanos = timeout.toNanos
    if (nanos < `Timeout`.MaxValue) s"${nanos}n"
    else if (timeout.toMicros < `Timeout`.MaxValue) s"${timeout.toMicros}u"
    else if (timeout.toMillis < `Timeout`.MaxValue) s"${timeout.toMillis}m"
    else if (timeout.toSeconds < `Timeout`.MaxValue) s"${timeout.toSeconds}S"
    else if (timeout.toMinutes < `Timeout`.MaxValue) s"${timeout.toMinutes}M"
    else s"${timeout.toHours}H"
  }
}

@ApiMayChange
object `Timeout` extends ModeledCustomHeaderCompanion[`Timeout`] {
  override val name = "grpc-timeout"
  override val lowercaseName: String = super.lowercaseName

  /** The wire format allows at most 8 digits. */
  private[grpc] final val MaxValue = 100000000L

  def apply(timeout: FiniteDuration): `Timeout` = new `Timeout`(timeout)

  override def parse(value: String): Try[`Timeout`] = Try {
    require(value.length >= 2, s"Invalid grpc-timeout [$value]")
    val digits = value.substring(0, value.length - 1)
    require(digits.length <= 8, s"Invalid grpc-timeout [$value], at most 8 digits allowed")
    val amount = digits.toLong
    require(amount >= 0, s"Invalid grpc-timeout [$value], must not be negative")
    val unit = value.charAt(value.length - 1) match {
      case 'H'   => TimeUnit.HOURS
      case 'M'   => TimeUnit.MINUTES
      case 'S'   => TimeUnit.SECONDS
      case 'm'   => TimeUnit.MILLISECONDS
      case 'u'   => TimeUnit.MICROSECONDS
      case 'n'   => TimeUnit.NANOSECONDS
      case other => throw new IllegalArgumentException(s"Invalid grpc-timeout unit [$other] in [$value]")
    }
    new `Timeout`(FiniteDuration(amount, unit))
  }

  /**
   * The timeout requested in these headers, if any.
   *
   * A malformed value yields `None` rather than failing the request: the timeout is a hint from
   * the peer, and refusing to serve a request over it would be worse than ignoring it.
   */
  def findIn(headers: immutable.Seq[HttpHeader]): Option[FiniteDuration] =
    headers.collectFirst { case h if h.is(name) => parse(h.value()).toOption.map(_.timeout) }.flatten
}

// grpc-message must be percent encoded: https://github.com/grpc/grpc/issues/4672
final class `Status-Message`(val unencodedValue: String) extends ModeledCustomHeader[`Status-Message`] {
  override def renderInRequests = false
  override def renderInResponses = true
  @nowarn("msg=the inferred type changes")
  override val companion = `Status-Message`
  override def value() = PercentEncoding.Encoder.encode(unencodedValue)
}

object `Status-Message` extends ModeledCustomHeaderCompanion[`Status-Message`] {
  override val name = "grpc-message"
  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Status-Message`] = Try(
    new `Status-Message`(PercentEncoding.Decoder.decode(value)))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[String] =
    headers.collectFirst { case h if h.is(name) => h.value() }
}

private[grpc] final class `Trailer` private (values: immutable.Seq[String]) extends ModeledCustomHeader[`Trailer`] {

  override def companion: ModeledCustomHeaderCompanion[`Trailer`] = `Trailer`

  override def value(): String = values.mkString(", ")

  override def renderInRequests(): Boolean = true

  override def renderInResponses(): Boolean = true
}

private[grpc] object `Trailer` extends ModeledCustomHeaderCompanion[`Trailer`] {
  def apply(values: immutable.Seq[String]): `Trailer` = new `Trailer`(values.map(_.trim))

  override val name = "trailer"

  override val lowercaseName: String = super.lowercaseName

  override def parse(value: String): Try[`Trailer`] =
    Try(`Trailer`(ArraySeq.unsafeWrapArray(SimpleCSVParser.parse(value))))

  def findIn(headers: immutable.Seq[HttpHeader]): Option[immutable.Seq[String]] =
    headers.collectFirst {
      case header if header.is(name) => ArraySeq.unsafeWrapArray(SimpleCSVParser.parse(header.value()))
    }
}
