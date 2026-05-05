/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

//#full-server
package example.myapp.helloworld;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.ConnectionContext;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.HttpsConnectionContext;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.pki.pem.DERPrivateKeyLoader;
import org.apache.pekko.pki.pem.PEMDecoder;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

class MtlsGreeterServer {

  private static final Logger log = LoggerFactory.getLogger(MtlsGreeterServer.class);

  public static void main(String[] args) throws Exception {
    ActorSystem sys = ActorSystem.create("MtlsHelloWorldServer");

    run(sys).thenAccept(binding -> {
      log.info("gRPC server bound to {}", binding.localAddress());
    });

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
    Materializer mat = SystemMaterializer.get(sys).materializer();

    // Instantiate implementation
    GreeterService impl = new GreeterServiceImpl(mat);

    Function<HttpRequest, CompletionStage<HttpResponse>> service =
      GreeterServiceHandlerFactory.create(impl, sys);

    return Http
      .get(sys)
      .newServerAt("127.0.0.1", 8443)
      .enableHttps(serverHttpContext())
      .bind(service);
  }

  private static HttpsConnectionContext serverHttpContext() {
    try {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

      // keyStore is for the server cert and private key
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null);
      PrivateKey serverPrivateKey =
        DERPrivateKeyLoader.load(PEMDecoder.decode(classPathFileAsString("/certs/localhost-server.key")));
      Certificate serverCert = certFactory.generateCertificate(
        MtlsGreeterServer.class.getResourceAsStream("/certs/localhost-server.crt"));
      keyStore.setKeyEntry(
        "private",
        serverPrivateKey,
        // No password for our private key
        new char[0],
        new Certificate[]{ serverCert });
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
      keyManagerFactory.init(keyStore, null);
      final KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

      // trustStore is for what client certs the server trust
      KeyStore trustStore = KeyStore.getInstance("PKCS12");
      trustStore.load(null);
      // any client cert signed by this CA is allowed to connect
      trustStore.setEntry(
        "rootCA",
        new KeyStore.TrustedCertificateEntry(
          certFactory.generateCertificate(MtlsGreeterServer.class.getResourceAsStream("/certs/rootCA.crt"))),
        null);
      /*
      // or specific client certs (less likely to be useful)
      trustStore.setEntry(
        "client1",
        new KeyStore.TrustedCertificateEntry(
          certFactory.generateCertificate(getClass().getResourceAsStream("/certs/localhost-client.crt"))),
        null)
       */
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
      tmf.init(trustStore);
      final TrustManager[] trustManagers = tmf.getTrustManagers();

      HttpsConnectionContext httpsContext = ConnectionContext.httpsServer(() -> {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, new SecureRandom());

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);

        // require client certs
        engine.setNeedClientAuth(true);

        return engine;
      });
      return httpsContext;

    } catch (Exception ex) {
      throw new RuntimeException("Failed setting up the server HTTPS context", ex);
    }
  }

  private static String classPathFileAsString(String path) {
    try (InputStream inputStream = MtlsGreeterServer.class.getResourceAsStream(path)) {
      if (inputStream == null) throw new IllegalArgumentException("'" + path + "' is not present on the classpath");
      return new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining("\n"));
    } catch (Exception ex) {
      throw new RuntimeException("Failed reading server key from classpath", ex);
    }
  }

}
//#full-server
