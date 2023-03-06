/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * 'barrier' that makes sure that, even when downstream is cancelled,
 * the complete upstream is consumed.
 *
 * @tparam T
 */
class CancellationBarrierGraphStage[T] extends GraphStage[FlowShape[T, T]] {
  val in: Inlet[T] = Inlet("CancellationBarrier")
  val out: Outlet[T] = Outlet("CancellationBarrier")

  override val shape: FlowShape[T, T] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = emit(out, grab(in))
        })

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = pull(in)

          override def onDownstreamFinish(cause: Throwable): Unit = {
            if (!hasBeenPulled(in))
              pull(in)

            setHandler(
              in,
              new InHandler {
                override def onPush(): Unit = {
                  grab(in)
                  pull(in)
                }
              })
          }
        })
    }
}
