/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package unit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import helper.BaseSpec;
import helper.ScalaWrapperPlugin;
import org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

class ApplySpecTest extends BaseSpec {

    private Project project;

    private File log;

    @BeforeEach
    void setUp() throws IOException {
        createBuildFolder();
        project = ProjectBuilder.builder().withProjectDir(projectDir).build();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void createLog() throws IOException {
        log = new File(buildDir, "pekko-grpcgradle-plugin.log");
        log.createNewFile();
    }

    private BuildResult executeGradleTask(String task) {
        GradleRunner runner = GradleRunner.create().forwardOutput()
                .withProjectDir(projectDir)
                .withArguments(gradleArguments(task))
                .withPluginClasspath()
                .withDebug(true);
        return runGradle(runner);
    }

    static Stream<Arguments> detectLanguageParams() {
        return Stream.of(
                Arguments.of("java", "2.12", false),
                Arguments.of("java", "2.13", false),
                Arguments.of("scala", "2.12", true),
                Arguments.of("scala", "2.13", true),
                Arguments.of(ScalaWrapperPlugin.class, "2.12", true),
                Arguments.of(ScalaWrapperPlugin.class, "2.13", true));
    }

    @ParameterizedTest(name = "should detect language for {0}")
    @MethodSource("detectLanguageParams")
    void shouldDetectLanguage(Object plugin, String scala, boolean isScala) throws IOException {
        PekkoGrpcPluginExtension pekkoGrpcExt;
        if (plugin instanceof String) {
            pekkoGrpcExt = sampleSetup(project, (String) plugin, scala);
        } else {
            pekkoGrpcExt = sampleSetupWithPlugin(project, (Class<?>) plugin, scala);
        }
        ((ProjectInternal) project).evaluate();

        assertEquals(System.getProperty("pekkoGrpcTest.pluginVersion"), pekkoGrpcExt.getPluginVersion());
        assertEquals(isScala, pekkoGrpcExt.getScala());
    }

    @Test
    void shouldFailIfNoScalaLibraryDeclared() {
        project.getPluginManager().apply("scala");
        project.getPluginManager().apply(PLUGIN_CODE);

        ProjectConfigurationException ex = assertThrows(ProjectConfigurationException.class,
                () -> ((ProjectInternal) project).evaluate());

        assertTrue(ex.getCause().getMessage().startsWith(
                PLUGIN_CODE + " requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath."));
        assertTrue(ex.getCause().getMessage().endsWith("Found []"));
    }

    @Test
    void shouldFailIfMultipleScalaLibraryDeclared() {
        project.getPluginManager().apply(PLUGIN_CODE);
        project.getDependencies().add("implementation", "org.mockito:mockito-scala_2.11:1.6.1");
        project.getDependencies().add("implementation", "org.mockito:mockito-scala_2.13:1.14.8");

        ProjectConfigurationException ex = assertThrows(ProjectConfigurationException.class,
                () -> ((ProjectInternal) project).evaluate());

        assertTrue(ex.getCause().getMessage().startsWith(
                PLUGIN_CODE + " requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath."));
        assertTrue(ex.getCause().getMessage().endsWith("Found [2.11, 2.13]"));
    }

    @Test
    void shouldNotFailScalaAutodetectIfDependenciesContainUnderscore() throws IOException {
        PekkoGrpcPluginExtension pekkoGrpcExt = sampleSetup(project);
        project.getDependencies().add("implementation", "com.google.errorprone:error_prone_annotations:2.3.4");

        ((ProjectInternal) project).evaluate();

        assertTrue(pekkoGrpcExt.getScala());
    }

    @Test
    void shouldDisableCompileJavaIfNoJavaSourceFilesFound() throws IOException {
        sampleSetup(project);
        generateBuildScripts();
        createLog();

        ((ProjectInternal) project).evaluate();
        BuildResult result = executeGradleTask("compileJava");

        assertTrue(project.getTasks().getByName("compileJava").getEnabled());
        assertEquals(SKIPPED, result.task(":compileJava").getOutcome());
    }

    @Test
    void shouldEnableCompileJavaIfJavaSourceFilesFound() throws IOException {
        sampleSetup(project);
        generateBuildScripts();
        createLog();

        File javaDir = Files.createDirectories(projectDir.toPath().resolve("src/main/java")).toFile();
        Files.writeString(new File(javaDir, "Empty.java").toPath(), "final class Empty {}");

        BuildResult result = executeGradleTask("compileJava");

        assertEquals(SUCCESS, result.task(":compileJava").getOutcome());
    }

    @Test
    void shouldEnableCompileJavaIfProjectHasOnlyGeneratedJavaSourceFiles() throws IOException {
        sampleSetup(project, "kotlin");

        File kotlinDir = Files.createDirectories(projectDir.toPath().resolve("src/main/kotlin")).toFile();
        Files.writeString(new File(kotlinDir, "Empty.kt").toPath(), "object Empty {}");

        ((ProjectInternal) project).evaluate();

        assertTrue(project.getTasks().getByName("compileJava").getEnabled());
    }

    @Test
    void shouldAllowImplicitDeclarationsOfScalaLibraryVersion() {
        project.getPluginManager().apply(PLUGIN_CODE);
        project.getDependencies().getConstraints().add("implementation",
                "org.scala-lang:scala-library:2.13.1");
        project.getDependencies().add("implementation", "org.scala-lang:scala-library");

        ((ProjectInternal) project).evaluate();

        assertNotNull(project.getExtensions().getByType(PekkoGrpcPluginExtension.class));
    }

    @Test
    void shouldFailIfScalaVersionImplicitlyDeclaredAndMismatches() {
        project.getPluginManager().apply(PLUGIN_CODE);
        project.getDependencies().getConstraints().add("implementation",
                "org.scala-lang:scala-library:2.12.0");
        project.getDependencies().add("implementation",
                "com.typesafe.scala-logging:scala-logging_2.13:3.9.2");

        ProjectConfigurationException ex = assertThrows(ProjectConfigurationException.class,
                () -> ((ProjectInternal) project).evaluate());

        assertTrue(ex.getCause().getMessage().startsWith(
                PLUGIN_CODE + " requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath."));
        assertTrue(ex.getCause().getMessage().endsWith("Found [2.12, 2.13]"));
    }
}
