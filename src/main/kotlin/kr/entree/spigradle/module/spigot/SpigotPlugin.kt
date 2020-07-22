/*
 * Copyright (c) 2020 Spigradle contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kr.entree.spigradle.module.spigot

import groovy.lang.Closure
import kr.entree.spigradle.data.Load
import kr.entree.spigradle.data.SpigotDebug
import kr.entree.spigradle.data.SpigotRepositories
import kr.entree.spigradle.internal.applyToConfigure
import kr.entree.spigradle.internal.groovyExtension
import kr.entree.spigradle.internal.runConfigurations
import kr.entree.spigradle.internal.settings
import kr.entree.spigradle.kotlin.mockBukkit
import kr.entree.spigradle.module.common.applySpigradlePlugin
import kr.entree.spigradle.module.common.createRunConfigurations
import kr.entree.spigradle.module.common.registerDescGenTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.GradleTask
import org.jetbrains.gradle.ext.JarApplication

/**
 * The Spigot plugin that adds:
 * - [kr.entree.spigradle.module.common.YamlGenerate] task for the 'plugin.yml' generation.
 * - [kr.entree.spigradle.module.common.SubclassDetection] task for the main-class detection.
 * - Debug tasks for test your plugin.
 */
class SpigotPlugin : Plugin<Project> {
    companion object {
        const val DESC_GEN_TASK_NAME = "generateSpigotDescription"
        const val MAIN_DETECTION_TASK_NAME = "detectSpigotMain"
        const val EXTENSION_NAME = "spigot"
        const val DESC_FILE_NAME = "plugin.yml"
        const val PLUGIN_SUPER_CLASS = "org/bukkit/plugin/java/JavaPlugin"
        const val TASK_GROUP = "spigot"
    }

    private val Project.spigot get() = extensions.getByName<SpigotExtension>(EXTENSION_NAME)

    override fun apply(project: Project) {
        with(project) {
            applySpigradlePlugin()
            setupDefaultRepositories()
            setupDefaultDependencies()
            registerDescGenTask<SpigotExtension>(
                    EXTENSION_NAME,
                    DESC_GEN_TASK_NAME,
                    MAIN_DETECTION_TASK_NAME,
                    DESC_FILE_NAME,
                    PLUGIN_SUPER_CLASS
            )
            setupGroovyExtensions()
            setupSpigotDebugTasks()
            createSpigotRunConfiguration(spigot.debug)
            createPaperRunConfiguration(spigot.debug)
        }
    }

    private fun Project.setupDefaultRepositories() {
        SpigotRepositories.run {
            listOf(SPIGOT_MC, PAPER_MC)
        }.forEach {
            repositories.maven(it)
        }
    }

    private fun Project.setupDefaultDependencies() {
        val ext = dependencies.groovyExtension
        ext.set("mockBukkit", object : Closure<Any>(this, this) {
            fun doCall(vararg arguments: String) =
                    dependencies.mockBukkit(arguments.getOrNull(0), arguments.getOrNull(1))
        }) // Can be replaced by reflection to SpigotExtensionsKt
    }

    private fun Project.setupGroovyExtensions() {
        spigot.groovyExtension.apply {
            set("POST_WORLD", Load.POST_WORLD)
            set("POSTWORLD", Load.POST_WORLD)
            set("STARTUP", Load.STARTUP)
        }
    }

    private fun Project.setupSpigotDebugTasks() {
        val debugOption = spigot.debug
        // prepareSpigot: downloadBuildTools -> buildSpigot -> copySpigot
        // preparePlugin: copyArtifactJar -> copyClasspathPlugins
        // debugSpigot: preparePlugin -> prepareSpigot -> runSpigot
        // debugPaper: preparePlugin -> downloadPaperclip -> runSpigot(runPaper)
        with(SpigotDebugTask) {
            // Spigot
            val buildToolDownload = registerDownloadBuildTool(debugOption)
            val buildSpigot = registerBuildSpigot(debugOption).applyToConfigure {
                mustRunAfter(buildToolDownload)
            }
            val prepareSpigot = registerPrepareSpigot(debugOption).applyToConfigure {
                dependsOn(buildToolDownload, buildSpigot)
            }
            val build by tasks
            val preparePlugin = registerPrepareSpigotPlugin(spigot).applyToConfigure {
                dependsOn(build)
            }
            val acceptsEula = registerAcceptEula(debugOption)
            val runSpigot = registerRunSpigot(debugOption).applyToConfigure {
                mustRunAfter(preparePlugin)
                dependsOn(acceptsEula)
            }
            registerDebugRun("Spigot").applyToConfigure { // debugSpigot
                dependsOn(preparePlugin, prepareSpigot, runSpigot)
                runSpigot.get().mustRunAfter(prepareSpigot)
            }
            registerCleanSpigotBuild(debugOption)
            // Paper
            val paperClipDownload = registerDownloadPaper(debugOption)
            registerDebugRun("Paper").applyToConfigure { // debugPaper
                dependsOn(preparePlugin, paperClipDownload, runSpigot)
                runSpigot.get().mustRunAfter(paperClipDownload)
            }
        }
    }

    private fun Project.createSpigotRunConfiguration(debug: SpigotDebug) {
        createRunConfigurations("Spigot", debug)
        val idea: IdeaModel by extensions
        idea.project?.settings {
            runConfigurations {
                named("RunSpigot", JarApplication::class).configure {
                    beforeRun {
                        register("acceptEula", GradleTask::class) {
                            task = tasks.getByName("acceptSpigotEula")
                        }
                    }
                }
            }
        }
    }

    private fun Project.createPaperRunConfiguration(debug: SpigotDebug) {
        val idea: IdeaModel by extensions
        idea.project?.settings {
            runConfigurations {
                register("RunPaper", JarApplication::class) {
                    jarPath = debug.serverJar.absolutePath
                    workingDirectory = debug.serverDirectory.absolutePath
                    programParameters = debug.programArgs.joinToString(" ")
                    jvmArgs = debug.jvmArgs.joinToString(" ")
                    beforeRun {
                        register("downloadPaper", GradleTask::class) {
                            task = tasks.getByName("downloadPaper")
                        }
                        register("preparePlugins", GradleTask::class) {
                            task = tasks.getByName("prepareSpigotPlugins")
                        }
                        register("acceptsEula", GradleTask::class) {
                            task = tasks.getByName("acceptSpigotEula")
                        }
                    }
                }
            }
        }
    }
}