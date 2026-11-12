/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc.maven

import java.io.File
import java.util.ArrayList

import org.apache.maven.project.MavenProject
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtoPathsSpec extends AnyWordSpec with Matchers {
  "Protobuf include paths" should {
    "follow the current Maven module and plugin configuration" in {
      val mojo = new AbstractGenerateMojo(null) {
        override def addGeneratedSourceRoot(generatedSourcesDir: String): Unit = ()
      }
      def configure(module: String, path: String): Unit = {
        val project = new MavenProject()
        project.setFile(new File(module, "pom.xml"))
        mojo.project = project
        mojo.protoPaths = new ArrayList[String]()
        mojo.protoPaths.add(path)
      }

      configure("first-module", "src/main/protobuf")
      mojo.normalizedProtoPaths shouldBe Seq(new File("first-module/src/main/protobuf"))

      configure("second-module", "src/main/protobuf")
      mojo.normalizedProtoPaths shouldBe Seq(new File("second-module/src/main/protobuf"))

      mojo.protoPaths = new ArrayList[String]()
      mojo.protoPaths.add("src/test/protobuf")
      mojo.normalizedProtoPaths shouldBe Seq(new File("second-module/src/test/protobuf"))
    }
  }
}
