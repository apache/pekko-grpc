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

package org.apache.pekko.grpc.gen

import java.io.PrintWriter
import java.lang.invoke.{ MethodHandle, MethodHandles, MethodType => JMethodType }
import java.nio.charset.StandardCharsets

import scala.collection.concurrent.TrieMap

// specific to gen so that the build tools can implement their own
trait Logger {
  def debug(text: String): Unit
  def info(text: String): Unit
  def warn(text: String): Unit
  def error(text: String): Unit
}

/**
 * Simple standard out logger for use in tests or where there is no logger from the build tool available
 */
object StdoutLogger extends Logger {
  def debug(text: String): Unit = println(s"[debug] $text")
  def info(text: String): Unit = println(s"[info] $text")
  def warn(text: String): Unit = println(s"[warn] $text")
  def error(text: String): Unit = println(s"[error] $text")
}

object SilencedLogger extends Logger {
  def debug(text: String): Unit = ()
  def info(text: String): Unit = ()
  def warn(text: String): Unit = ()
  def error(text: String): Unit = ()
}

class FileLogger(path: String) extends Logger {
  val printer = new PrintWriter(path, StandardCharsets.UTF_8)
  def debug(text: String): Unit = {
    printer.println(s"[debug] $text")
    printer.flush()
  }
  def info(text: String): Unit = {
    printer.println(s"[info] $text")
    printer.flush()
  }
  def warn(text: String): Unit = {
    printer.println(s"[warn] $text")
    printer.flush()
  }
  def error(text: String): Unit = {
    printer.println(s"[error] $text")
    printer.flush()
  }
}

/**
 * Logger that forwards calls to another Logger via MethodHandles.
 *
 *  This enables a code generator that is loaded inside a sandboxed class loader to
 *  use a logger that lives in a different class loader.
 */
class ReflectiveLogger(logger: Object) extends Logger {
  import ReflectiveLogger._

  private val handles = handlesFor(logger.getClass)

  def debug(text: String): Unit = handles.debug.invoke(logger, text)
  def info(text: String): Unit = handles.info.invoke(logger, text)
  def warn(text: String): Unit = handles.warn.invoke(logger, text)
  def error(text: String): Unit = handles.error.invoke(logger, text)
}

private object ReflectiveLogger {
  private final case class LoggerHandles(debug: MethodHandle, info: MethodHandle, warn: MethodHandle,
      error: MethodHandle)

  private val lookup = MethodHandles.publicLookup()
  private val loggerMethodType = JMethodType.methodType(Void.TYPE, classOf[String])
  private val handlesByLoggerClass = TrieMap.empty[Class[?], LoggerHandles]

  private def handlesFor(loggerClass: Class[?]): LoggerHandles =
    handlesByLoggerClass.getOrElseUpdate(
      loggerClass,
      LoggerHandles(
        lookup.findVirtual(loggerClass, "debug", loggerMethodType),
        lookup.findVirtual(loggerClass, "info", loggerMethodType),
        lookup.findVirtual(loggerClass, "warn", loggerMethodType),
        lookup.findVirtual(loggerClass, "error", loggerMethodType)))
}
