package unit

import org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension
import helper.BaseSpec
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import static org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension.GRPC_VERSION
import static org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension.PROTOC_PLUGIN_SCALA_VERSION

class DependenciesSpec extends BaseSpec {

    static final String PROTOC_PLUGIN_CODEGEN = "pekkoGrpc"

    def checkCodegen(Dependency d, PekkoGrpcPluginExtension ext) {
        assert d.group == "com.lightbend.akka.grpc"
        assert d.name == "pekko-grpc-codegen_${PROTOC_PLUGIN_SCALA_VERSION}"
        assert d.version == ext.pluginVersion
        true
    }

    static final String PROTOC_PLUGIN_SCALAPB = "scalapb"

    def checkScalapb(Dependency d, PekkoGrpcPluginExtension ext) {
        assert d.group == "com.lightbend.akka.grpc"
        assert d.name == "pekko-grpc-scalapb-protoc-plugin_${PROTOC_PLUGIN_SCALA_VERSION}"
        assert d.version == ext.pluginVersion
        true
    }

    Project project

    def setup() {
        createBuildFolder()
        project = ProjectBuilder.builder().withProjectDir(projectDir.root).build()
    }

    def "should add only protoc codegen plugin for java"() {
        given:
        def pekkoGrpcExt = sampleSetup('java')
        when:
        project.evaluate()
        then:
        Map<String, Dependency> plugins = project.protobuf.tools.plugins.collectEntries { [(it.name): project.dependencies.create(it.artifact)] }
        plugins.keySet().sort() == [PROTOC_PLUGIN_CODEGEN]
        and:
        checkCodegen(plugins[(PROTOC_PLUGIN_CODEGEN)], pekkoGrpcExt)
        and:
        def cfg = project.configurations.getByName("protobufToolsLocator_$PROTOC_PLUGIN_CODEGEN")
        cfg.dependencies.size() == 1
        checkCodegen(cfg.dependencies.first(), pekkoGrpcExt)
    }

    def "should add protoc codegen and scalapb plugins for scala"() {
        given:
        def pekkoGrpcExt = sampleSetup('scala')
        when:
        project.evaluate()
        then:
        Map<String, Dependency> plugins = project.protobuf.tools.plugins.collectEntries { [(it.name): project.dependencies.create(it.artifact)] }
        plugins.keySet().sort() == [PROTOC_PLUGIN_CODEGEN, PROTOC_PLUGIN_SCALAPB]
        and:
        checkCodegen(plugins[(PROTOC_PLUGIN_CODEGEN)], pekkoGrpcExt)
        checkScalapb(plugins[(PROTOC_PLUGIN_SCALAPB)], pekkoGrpcExt)
        and:
        def cfg = project.configurations.getByName("protobufToolsLocator_$PROTOC_PLUGIN_CODEGEN")
        cfg.dependencies.size() == 1
        checkCodegen(cfg.dependencies.first(), pekkoGrpcExt)
        and:
        def cfg2 = project.configurations.getByName("protobufToolsLocator_$PROTOC_PLUGIN_SCALAPB")
        cfg2.dependencies.size() == 1
        checkScalapb(cfg2.dependencies.first(), pekkoGrpcExt)
    }

    @Unroll
    def "should autodetected scala version for pekko-grpc-runtime #plugin #scala"() {
        given:
        def pekkoGrpcExt = sampleSetup(plugin, scala)
        when:
        project.evaluate()
        then: "added to configuration"
        def deps = project.configurations.implementation.dependencies
        deps.any { it.name == "pekko-grpc-runtime_$scala" && it.version == pekkoGrpcExt.pluginVersion }
        deps.any { it.name == "grpc-stub" && it.version == GRPC_VERSION }
        where:
        plugin || scala
        "java"  | "2.12"
        "java"  | "2.13"
        "scala" | "2.12"
        "scala" | "2.13"
    }
}
