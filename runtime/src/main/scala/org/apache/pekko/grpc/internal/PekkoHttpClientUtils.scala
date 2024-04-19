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

import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.CompletionStage
import scala.concurrent.duration._
import org.apache.pekko
import pekko.{ Done, NotUsed }
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.grpc.GrpcProtocol.GrpcProtocolReader
import pekko.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcSingleResponse, ProtobufSerializer }
import pekko.http.scaladsl.model.HttpEntity.{ Chunk, Chunked, LastChunk, Strict }
import pekko.http.scaladsl.{ ClientTransport, ConnectionContext, Http }
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.{ Materializer, OverflowStrategy }
import pekko.stream.scaladsl.{ Keep, Sink, Source }
import pekko.util.ByteString
import pekko.util.FutureConverters._
import io.grpc.{ CallOptions, MethodDescriptor, Status, StatusRuntimeException }

import javax.net.ssl.{ KeyManager, SSLContext, TrustManager }
import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }
import pekko.http.scaladsl.model.StatusCodes

/**
 * INTERNAL API
 */
@InternalApi
object PekkoHttpClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit sys: ClassicActorSystemProvider): InternalChannel = {
    implicit val ec = sys.classicSystem.dispatcher

    log.debug("Creating gRPC client channel")

    // https://github.com/grpc/grpc/blob/master/doc/compression.md
    // since a client can't assume what algorithms a server supports, we
    // must default to no compression.
    // Configuring a different default could be a future feature.
    // Configuring compression per call could be a future power API feature.
    implicit val writer = GrpcProtocolNative.newWriter(Identity)

    // TODO FIXME adapt to new API's for discovery, loadbalancing etc
    // https://github.com/akka/akka-grpc/issues/1196
    // https://github.com/akka/akka-grpc/issues/1197

    var roundRobin: Int = 0
    val clientConnectionSettings =
      ClientConnectionSettings(sys).withTransport(ClientTransport.withCustomResolver((host, _) => {
        settings.overrideAuthority.foreach { authority =>
          assert(host == authority)
        }
        settings.serviceDiscovery.lookup(settings.serviceName, 10.seconds).map { resolved =>
          // quasi-roundrobin is nicer than random selection: somewhat lower chance of making
          // an 'unlucky choice' multiple times in a row.
          roundRobin += 1
          val target = resolved.addresses(roundRobin % resolved.addresses.size)
          target.address match {
            case Some(address) =>
              new InetSocketAddress(address, target.port.getOrElse(settings.defaultPort))
            case None =>
              new InetSocketAddress(target.host, target.port.getOrElse(settings.defaultPort))
          }
        }
      }))

    val builder = Http()
      .connectionTo(settings.overrideAuthority.getOrElse(settings.serviceName))
      .withClientConnectionSettings(clientConnectionSettings)

    val http2client =
      if (settings.useTls) {
        val connectionContext =
          ConnectionContext.httpsClient {
            settings.sslContext.getOrElse {
              settings.trustManager match {
                case None => SSLContext.getDefault
                case Some(trustManager) =>
                  val sslContext: SSLContext = SSLContext.getInstance("TLS")
                  sslContext.init(Array[KeyManager](), Array[TrustManager](trustManager), new SecureRandom)
                  sslContext
              }
            }
          }

        builder.withCustomHttpsConnectionContext(connectionContext).managedPersistentHttp2()
      } else {
        builder.managedPersistentHttp2WithPriorKnowledge()
      }

    val (queue, doneFuture) =
      Source
        .queue[HttpRequest](4242, OverflowStrategy.fail)
        .via(http2client)
        .toMat(Sink.foreach { res =>
          res.attribute(ResponsePromise.Key).get.promise.trySuccess(res)
        })(Keep.both)
        .run()

    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      val p = Promise[HttpResponse]()
      queue.offer(request.addAttribute(ResponsePromise.Key, ResponsePromise(p))).flatMap(_ => p.future)
    }

    implicit def serializerFromMethodDescriptor[I, O](descriptor: MethodDescriptor[I, O]): ProtobufSerializer[I] =
      descriptor.getRequestMarshaller.asInstanceOf[WithProtobufSerializer[I]].protobufSerializer

    implicit def deserializerFromMethodDescriptor[I, O](descriptor: MethodDescriptor[I, O]): ProtobufSerializer[O] =
      descriptor.getResponseMarshaller.asInstanceOf[WithProtobufSerializer[O]].protobufSerializer

    new InternalChannel() {
      override def shutdown(): Unit = queue.complete()

      override def done: Future[Done] = doneFuture

      override def invoke[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[O] =
        invokeWithMetadata(request, headers, descriptor, options).map(_.value)

      override def invokeWithMetadata[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[GrpcSingleResponse[O]] = {
        val src =
          invokeWithMetadata(Source.single(request), headers, descriptor, streamingResponse = false, options)
        val (metadataFuture, resultFuture) = src.toMat(Sink.head)(Keep.both).run()
        metadataFuture.zip(resultFuture).map {
          case (metadata, result) =>
            new GrpcSingleResponse[O] {
              def value: O = result

              def getValue(): O = result

              def headers = metadata.headers

              def getHeaders() = metadata.getHeaders()

              def trailers = metadata.trailers

              def getTrailers() = metadata.getTrailers()
            }
        }
      }

      override def invokeWithMetadata[I, O](
          source: Source[I, NotUsed],
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = {
        implicit val serializer: ProtobufSerializer[I] = descriptor
        val deserializer: ProtobufSerializer[O] = descriptor
        val scheme = if (settings.useTls) "https" else "http"
        val httpRequest = GrpcRequestHelpers(
          Uri(
            s"${scheme}://${settings.overrideAuthority.getOrElse(settings.serviceName)}/" + descriptor.getFullMethodName),
          GrpcEntityHelpers.metadataHeaders(headers.entries),
          source)
        responseToSource(singleRequest(httpRequest), deserializer)
      }
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  def responseToSource[O](response: Future[HttpResponse], deserializer: ProtobufSerializer[O])(
      implicit ec: ExecutionContext,
      mat: Materializer): Source[O, Future[GrpcResponseMetadata]] = {
    Source.lazyFutureSource[O, Future[GrpcResponseMetadata]](() => {
      response.map { response =>
        {
          if (response.status != StatusCodes.OK) {
            response.entity.discardBytes()
            val failure = mapToStatusException(response, immutable.Seq.empty)
            Source.failed(failure).mapMaterializedValue(_ => Future.failed(failure))
          } else {
            Codecs.detect(response) match {
              case Success(codec) =>
                implicit val reader: GrpcProtocolReader = GrpcProtocolNative.newReader(codec)
                val trailerPromise = Promise[immutable.Seq[HttpHeader]]()
                // Completed with success or failure based on grpc-status and grpc-message trailing headers
                val completionFuture: Future[Unit] =
                  trailerPromise.future.flatMap(trailers => parseResponseStatus(response, trailers))

                val responseData =
                  response.entity match {
                    case Chunked(_, chunks) =>
                      chunks
                        .map {
                          case Chunk(data, _) =>
                            data
                          case LastChunk(_, trailer) =>
                            trailerPromise.success(trailer)
                            ByteString.empty
                        }
                        .watchTermination()((_, done) =>
                          done.onComplete(_ => trailerPromise.trySuccess(immutable.Seq.empty)))
                    case Strict(_, data) =>
                      val rawTrailers =
                        response.attribute(AttributeKeys.trailer).map(_.headers).getOrElse(immutable.Seq.empty)
                      val trailers = rawTrailers.map(h => RawHeader(h._1, h._2))
                      trailerPromise.success(trailers)
                      Source.single[ByteString](data)
                    case _ =>
                      response.entity.discardBytes()
                      throw mapToStatusException(response, Seq.empty)
                  }
                responseData
                  // This never adds any data to the stream, but makes sure it fails with the correct error code if applicable
                  .concat(
                    Source
                      .maybe[ByteString]
                      .mapMaterializedValue(promise => promise.completeWith(completionFuture.map(_ => None))))
                  // Make sure we continue reading to get the trailing header even if we're no longer interested in the rest of the body
                  .via(new CancellationBarrierGraphStage)
                  .via(reader.dataFrameDecoder)
                  .map(deserializer.deserialize)
                  .mapMaterializedValue(_ =>
                    Future.successful(new GrpcResponseMetadata() {
                      override def headers: pekko.grpc.scaladsl.Metadata =
                        new HeaderMetadataImpl(response.headers)

                      override def getHeaders(): pekko.grpc.javadsl.Metadata =
                        new JavaMetadataImpl(new HeaderMetadataImpl(response.headers))

                      override def trailers: Future[pekko.grpc.scaladsl.Metadata] =
                        trailerPromise.future.map(new HeaderMetadataImpl(_))

                      override def getTrailers(): CompletionStage[pekko.grpc.javadsl.Metadata] =
                        trailerPromise.future
                          .map[pekko.grpc.javadsl.Metadata](h =>
                            new JavaMetadataImpl(new HeaderMetadataImpl(h)))
                          .asJava
                    }))
              case Failure(e) =>
                Source.failed[O](e).mapMaterializedValue(_ => Future.failed(e))
            }
          }
        }
      }
    })
  }.mapMaterializedValue(_.flatten)

  private def parseResponseStatus(response: HttpResponse, trailers: Seq[HttpHeader]): Future[Unit] = {
    val allHeaders = response.headers ++ trailers
    allHeaders.find(_.name == "grpc-status").map(_.value) match {
      case Some("0") =>
        Future.successful(())
      case _ =>
        Future.failed(mapToStatusException(response, trailers))
    }
  }

  private def mapToStatusException(response: HttpResponse, trailers: Seq[HttpHeader]): StatusRuntimeException = {
    val allHeaders = response.headers ++ trailers
    val metadata: io.grpc.Metadata = new MetadataImpl(new HeaderMetadataImpl(allHeaders).asList).toGoogleGrpcMetadata()
    allHeaders.find(_.name == "grpc-status").map(_.value) match {
      case None =>
        new StatusRuntimeException(mapHttpStatus(response).withDescription("No grpc-status found"), metadata)
      case Some(statusCode) =>
        val description = allHeaders.find(_.name == "grpc-message").map(_.value)
        new StatusRuntimeException(Status.fromCodeValue(statusCode.toInt).withDescription(description.orNull), metadata)
    }
  }

  /**
   * See https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
   */
  private def mapHttpStatus(response: HttpResponse): Status = {
    response.status match {
      case StatusCodes.BadRequest         => Status.INTERNAL
      case StatusCodes.Unauthorized       => Status.UNAUTHENTICATED
      case StatusCodes.Forbidden          => Status.PERMISSION_DENIED
      case StatusCodes.NotFound           => Status.UNIMPLEMENTED
      case StatusCodes.TooManyRequests    => Status.UNAVAILABLE
      case StatusCodes.BadGateway         => Status.UNAVAILABLE
      case StatusCodes.ServiceUnavailable => Status.UNAVAILABLE
      case StatusCodes.GatewayTimeout     => Status.UNAVAILABLE
      case _                              => Status.UNKNOWN
    }
  }

  case class ResponsePromise(promise: Promise[HttpResponse]) extends RequestResponseAssociation
  object ResponsePromise {
    val Key = AttributeKey[ResponsePromise]("association-handle")
  }
}
