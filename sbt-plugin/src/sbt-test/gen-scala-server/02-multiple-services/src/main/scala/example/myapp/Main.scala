/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package example.myapp

import java.io.InputStream
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.scaladsl.ServiceHandler
import pekko.http.scaladsl.{ Http, HttpsConnectionContext }

import example.myapp.echo.EchoServiceImpl
import example.myapp.echo.grpc.EchoServiceHandler

import example.myapp.helloworld.GreeterServiceImpl
import example.myapp.helloworld.grpc.GreeterServiceHandler

object Main extends App {
  implicit val system: ActorSystem = ActorSystem()

  val echoHandler = EchoServiceHandler.partial(new EchoServiceImpl)
  val greeterHandler = GreeterServiceHandler.partial(new GreeterServiceImpl)
  val serviceHandler = ServiceHandler.concatOrNotFound(echoHandler, greeterHandler)

  Http().newServerAt("localhost", 8443)
    .enableHttps(serverHttpContext())
    .bind(serviceHandler)

  private def serverHttpContext() = {
    // never put passwords into code!
    val password = "abcdef".toCharArray

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(Option(getClass.getClassLoader.getResourceAsStream("server.p12")).get, password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)

    HttpsConnectionContext.httpsServer(context)
  }
}
