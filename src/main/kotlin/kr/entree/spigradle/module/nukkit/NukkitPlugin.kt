package kr.entree.spigradle.module.nukkit

import kr.entree.spigradle.data.Load
import kr.entree.spigradle.data.NukkitRepositories
import kr.entree.spigradle.internal.Groovies
import kr.entree.spigradle.internal.applyToConfigure
import kr.entree.spigradle.module.common.applySpigradlePlugin
import kr.entree.spigradle.module.common.registerDescGenTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.provideDelegate

/**
 * Created by JunHyung Lim on 2020-04-28
 */
class NukkitPlugin : Plugin<Project> {
    companion object {
        const val DESC_GEN_TASK_NAME = "generateNukkitDescription"
        const val MAIN_DETECTION_TASK_NAME = "detectNukkitMain"
        const val EXTENSION_NAME = "nukkit"
        const val DESC_FILE_NAME = "plugin.yml"
        const val PLUGIN_SUPER_CLASS = "cn/nukkit/plugin/PluginBase"
    }

    override fun apply(project: Project) {
        with(project) {
            applySpigradlePlugin()
            setupDefaultRepositories()
            registerDescGenTask<NukkitExtension>(
                    EXTENSION_NAME,
                    DESC_GEN_TASK_NAME,
                    MAIN_DETECTION_TASK_NAME,
                    DESC_FILE_NAME,
                    PLUGIN_SUPER_CLASS
            )
            setupGroovyExtensions()
            setupNukkitDebugTasks()
        }
    }

    private fun Project.setupDefaultRepositories() {
        repositories.maven(NukkitRepositories.NUKKIT_X)
    }

    private fun Project.setupGroovyExtensions() {
        Groovies.getExtensionFrom(extensions.getByName(EXTENSION_NAME)).apply {
            set("POST_WORLD", Load.POST_WORLD)
            set("STARTUP", Load.STARTUP)
        }
    }

    private fun Project.setupNukkitDebugTasks() {
        val nukkit: NukkitExtension by extensions
        val debug = nukkit.debug
        val build by tasks
        with(NukkitDebugTask) {
            val nukkitDownload = registerDownloadNukkit(debug)
            val runNukkit = registerRunNukkit(debug)
            val preparePlugin = registerPrepareNukkitPlugins(nukkit).applyToConfigure {
                dependsOn(build)
            }
            registerDebugNukkit().applyToConfigure {
                dependsOn(preparePlugin, nukkitDownload, runNukkit)
                runNukkit.get().mustRunAfter(nukkitDownload)
            }
        }
    }
}