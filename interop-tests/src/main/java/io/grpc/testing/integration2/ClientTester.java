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

package io.grpc.testing.integration2;

import java.io.InputStream;

/**
 * This class has all the methods of the grpc-java AbstractInteropTest, but none of the
 * implementations, so it can be implemented either by calling AbstractInteropTest or with an Apache
 * Pekko gRPC implementation.
 *
 * <p>Test requirements documentation:
 * https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md
 */
public interface ClientTester {

  void setUp();

  void tearDown() throws Exception;

  void emptyUnary() throws Exception;

  void cacheableUnary();

  void largeUnary() throws Exception;

  void clientCompressedUnary(boolean probe) throws Exception;

  void serverCompressedUnary() throws Exception;

  void clientStreaming() throws Exception;

  void clientCompressedStreaming(boolean probe) throws Exception;

  void serverStreaming() throws Exception;

  void serverCompressedStreaming() throws Exception;

  void pingPong() throws Exception;

  void emptyStream() throws Exception;

  void computeEngineCreds(String serviceAccount, String oauthScope) throws Exception;

  void serviceAccountCreds(String jsonKey, InputStream credentialsStream, String authScope)
      throws Exception;

  void jwtTokenCreds(InputStream serviceAccountJson) throws Exception;

  void oauth2AuthToken(String jsonKey, InputStream credentialsStream, String authScope)
      throws Exception;

  void perRpcCreds(String jsonKey, InputStream credentialsStream, String oauthScope)
      throws Exception;

  void customMetadata() throws Exception;

  void statusCodeAndMessage() throws Exception;

  void unimplementedMethod();

  void unimplementedService();

  void cancelAfterBegin() throws Exception;

  void cancelAfterFirstResponse() throws Exception;

  void timeoutOnSleepingServer() throws Exception;
}
