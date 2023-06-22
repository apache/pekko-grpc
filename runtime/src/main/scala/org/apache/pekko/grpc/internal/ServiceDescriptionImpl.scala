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

import com.google.protobuf.Descriptors.FileDescriptor;

import org.apache.pekko
import pekko.annotation.InternalApi

import pekko.grpc.ServiceDescription

/**
 * INTERNAL API
 */
@InternalApi
class ServiceDescriptionImpl(val name: String, val descriptor: FileDescriptor) extends ServiceDescription
