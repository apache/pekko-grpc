/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension;
import org.gradle.api.Project;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

public abstract class BaseSpec {

    public static final String PLUGIN_CODE = "org.apache.pekko.grpc.gradle";

    protected File projectDir;
    protected File srcDir;
    protected File buildDir;
    protected File buildFile;

    protected File ensureProjectDir() throws IOException {
        if (projectDir == null) {
            projectDir = Files.createTempDirectory("pekko-grpc-test-").toFile();
        }
        return projectDir;
    }

    protected void cleanup() {
        if (projectDir != null && projectDir.exists()) {
            deleteRecursively(projectDir);
        }
    }

    protected void createBuildFolder() throws IOException {
        ensureProjectDir();
        srcDir = Files.createDirectories(projectDir.toPath().resolve("src/main/proto")).toFile();
        buildDir = Files.createDirectories(projectDir.toPath().resolve("build")).toFile();
    }

    protected void generateBuildScripts() throws IOException {
        ensureProjectDir();
        buildFile = new File(projectDir, "build.gradle");
        Files.writeString(buildFile.toPath(),
                "plugins {\n" +
                "  id '" + PLUGIN_CODE + "'\n" +
                "}\n" +
                "repositories {\n" +
                "    mavenLocal()\n" +
                "}\n" +
                "project.dependencies {\n" +
                "    implementation \"com.typesafe.scala-logging:scala-logging_2.13:3.9.2\"\n" +
                "}\n");
    }

    protected List<String> gradleArguments(String task) {
        List<String> arguments = new ArrayList<>();
        arguments.add("--stacktrace");
        String baselineVersion = baselineVersion();
        if (baselineVersion != null) {
            arguments.add("-Dpekko.grpc.baseline.version=" + baselineVersion);
        }
        arguments.add(task);
        return arguments;
    }

    protected BuildResult runGradle(GradleRunner runner) {
        return runGradle(runner, false);
    }

    protected BuildResult runGradle(GradleRunner runner, boolean shouldFail) {
        String previousBaselineVersion = System.getProperty("pekko.grpc.baseline.version");
        try {
            if (shouldFail) {
                return runner.buildAndFail();
            } else {
                return runner.build();
            }
        } finally {
            restoreSystemProperty("pekko.grpc.baseline.version", previousBaselineVersion);
        }
    }

    private void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private String baselineVersion() {
        String configuredBaselineVersion = System.getProperty("pekkoGrpcTest.baselineVersion");
        if (configuredBaselineVersion != null) {
            return configuredBaselineVersion;
        }

        String pluginVersion = System.getProperty("pekkoGrpcTest.pluginVersion");
        if (pluginVersion == null) {
            return null;
        }

        return pluginVersion.replaceFirst("-\\d{8}-\\d{4}-SNAPSHOT$", "-SNAPSHOT");
    }

    protected PekkoGrpcPluginExtension sampleSetup(Project project) throws IOException {
        return sampleSetup(project, "scala", "2.12");
    }

    protected PekkoGrpcPluginExtension sampleSetup(Project project, String plugin) throws IOException {
        return sampleSetup(project, plugin, "2.12");
    }

    protected PekkoGrpcPluginExtension sampleSetup(Project project, String plugin, String scala) throws IOException {
        ensureProjectDir();
        if ("scala".equals(plugin)) {
            File scalaDir = Files.createDirectories(projectDir.toPath().resolve("src/main/scala")).toFile();
            Files.writeString(new File(scalaDir, "test.scala").toPath(), "object PekkoGrpc");
        }
        project.getPluginManager().apply(PLUGIN_CODE);
        project.getDependencies().add("implementation",
                "com.typesafe.scala-logging:scala-logging_" + scala + ":3.9.2");
        return project.getExtensions().getByType(PekkoGrpcPluginExtension.class);
    }

    protected PekkoGrpcPluginExtension sampleSetupWithPlugin(Project project, Class<?> pluginClass, String scala)
            throws IOException {
        ensureProjectDir();
        File scalaDir = Files.createDirectories(projectDir.toPath().resolve("src/main/scala")).toFile();
        Files.writeString(new File(scalaDir, "test.scala").toPath(), "object PekkoGrpc");
        project.getPluginManager().apply(PLUGIN_CODE);
        project.getDependencies().add("implementation",
                "com.typesafe.scala-logging:scala-logging_" + scala + ":3.9.2");
        return project.getExtensions().getByType(PekkoGrpcPluginExtension.class);
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
