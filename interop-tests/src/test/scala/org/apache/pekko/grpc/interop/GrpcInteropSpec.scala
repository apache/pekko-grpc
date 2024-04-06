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

package org.apache.pekko.grpc.interop

import io.grpc.testing.integration.TestServiceHandlerFactory

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.IoGrpc)

class GrpcInteropIoWithPekkoNettyScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoNetty.Scala)
class GrpcInteropIoWithPekkoNettyScalaWithSslContextSpec
    extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoNetty.ScalaWithSslContext)
class GrpcInteropIoWithPekkoNettyJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoNetty.Java)
class GrpcInteropIoWithPekkoNettyJavaWithSslContextSpec
    extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoNetty.JavaWithSslContext)

class GrpcInteropIoWithPekkoHttpScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoHttp.Scala)
class GrpcInteropIoWithPekkoHttpScalaWithSslContextSpec
    extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoHttp.ScalaWithSslContext)
class GrpcInteropIoWithPekkoHttpJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoHttp.Java)
class GrpcInteropIoWithPekkoHttpJavaWithSslContextSpec
    extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoHttp.JavaWithSslContext)

class GrpcInteropPekkoScalaWithIoSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.IoGrpc)

class GrpcInteropPekkoScalaWithPekkoNettyScalaSpec
    extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoNetty.Scala)
class GrpcInteropPekkoScalaWithPekkoNettyJavaSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoNetty.Java)
class GrpcInteropPekkoScalaWithPekkoHttpScalaSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoHttp.Scala)
class GrpcInteropPekkoScalaWithPekkoHttpJavaSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoHttp.Java)

class GrpcInteropPekkoJavaWithIoSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.IoGrpc)
class GrpcInteropPekkoJavaWithPekkoNettyScalaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoNetty.Scala)
class GrpcInteropPekkoJavaWithPekkoNettyJavaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoNetty.Java)
class GrpcInteropPekkoJavaWithPekkoHttpScalaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoHttp.Scala)
class GrpcInteropPekkoJavaWithPekkoHttpJavaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoHttp.Java)

//--- Aliases

object Servers {
  val IoGrpc = IoGrpcJavaServerProvider
  object Pekko {
    val Java = PekkoHttpServerProviderJava$
    val Scala = PekkoHttpServerProviderScala
  }
}

object Clients {
  val IoGrpc = IoGrpcJavaClientProvider
  object PekkoNetty {
    val Java = new PekkoClientProviderJava("netty", false)
    val Scala = new PekkoClientProviderScala("netty", false)
    val ScalaWithSslContext = new PekkoClientProviderScala("netty", true)
    val JavaWithSslContext = new PekkoClientProviderJava("netty", true)
  }
  object PekkoHttp {
    val Java = new PekkoClientProviderJava("pekko-http", false)
    val Scala = new PekkoClientProviderScala("pekko-http", false)
    val ScalaWithSslContext = new PekkoClientProviderScala("pekko-http", true)
    val JavaWithSslContext = new PekkoClientProviderJava("pekko-http", true)
  }
}

//--- Some more providers

object PekkoHttpServerProviderJava$ extends PekkoHttpServerProvider {
  val label: String = "pekko-grpc java server"

  val pendingCases =
    Set("custom_metadata")

  val server = new PekkoGrpcServerJava((mat, sys) => {
    TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), sys)
  })
}

class PekkoClientProviderScala(backend: String, testWithSslContext: Boolean) extends PekkoClientProvider {
  val label: String = s"pekko-grpc scala client tester $backend"

  def client = PekkoGrpcClientScala(settings =>
    implicit sys => new PekkoGrpcScalaClientTester(settings, backend, testWithSslContext))
}

class PekkoClientProviderJava(backend: String, testWithSslContext: Boolean) extends PekkoClientProvider {
  val label: String = "pekko-grpc java client tester"

  def client = new PekkoGrpcClientJava((settings, sys) =>
    new PekkoGrpcJavaClientTester(settings, sys, backend, testWithSslContext))
}
