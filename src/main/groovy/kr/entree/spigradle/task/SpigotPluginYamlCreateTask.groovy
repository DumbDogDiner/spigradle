package kr.entree.spigradle.task

import kr.entree.spigradle.extension.PluginAttributes
import kr.entree.spigradle.util.Version
import kr.entree.spigradle.util.inspector.ByteInspector
import kr.entree.spigradle.util.inspector.InspectorResult
import kr.entree.spigradle.util.mapper.Mapper
import kr.entree.spigradle.util.yaml.SpigradleRepresenter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * Created by JunHyung Lim on 2019-12-12
 */
class SpigotPluginYamlCreateTask extends DefaultTask {
    @Input
    PluginAttributes attributes
    @Input
    String encoding = 'UTF-8'
    @Input
    Yaml yaml = createYaml()

    @TaskAction
    def createPluginYaml() {
        def file = new File(temporaryDir, 'plugin.yml')
        file.newWriter(encoding).withCloseable {
            writePluginYaml(it)
        }
        project.tasks.withType(Jar) {
            it.from file
        }
    }

    def writePluginYaml(Writer writer) {
        def inspected = new ByteInspector(project).doInspect()
        yaml.dump(createMap(inspected), writer)
    }

    def createMap(InspectorResult inspected) {
        attributes.with {
            main = main ?: inspected.mainClass
            name = name ?: project.name
            version = version ?: project.version
        }
        def yamlMap = Mapper.mapping(attributes, true) as Map<String, Object>
        validateYamlMap(yamlMap)
        return yamlMap
    }

    static def validateYamlMap(Map<String, Object> yamlMap) {
        if (yamlMap.main == null) {
            throw new IllegalArgumentException(
                    """\
                        Spigradle couldn\'t find a main class automatically.
                        Please set a 'main' property in spigot {} block in build.gradle\
                    """.stripIndent()
            )
        }
        if (yamlMap.'api-version' != null) {
            def rawApiVersion = yamlMap.'api-version'.toString()
            Version.parse(rawApiVersion).with {
                if (major < 1 || minor < 13) {
                    throw new IllegalArgumentException("""Invalid api-version configured:'$rawApiVersion'\nIt should be 1.13 or higher!""")
                }
                if (major == 1 && (13..15).contains(minor) && patch != null) {
                    throw new IllegalArgumentException("""Invalid api-version configured:'$rawApiVersion'\nValid format: $major.$minor""")
                }
            }
        }
    }

    static Yaml createYaml() {
        def options = new DumperOptions()
        options.with {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indicatorIndent = indent - 1
        }
        return new Yaml(new SpigradleRepresenter(), options)
    }
}
