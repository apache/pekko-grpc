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

package func

import helper.BaseSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class GradleCompatibilitySpec extends BaseSpec {

    BuildResult executeGradleTaskWithVersion(String task, String gradleVersion, boolean shouldFail) {
        def runner = GradleRunner.create().forwardOutput()
            .withProjectDir(projectDir.root)
            .withArguments("--stacktrace", task)
            .withPluginClasspath()
            .withDebug(true)
            .withGradleVersion(gradleVersion)

        if (shouldFail) {
            return runner.buildAndFail()
        } else {
            return runner.build()
        }
    }

    @Unroll
    void 'should succeed for version #gradleVersion greater than 5.6'() {
        given:
        createBuildFolder()
        generateBuildScripts()
        when:
        BuildResult result = executeGradleTaskWithVersion('tasks', gradleVersion, false)
        then:
        result.task(":tasks").outcome == TaskOutcome.SUCCESS
        where:
        gradleVersion << ["5.6", "5.6.4", "6.4.1"]
    }

    @Unroll
    void 'should fail for version #gradleVersion less than 5.6'() {
        given:
        createBuildFolder()
        generateBuildScripts()
        when:
        BuildResult result = executeGradleTaskWithVersion('tasks', gradleVersion, true)
        then:
        result.output.contains("Gradle version is ${gradleVersion}. Minimum supported version is 5.6")
        where:
        gradleVersion << ["5.5", "4.0"]
    }
}
