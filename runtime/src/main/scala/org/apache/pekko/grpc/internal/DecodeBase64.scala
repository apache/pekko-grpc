/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.stream.scaladsl.Flow
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object DecodeBase64 {
  def apply(): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].via(new DecodeBase64)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class DecodeBase64 extends GraphStage[FlowShape[ByteString, ByteString]] {
  private val in = Inlet[ByteString]("DecodeBase64.in")
  private val out = Outlet[ByteString]("DecodeBase64.out")

  override def initialAttributes = Attributes.name("DecodeBase64")

  final override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var buffer: ByteString = ByteString.empty

      override def onPush(): Unit = {
        buffer ++= grab(in)

        val length = buffer.length
        val decodeLength = length - length % 4

        if (decodeLength > 0) {
          val (decodeBytes, remaining) = buffer.splitAt(decodeLength)
          push(out, decodeBytes.decodeBase64)
          buffer = remaining
        } else {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (buffer.nonEmpty) {
          if (isAvailable(out)) {
            push(out, buffer.decodeBase64)
            buffer = ByteString.empty
          }
        } else {
          completeStage()
        }
      }

      override def onPull(): Unit = {
        if (isClosed(in)) {
          if (buffer.nonEmpty) {
            push(out, buffer.decodeBase64)
            buffer = ByteString.empty
          } else {
            completeStage()
          }
        } else pull(in)
      }

      setHandlers(in, out, this)
    }
}
