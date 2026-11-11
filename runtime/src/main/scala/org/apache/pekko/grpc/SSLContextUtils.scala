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

package org.apache.pekko.grpc

import java.io.{ BufferedInputStream, IOException, InputStream }
import java.security.KeyStore
import java.security.cert.{ CertificateFactory, X509Certificate }

import org.apache.pekko.annotation.InternalApi

import javax.net.ssl.{ SSLContext, SSLContextSpi, SSLEngine, SSLParameters, TrustManager, TrustManagerFactory }

object SSLContextUtils {
  def trustManagerFromStream(certStream: InputStream): TrustManager = {
    try {
      import scala.jdk.CollectionConverters._
      val cf = CertificateFactory.getInstance("X.509")
      val bis = new BufferedInputStream(certStream)

      val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
      keystore.load(null)
      cf.generateCertificates(bis).asScala.foreach { cert =>
        val alias = cert.asInstanceOf[X509Certificate].getSubjectX500Principal.getName
        keystore.setCertificateEntry(alias, cert)
      }

      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(keystore)
      tmf.getTrustManagers()(0)
    } finally certStream.close()
  }

  def trustManagerFromResource(certificateResourcePath: String): TrustManager = {
    val certStream: InputStream = getClass.getResourceAsStream(certificateResourcePath)
    if (certStream == null) throw new IOException(s"Couldn't find '$certificateResourcePath' on the classpath")
    trustManagerFromStream(certStream)
  }

  /**
   * Known TLS protocol versions in ascending order of strength.
   * Used to filter protocols based on a minimum version requirement.
   */
  private val TlsVersionOrder: List[String] =
    List("TLSv1.2", "TLSv1.3")

  /**
   * INTERNAL API
   *
   * Returns `version` if it is a minimum TLS version this library accepts, so that a typo fails
   * when the settings are built rather than when a channel is first created.
   *
   * @throws IllegalArgumentException if `version` is not one of the accepted names
   */
  @InternalApi
  private[grpc] def requireKnownTlsVersion(version: String): String = {
    tlsVersionIndex(version)
    version
  }

  private def tlsVersionIndex(minimumVersion: String): Int = {
    val minIndex = TlsVersionOrder.indexOf(minimumVersion)
    if (minIndex < 0)
      throw new IllegalArgumentException(
        s"Unknown TLS version '$minimumVersion'. Expected one of: ${TlsVersionOrder.mkString(", ")}")
    minIndex
  }

  /**
   * INTERNAL API
   *
   * Returns the TLS protocols enabled by default on the given SSLContext that are at or above
   * the specified minimum version. Throws IllegalArgumentException if the minimum
   * version is not a known TLS protocol name.
   *
   * Reads the *default* (enabled) parameters rather than the supported ones: the minimum
   * version only narrows what may be negotiated, so a version the JDK has disabled (for example
   * through `jdk.tls.client.protocols` or `jdk.tls.disabledAlgorithms`) stays disabled.
   */
  @InternalApi
  private[grpc] def enabledProtocols(sslContext: SSLContext, minimumVersion: String): Array[String] = {
    val minIndex = tlsVersionIndex(minimumVersion)
    val enabled = sslContext.getDefaultSSLParameters.getProtocols
    filterProtocols(enabled, minIndex)
  }

  /**
   * INTERNAL API
   *
   * Returns TLS protocols from the given list that are at or above the specified minimum version.
   */
  @InternalApi
  private[grpc] def enabledProtocols(supportedProtocols: Array[String], minimumVersion: String): Array[String] = {
    val minIndex = tlsVersionIndex(minimumVersion)
    filterProtocols(supportedProtocols, minIndex)
  }

  private def filterProtocols(supported: Array[String], minIndex: Int): Array[String] = {
    val allowed = TlsVersionOrder.drop(minIndex).toSet
    val filtered = supported.filter(allowed.contains)
    if (filtered.isEmpty)
      throw new IllegalStateException(
        s"No TLS protocols at or above ${TlsVersionOrder(minIndex)} are supported. " +
        s"Supported protocols: ${supported.mkString(", ")}")
    filtered
  }

  /**
   * INTERNAL API
   *
   * Wraps an SSLContext so that all SSLEngines created from it will only allow
   * protocols at or above the specified minimum TLS version.
   */
  @InternalApi
  private[grpc] def withMinimumTlsVersion(sslContext: SSLContext, minimumVersion: String): SSLContext = {
    val protocols = enabledProtocols(sslContext, minimumVersion)
    new SSLContext(new SSLContextSpi {
        override def engineCreateSSLEngine(): SSLEngine = {
          val engine = sslContext.createSSLEngine()
          engine.setEnabledProtocols(protocols)
          engine
        }
        override def engineCreateSSLEngine(host: String, port: Int): SSLEngine = {
          val engine = sslContext.createSSLEngine(host, port)
          engine.setEnabledProtocols(protocols)
          engine
        }
        override def engineInit(
            km: Array[javax.net.ssl.KeyManager],
            tm: Array[TrustManager],
            random: java.security.SecureRandom): Unit =
          sslContext.init(km, tm, random)
        override def engineGetSocketFactory: javax.net.ssl.SSLSocketFactory = sslContext.getSocketFactory
        override def engineGetServerSocketFactory: javax.net.ssl.SSLServerSocketFactory =
          sslContext.getServerSocketFactory
        override def engineGetServerSessionContext: javax.net.ssl.SSLSessionContext = sslContext.getServerSessionContext
        override def engineGetClientSessionContext: javax.net.ssl.SSLSessionContext = sslContext.getClientSessionContext
        override def engineGetDefaultSSLParameters: SSLParameters = {
          val params = sslContext.getDefaultSSLParameters
          params.setProtocols(protocols)
          params
        }
        override def engineGetSupportedSSLParameters: SSLParameters = {
          val params = sslContext.getSupportedSSLParameters
          params.setProtocols(protocols)
          params
        }
      }, sslContext.getProvider, sslContext.getProtocol) {}
  }
}
