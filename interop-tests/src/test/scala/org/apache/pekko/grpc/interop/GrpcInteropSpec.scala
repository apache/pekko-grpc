/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop

import io.grpc.testing.integration.TestServiceHandlerFactory

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.IoGrpc)
class GrpcInteropIoWithPekkoNettyScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoNetty.Scala)
class GrpcInteropIoWithPekkoNettyJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoNetty.Java)
class GrpcInteropIoWithPekkkoHttpScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoHttp.Scala)
//class GrpcInteropIoWithpekkoHttpJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.PekkoHttp.Java)

class GrpcInteropPekkoScalaWithIoSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.IoGrpc)
class GrpcInteropPekkoScalaWithPekkoNettyScalaSpec
    extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoNetty.Scala)
class GrpcInteropPekkoScalaWithPekkoNettyJavaSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoNetty.Java)
class GrpcInteropPekkoScalaWithPekkoHttpScalaSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoHttp.Scala)
//class GrpcInteropPekkoScalaWithPekkoHttpJavaSpec extends GrpcInteropTests(Servers.Pekko.Scala, Clients.PekkoHttp.Java)

class GrpcInteropPekkoJavaWithIoSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.IoGrpc)
class GrpcInteropPekkoJavaWithPekkoNettyScalaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoNetty.Scala)
class GrpcInteropPekkoJavaWithPekkoNettyJavaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoNetty.Java)
class GrpcInteropPekkoJavaWithPekkoHttpScalaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoHttp.Scala)
//class GrpcInteropPekkoJavaWithPekkoHttpJavaSpec extends GrpcInteropTests(Servers.Pekko.Java, Clients.PekkoHttp.Java)

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
    val Java = PekkoNettyClientProviderJava$
    val Scala = new PekkoClientProviderScala("netty")
  }
  object PekkoHttp {
    // FIXME: let's have Scala stable and we'll do Java later.
    // val Java = PekkoHttpClientProviderJava
    val Scala = new PekkoClientProviderScala("pekko-http")
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

class PekkoClientProviderScala(backend: String) extends PekkoClientProvider {
  val label: String = s"pekko-grpc scala client tester $backend"

  def client = PekkoGrpcClientScala(settings => implicit sys => new PekkoGrpcScalaClientTester(settings, backend))
}

object PekkoNettyClientProviderJava$ extends PekkoClientProvider {
  val label: String = "pekko-grpc java client tester"

  def client = new PekkoGrpcClientJava((settings, sys) => new PekkoGrpcJavaClientTester(settings, sys))
}
