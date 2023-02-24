/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop

import io.grpc.testing.integration.TestServiceHandlerFactory

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.IoGrpc)
class GrpcInteropIoWithAkkaNettyScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaNetty.Scala)
class GrpcInteropIoWithAkkaNettyJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaNetty.Java)
class GrpcInteropIoWithAkkaHttpScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaHttp.Scala)
//class GrpcInteropIoWithAkkaHttpJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaHttp.Java)

class GrpcInteropPekkoScalaWithIoSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.IoGrpc)
class GrpcInteropPekkoScalaWithAkkaNettyScalaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaNetty.Scala)
class GrpcInteropPekkoScalaWithAkkaNettyJavaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaNetty.Java)
class GrpcInteropPekkoScalaWithAkkaHttpScalaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaHttp.Scala)
//class GrpcInteropPekkoScalaWithAkkaHttpJavaSpec extends GrpcInteropTests(Servers.Akka.Scala, Clients.AkkaHttp.Java)

class GrpcInteropPekkoJavaWithIoSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.IoGrpc)
class GrpcInteropPekkoJavaWithAkkaNettyScalaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaNetty.Scala)
class GrpcInteropPekkoJavaWithAkkaNettyJavaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaNetty.Java)
class GrpcInteropPekkoJavaWithAkkaHttpScalaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaHttp.Scala)
//class GrpcInteropPekkoJavaWithAkkaHttpJavaSpec extends GrpcInteropTests(Servers.Akka.Java, Clients.AkkaHttp.Java)

//--- Aliases

object Servers {
  val IoGrpc = IoGrpcJavaServerProvider
  object Akka {
    val Java = PekkoHttpServerProviderJava$
    val Scala = PekkoHttpServerProviderScala
  }
}

object Clients {
  val IoGrpc = IoGrpcJavaClientProvider
  object AkkaNetty {
    val Java = PekkoNettyClientProviderJava$
    val Scala = new PekkoClientProviderScala("netty")
  }
  object AkkaHttp {
    // FIXME: let's have Scala stable and we'll do Java later.
    // val Java = AkkaHttpClientProviderJava
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
