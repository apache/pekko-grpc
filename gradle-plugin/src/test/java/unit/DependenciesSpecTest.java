/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package unit;

import java.io.IOException;
import java.util.stream.Stream;

import helper.BaseSpec;
import org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class DependenciesSpecTest extends BaseSpec {

    static final String PROTOC_PLUGIN_CODEGEN = "pekkoGrpc";
    static final String PROTOC_PLUGIN_SCALAPB = "scalapb";
    static final String PROTOC_PLUGIN_SCALA_VERSION = "2.12";
    static final String GRPC_VERSION = "1.82.1";

    private Project project;

    @BeforeEach
    void setUp() throws IOException {
        createBuildFolder();
        project = ProjectBuilder.builder().withProjectDir(projectDir).build();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void checkCodegen(Dependency d, PekkoGrpcPluginExtension ext) {
        assertEquals("org.apache.pekko", d.getGroup());
        assertEquals("pekko-grpc-codegen_" + PROTOC_PLUGIN_SCALA_VERSION, d.getName());
        assertEquals(ext.getPluginVersion(), d.getVersion());
    }

    private void checkScalapb(Dependency d, PekkoGrpcPluginExtension ext) {
        assertEquals("org.apache.pekko", d.getGroup());
        assertEquals("pekko-grpc-scalapb-protoc-plugin_" + PROTOC_PLUGIN_SCALA_VERSION, d.getName());
        assertEquals(ext.getPluginVersion(), d.getVersion());
    }

    @Test
    void shouldAddOnlyProtocCodegenPluginForJava() throws IOException {
        PekkoGrpcPluginExtension pekkoGrpcExt = sampleSetup(project, "java");
        ((ProjectInternal) project).evaluate();

        var cfg = project.getConfigurations().getByName("protobufToolsLocator_" + PROTOC_PLUGIN_CODEGEN);
        assertEquals(1, cfg.getDependencies().size());
        checkCodegen(cfg.getDependencies().iterator().next(), pekkoGrpcExt);
    }

    @Test
    void shouldAddProtocCodegenAndScalapbPluginsForScala() throws IOException {
        PekkoGrpcPluginExtension pekkoGrpcExt = sampleSetup(project, "scala");
        ((ProjectInternal) project).evaluate();

        var cfg = project.getConfigurations().getByName("protobufToolsLocator_" + PROTOC_PLUGIN_CODEGEN);
        assertEquals(1, cfg.getDependencies().size());
        checkCodegen(cfg.getDependencies().iterator().next(), pekkoGrpcExt);

        var cfg2 = project.getConfigurations().getByName("protobufToolsLocator_" + PROTOC_PLUGIN_SCALAPB);
        assertEquals(1, cfg2.getDependencies().size());
        checkScalapb(cfg2.getDependencies().iterator().next(), pekkoGrpcExt);
    }

    static Stream<Arguments> autodetectScalaParams() {
        return Stream.of(
                Arguments.of("java", "2.12"),
                Arguments.of("java", "2.13"),
                Arguments.of("scala", "2.12"),
                Arguments.of("scala", "2.13"));
    }

    @ParameterizedTest(name = "should autodetect scala version for pekko-grpc-runtime {0} {1}")
    @MethodSource("autodetectScalaParams")
    void shouldAutodetectScalaVersionForPekkoGrpcRuntime(String plugin, String scala) throws IOException {
        PekkoGrpcPluginExtension pekkoGrpcExt = sampleSetup(project, plugin, scala);
        ((ProjectInternal) project).evaluate();

        var deps = project.getConfigurations().getByName("implementation").getDependencies();
        assertTrue(deps.stream().anyMatch(d ->
                ("pekko-grpc-runtime_" + scala).equals(d.getName())
                        && pekkoGrpcExt.getPluginVersion().equals(d.getVersion())));
        assertTrue(deps.stream().anyMatch(d ->
                "grpc-stub".equals(d.getName())
                        && GRPC_VERSION.equals(d.getVersion())));
    }
}
