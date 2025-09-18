/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.scaladsl.Sink
import pekko.stream.{ javadsl, AbruptStageTerminationException, Attributes, Inlet, SinkShape }
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler }

import scala.concurrent.{ Future, Promise }

/**
 * Stage that reads a single parameter from the stream
 *
 *  This sink does not, like 'Sink.head', complete the stage as soon as the first element has arrived.
 *  This is important to avoid triggering the stream teardown after consuming the first message.
 *
 * INTERNAL API
 */
@InternalApi private[internal] final class SingleParameterStage[T]
    extends GraphStageWithMaterializedValue[SinkShape[T], Future[T]] {

  val in: Inlet[T] = Inlet("singleParameterSink.in")

  override def shape: SinkShape[T] = SinkShape.of(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[T]) = {
    val p: Promise[T] = Promise()
    (
      new GraphStageLogic(shape) with InHandler {
        override def preStart(): Unit = pull(in)

        def onPush(): Unit = {
          p.success(grab(in))
          // We expect only a completion
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          if (!p.isCompleted) {
            p.failure(new MissingParameterException())
          }
          completeStage()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          p.tryFailure(ex)
          failStage(ex)
        }

        override def postStop(): Unit = {
          if (!p.isCompleted) p.failure(new AbruptStageTerminationException(this))
        }

        setHandler(in, this)
      },
      p.future)
  }

}
object SingleParameterSink {
  def apply[T](): Sink[T, Future[T]] =
    Sink.fromGraph(new SingleParameterStage[T]).withAttributes(Attributes.name("singleParameterSink"))

  def create[T](): javadsl.Sink[T, CompletionStage[T]] = {
    import scala.jdk.FutureConverters._
    new javadsl.Sink(SingleParameterSink().mapMaterializedValue(_.asJava))
  }
}
