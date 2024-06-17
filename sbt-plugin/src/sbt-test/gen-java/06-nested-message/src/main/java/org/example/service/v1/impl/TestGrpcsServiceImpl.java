/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.example.service.v1.impl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import org.example.service.v1.TestGrpcsService;
import org.example.service.v1.Test.EnvelopeTest.RequestTest;
import org.example.service.v1.Test.EnvelopeTest.ResponseTest;

class TestGrpcsServiceImpl implements TestGrpcsService {
  public Source<ResponseTest, NotUsed> transceive(RequestTest request) {
    throw new UnsupportedOperationException();
  }
}
