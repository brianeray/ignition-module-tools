package io.ia.sdk.gradle.modl.task

import io.ia.ignition.module.generator.ModuleGenerator
import io.ia.ignition.module.generator.api.GeneratorConfigBuilder
import io.ia.ignition.module.generator.api.GradleDsl
import io.ia.sdk.gradle.modl.BaseTest
import io.ia.sdk.gradle.modl.util.collapseXmlToOneLine
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class WriteModuleXmlTest : BaseTest() {
    companion object {
        const val MODULE_NAME = "ModuleXmlTest"
        const val PACKAGE_NAME = "module.xml.test"
    }

    @Test
    // @Tag("IGN-9137")
    fun `optional module dependencies marked as not required`() {
        val dirName = currentMethodName()
        val replacements = mapOf(
            "optionalModuledependencies = [ : ]" to
                "optionalModuledependencies = ['io.ia.mod': 'GCD']",
        )

        val projectDir = generateModule(
            tempFolder.newFolder(dirName),
            replacements,
        )

        val result: BuildResult = runTask(
            projectDir.toFile(),
            listOf("writeModuleXml", "--stackTrace")
        )

        val task = result.task(":writeModuleXml")
        assertEquals(task?.outcome, TaskOutcome.SUCCESS)

        // We could do real XML parsing here but this is just a test,
        // quick-and-dirty should be fine.
        val oneLineXml = collapseXmlToOneLine(
            projectDir.resolve("build/moduleContent/module.xml").readText()
        )
        assertContains(
            oneLineXml,
            """<depends scope="GCD" required="false">io.ia.mod"""
        )
        assertEquals(
            Regex("<depends").findAll(oneLineXml).toList().size,
            1
        )
    }

/*
    @Test
    // @Tag("IGN-9137")
    fun `required module dependencies marked as required`() {
        // FIXME fill in the test
    }
 */

/*
    @Test
    // @Tag("IGN-9137")
    fun `legacy module dependencies not marked at all for requiredness`() {
        // FIXME fill in the test
    }
 */

    private fun generateModule(
        projDir: File,
        replacements: Map<String,String> = mapOf(),
    ): Path {
        val config = GeneratorConfigBuilder()
            .moduleName(MODULE_NAME)
            .scopes("GCD")
            .packageName(PACKAGE_NAME)
            .parentDir(projDir.toPath())
            .customReplacements(replacements)
            .debugPluginConfig(true)
            .allowUnsignedModules(true)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        return ModuleGenerator.generate(config)
    }
}
