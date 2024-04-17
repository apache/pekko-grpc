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

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts
import pekko.grpc.{ GrpcResponseMetadata, GrpcServiceException }
import pekko.stream
import pekko.stream.{ Attributes => _, _ }
import pekko.stream.stage._
import pekko.util.FutureConverters._
import io.grpc._

import scala.concurrent.{ Future, Promise }
import scala.util.Success

@InternalApi
private object PekkoNettyGrpcClientGraphStage {
  sealed trait ControlMessage
  case object ReadyForSending extends ControlMessage
  case class Closed(status: Status) extends ControlMessage
}

/**
 * Wrapper graph stage representing a gRPC Netty call as a Flow[I, O, ForLater].
 *
 * Interaction is done through two parts, the ClientCall object and a listener registered
 * to the same which gets callbacks from the client:
 *
 *                                 ------------------
 *  Flow in       ------ I ----->  |                | ------- O ------->  Flow out
 *                                 |  Netty client  |
 *  upstream pull <-- onReady ---  |      call      | <-- request(1) ---  downstream pull
 *                                 ------------------
 *
 * @param streamingResponse Do we expect a stream of responses or does more than 1 response mean a faulty server?
 */
@InternalApi
private final class PekkoNettyGrpcClientGraphStage[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: Channel,
    options: CallOptions,
    streamingResponse: Boolean,
    headers: MetadataImpl)
    extends GraphStageWithMaterializedValue[FlowShape[I, O], Future[GrpcResponseMetadata]] {
  val fqMethodName = descriptor.getFullMethodName
  val in = Inlet[I](fqMethodName + ".in")
  val out = Outlet[O](fqMethodName + ".out")

  override val shape: FlowShape[I, O] = FlowShape.of(in, out)

  def createLogicAndMaterializedValue(
      inheritedAttributes: stream.Attributes): (GraphStageLogic, Future[GrpcResponseMetadata]) = {
    import PekkoNettyGrpcClientGraphStage._
    val matVal = Promise[GrpcResponseMetadata]()
    val logic = new GraphStageLogic(shape) with InHandler with OutHandler {
      // this is here just to fail single response requests getting more responses
      // duplicating behavior in io.grpc.stub.ClientCalls
      var sawFirstElement = false
      var requested = 0

      // any here to avoid wrapping every incoming element
      val callback = getAsyncCallback[Any] {
        case msg: ControlMessage =>
          msg match {
            case ReadyForSending => if (!isClosed(in) && !hasBeenPulled(in)) tryPull(in)
            case Closed(status)  => onCallClosed(status)
          }
        case element: O @unchecked =>
          if (!streamingResponse) {
            if (sawFirstElement) {
              throw new IllegalStateException("Got more than one messages back from to a non-streaming call")
            } else sawFirstElement = true
          }
          emit(out, element)
          requested -= 1
      }

      var call: ClientCall[I, O] = null

      val listener = new ClientCall.Listener[O] {
        override def onReady(): Unit =
          callback.invoke(ReadyForSending)

        override def onHeaders(responseHeaders: Metadata): Unit = {
          matVal.success(new GrpcResponseMetadata {
            private lazy val sMetadata = MetadataImpl.scalaMetadataFromGoogleGrpcMetadata(responseHeaders)
            private lazy val jMetadata = MetadataImpl.javaMetadataFromGoogleGrpcMetadata(responseHeaders)
            def headers = sMetadata
            def getHeaders() = jMetadata

            private lazy val sTrailers = Future.successful(sMetadata)
            private lazy val jTrailers = Future.successful(jMetadata).asJava
            def trailers = sTrailers
            def getTrailers() = jTrailers
          })
        }

        override def onMessage(message: O): Unit =
          callback.invoke(message)

        override def onClose(status: Status, trailers: Metadata): Unit = {
          if (!matVal.isCompleted) {
            onHeaders(trailers)
          }
          callback.invoke(Closed(status))
        }
      }

      override def preStart(): Unit = {

        call = channel.newCall(descriptor, options)
        call.start(listener, headers.toGoogleGrpcMetadata())

        // always pull early - pull 2 for non-streaming response "to trigger failure early"
        // duplicating behavior in io.grpc.stub.ClientCalls - not sure why this is a good idea
        val initialRequest = if (streamingResponse) 1 else 2
        call.request(initialRequest)
        requested = initialRequest

        // give us a chance to deal with the call cancellation even when
        // the up and downstreams are done
        setKeepGoing(true)

        // the netty client doesn't always start with an OnReady, but all calls has at least one
        // request so pull early to get things going
        pull(in)
      }

      override def onPush(): Unit = {
        call.sendMessage(grab(in))
        if (call.isReady && !hasBeenPulled(in)) {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        call.halfClose()
        if (isClosed(out)) {
          call.cancel("Upstream completed and downstream has cancelled", null)
          call = null
          completeStage()
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        call.cancel("Failure from upstream", ex)
        call = null
        failStage(ex)
      }

      override def onPull(): Unit =
        if (requested == 0) {
          call.request(1)
          requested += 1
        }

      override def onDownstreamFinish(cause: Throwable): Unit =
        if (isClosed(out)) {
          call.cancel("Downstream cancelled", cause)
          call = null
          completeStage()
        }

      def onCallClosed(status: Status): Unit = {
        if (status.isOk()) {
          completeStage()
        } else {
          matVal.future.onComplete {
            case Success(metadata) => failStage(new GrpcServiceException(status, metadata.headers))
            case _                 => failStage(new GrpcServiceException(status))
          }(ExecutionContexts.parasitic)
        }
        call = null
      }

      override def postStop(): Unit = {
        if (call != null) {
          call.cancel("Abrupt stream termination", null)
          call = null
        }
        if (!matVal.isCompleted) {
          matVal.tryFailure(new AbruptStageTerminationException(this))
        }
      }

      setHandlers(in, out, this)
    }

    (logic, matVal.future)
  }
}
