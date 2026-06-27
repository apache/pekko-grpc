/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package func;

import java.io.IOException;
import java.util.stream.Stream;

import helper.BaseSpec;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class GradleCompatibilitySpecTest extends BaseSpec {

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private BuildResult executeGradleTaskWithVersion(String task, String gradleVersion, boolean shouldFail) {
        GradleRunner runner = GradleRunner.create().forwardOutput()
                .withProjectDir(projectDir)
                .withArguments(gradleArguments(task))
                .withPluginClasspath()
                .withDebug(true)
                .withGradleVersion(gradleVersion);

        return runGradle(runner, shouldFail);
    }

    static Stream<Arguments> successVersions() {
        return Stream.of(
                Arguments.of("5.6"),
                Arguments.of("5.6.4"),
                Arguments.of("7.6.4"));
    }

    @ParameterizedTest(name = "should succeed for version {0} greater than 5.6")
    @MethodSource("successVersions")
    void shouldSucceedForVersionGreaterThanOrEqualTo56(String gradleVersion) throws IOException {
        assumeGradleVersionCanRunOnCurrentJava(gradleVersion);

        createBuildFolder();
        generateBuildScripts();

        BuildResult result = executeGradleTaskWithVersion("tasks", gradleVersion, false);

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks").getOutcome());
    }

    static Stream<Arguments> failVersions() {
        return Stream.of(
                Arguments.of("5.5"),
                Arguments.of("4.0"));
    }

    @ParameterizedTest(name = "should fail for version {0} less than 5.6")
    @MethodSource("failVersions")
    void shouldFailForVersionLessThan56(String gradleVersion) throws IOException {
        assumeGradleVersionCanRunOnCurrentJava(gradleVersion);

        createBuildFolder();
        generateBuildScripts();

        BuildResult result = executeGradleTaskWithVersion("tasks", gradleVersion, true);

        assertTrue(result.getOutput().contains(
                "Gradle version is " + gradleVersion + ". Minimum supported version is 5.6"));
    }

    private static void assumeGradleVersionCanRunOnCurrentJava(String gradleVersion) {
        Assumptions.assumeFalse(
                Runtime.version().feature() >= 17 &&
                        GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("7.3")) < 0 &&
                        !GradleVersion.version(gradleVersion).equals(GradleVersion.version("4.0")),
                "Gradle " + gradleVersion + " cannot run on Java " + Runtime.version().feature());
    }
}
