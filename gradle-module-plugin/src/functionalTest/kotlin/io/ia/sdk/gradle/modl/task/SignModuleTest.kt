package io.ia.sdk.gradle.modl.task

import com.inductiveautomation.ignitionsdk.ZipMap
import io.ia.ignition.module.generator.ModuleGenerator
import io.ia.ignition.module.generator.api.GeneratorConfigBuilder
import io.ia.ignition.module.generator.api.GradleDsl
import io.ia.sdk.gradle.modl.BaseTest
import io.ia.sdk.gradle.modl.util.signedModuleName
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Ignore

class SignModuleTest : BaseTest() {
    companion object {
        const val PATH_KEY = "<FILEPATH>"
        // For a specific YubiKey 5; you may need to change this for another key
        // FIXME may need a different certFile + certPassword
        val PKCS11_HSM_SIGNING_PROPERTY_ENTRIES = """
            ignition.signing.certAlias=X.509 Certificate for Digital Signature
            ignition.signing.keystorePassword=123456
            ignition.signing.certFile=./certificate.pem
            ignition.signing.certPassword=password
            ignition.signing.pkcs11Cfg=./pkcs11-yk5-win.cfg
        """.trimIndent()
    }

    @Test
    fun `module built and signed successfully with gradle properties file`() {
        val parentDir: File = tempFolder.newFolder("module_built_and_signed_successfully")
        val moduleName = "I Was Signed"
        val signingResourcesDestination = parentDir.toPath().resolve("i-was-signed")

        prepareSigningTestResources(signingResourcesDestination)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(parentDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)

        val result = runTask(projectDir.toFile(), "signModule", "--stacktrace")

        val buildDir = projectDir.resolve("build")
        val signedFileName = signedModuleName(moduleName)

        val signedFilePath = "${buildDir.toAbsolutePath()}/$signedFileName"
        val signed = File(signedFilePath)

        // unzip and look for signatures file
        val zm = ZipMap(signed)
        val file = zm.get("signatures.properties")

        assertTrue(signed.exists(), "signed file exists")
        assertNotNull(file, "signatures.properties found in signed modl")
        assertTrue(result.output.toString().contains("SUCCESSFUL"))
    }

    @Test
    fun `module signed with cmdline flags`() {
        val parentDir: File = tempFolder.newFolder("module_signed_with_cmdline_flags")
        val moduleName = "I Was Signed"
        val signingResourcesDestination = parentDir.toPath().resolve("i-was-signed")

        val signResources = prepareSigningTestResources(signingResourcesDestination, false)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(parentDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)

        val taskArgs = listOf(
            ":signModule",
            "--keystoreFile=${signResources.keystore}",
            "--certFile=${signResources.certFile}",
            "--keystorePassword=password",
            "--certAlias=selfsigned",
            "--certPassword=password",
            "--stacktrace",
            "--info"
        )

        runTask(projectDir.toFile(), taskArgs)

        val buildDir = projectDir.resolve("build")
        val signedFileName = signedModuleName(moduleName)

        val signedFilePath = "${buildDir.toAbsolutePath()}/$signedFileName"
        val signed = File(signedFilePath)

        // unzip and look for signatures file
        val zm = ZipMap(signed)
        val file = zm.get("signatures.properties")

        assertTrue(signed.exists(), "signed file exists")
        assertNotNull(file, "signatures.properties found in signed modl")
    }

    @Test
    fun `module signing failed due to missing signing configuration properties`() {
        val name = "I Was Signed"
        val dirName = currentMethodName()
        val config = GeneratorConfigBuilder()
            .moduleName(name)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(tempFolder.newFolder(dirName).toPath())
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .build()

        val projectDir = ModuleGenerator.generate(config)
        var result: BuildResult? = null
        var msg: String = ""
        try {
            result = runTask(projectDir.toFile(), listOf("signModule", "--certAlias=something"))
        } catch (e: Exception) {
            msg = e.message.toString()
        }

        val expected = "Some problems were found with the configuration of task ':signModule' (type 'SignModule')."

        val output: String? = result?.output
        assertNull(output, "Should have received output from build attempt")
        assertNotNull(msg, "should have exception message")
        assertTrue(msg.contains(expected), "Execution failed due to missing sign props")
    }

    @Test
    fun `module failed with missing keystore pw flags`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)
        val signResources = prepareSigningTestResources(projectDir, false)

        val taskArgs = listOf(
            ":signModule",
            "--keystoreFile=${signResources.keystore}",
            "--certFile=${signResources.certFile}",
            "--certAlias=selfsigned",
            "--certPassword=password"
        )
        var result: BuildResult? = null
        var ex: Exception? = null
        try {
            result = runTask(projectDir.toFile(), taskArgs)
        } catch (e: Exception) {
            ex = e
        }

        val expectedError = Regex(
            """> Task :signModule FAILED\RRequired keystore password not found.  Specify via flag """ +
                "'--keystorePassword=<value>', or in gradle.properties file as 'ignition.signing.keystorePassword=<value>'"
        )
        assertNull(result, "build output will be null due to failure")
        assertNotNull(ex, "Exception should be caught and not null")
        assertNotNull(ex.message, "Exception should have message")
        assertTrue(ex.message.toString().contains(expectedError), "expected error detected.")
    }

    @Test
    fun `module failed with missing cert pw flags`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)
        val signResources = prepareSigningTestResources(projectDir, false)

        val taskArgs = listOf(
            ":signModule",
            "--keystoreFile=${signResources.keystore}",
            "--certFile=${signResources.certFile}",
            "--certAlias=selfsigned",
            "--certPassword=password"
        )
        var result: BuildResult? = null
        var ex: Exception? = null
        try {
            result = runTask(projectDir.toFile(), taskArgs)
        } catch (e: Exception) {
            ex = e
        }

        val expectedError = Regex(
            """> Task :signModule FAILED\RRequired keystore password not found.  Specify via flag """ +
                "'--keystorePassword=<value>', or in gradle.properties file as 'ignition.signing.keystorePassword=<value>'"
        )
        assertNull(result, "build output will be null due to failure")
        assertNotNull(ex, "Exception should be caught and not null")
        assertNotNull(ex.message, "Exception should have message")
        assertTrue(ex.message.toString().contains(expectedError), "expected error detected.")
    }

    @Test
    // @Tag("IGN-7871")
    fun `module failed - file and pkcs11 keystore in gradle properties`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)

        // These calls yield a gradle.properties file with both file- and
        // PKCS#11-based keystore config--a conflict.
        val signingResourcesDestination =
            workingDir.toPath().resolve("i-was-signed")
        writeResourceFiles(
            signingResourcesDestination,
            listOf("certificate.pem", "keystore.jks", "pkcs11.cfg")
        )
        writeSigningCredentials(
            signingResourcesDestination,
            "$PKCS11_PROPERTY_ENTRIES\n$KEYSTORE_PROPERTY_ENTRIES"
        )

        val result: BuildResult =
            runTaskAndFail(
                projectDir.toFile(),
                listOf("signModule", "--stacktrace")
            )

        val task = result.task(":signModule")
        assertEquals(task?.outcome, TaskOutcome.FAILED)
        assertContains(
            result.output,
            "'--keystoreFile' flag/'ignition.signing.keystoreFile' property " +
                "in gradle.properties or " +
                "'--pkcs11Cfg' flag/'ignition.signing.pkcs11Cfg' property " +
                "in gradle.properties but not both"
        )
        assertContains(result.output, "InvalidUserDataException")
    }

    @Test
    //@Tag("IGN-7871")
    fun `module failed - file keystore in gradle properties, pkcs11 keystore on cmdline`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)

        // Write file-based keystore + specify in gradle.properties.
        val signingResourcesDestination =
            workingDir.toPath().resolve("i-was-signed")
        prepareSigningTestResources(signingResourcesDestination)

        // Also write PKCS#11 HSM config, which by itself is OK.
        val pkcs11CfgPath = writeResourceFiles(
            signingResourcesDestination, listOf("pkcs11.cfg")
        ).first()

        // But specifying that file via option suggests there is an HSM
        // keystore, which conflicts with the file-based keystore.
        val taskArgs = listOf(
            "signModule",
            "--pkcs11Cfg=$pkcs11CfgPath",
            "--stacktrace",
        )
        val result: BuildResult =
            runTaskAndFail(projectDir.toFile(), taskArgs)

        val task = result.task(":signModule")
        assertEquals(task?.outcome, TaskOutcome.FAILED)
        assertContains(
            result.output,
            "'--keystoreFile' flag/'ignition.signing.keystoreFile' property " +
                "in gradle.properties or " +
                "'--pkcs11Cfg' flag/'ignition.signing.pkcs11Cfg' property " +
                "in gradle.properties but not both"
        )
        assertContains(result.output, "InvalidUserDataException")
    }

    @Test
    //@Tag("IGN-7871")
    fun `module failed - file keystore on cmdline, pkcs11 keystore in gradle properties`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)

        // Write PKCS#11 HSM config file + specify in gradle.properties.
        val signingResourcesDestination =
            workingDir.toPath().resolve("i-was-signed")
        preparePKCS11SigningTestResources(signingResourcesDestination)

        // Also write file-based keystore, which by itself is OK.
        val ksPath = writeResourceFiles(
            signingResourcesDestination, listOf("keystore.jks")
        ).first()

        // But specifying that file suggests there is a file keystore,
        // which conflicts with the HSM keystore.
        val taskArgs = listOf(
            "signModule",
            "--keystoreFile=$ksPath",
            "--stacktrace",
        )
        val result: BuildResult =
            runTaskAndFail(projectDir.toFile(), taskArgs)

        val task = result.task(":signModule")
        assertEquals(task?.outcome, TaskOutcome.FAILED)
        assertContains(
            result.output,
            "'--keystoreFile' flag/'ignition.signing.keystoreFile' property " +
                "in gradle.properties or " +
                "'--pkcs11Cfg' flag/'ignition.signing.pkcs11Cfg' property " +
                "in gradle.properties but not both"
        )
        assertContains(result.output, "InvalidUserDataException")
    }

    @Test
    //@Tag("IGN-7871")
    fun `module failed - file and pkcs11 keystore on cmdline`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                    id("io.ia.sdk.modl")
                """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)

        // Write PKCS#11 HSM config file + write file-based keystore, which
        // by itself is OK.
        val signingResourcesDestination =
            workingDir.toPath().resolve("i-was-signed")
        val (ksPath, pkcs11Cfg) = writeResourceFiles(
            signingResourcesDestination,
            listOf("keystore.jks", "pkcs11.cfg", "certificate.pem")
        )

        // But specifying that file suggests there is a file keystore,
        // which conflicts with the HSM keystore.
        val taskArgs = listOf(
            "signModule",
            "--keystoreFile=$ksPath",
            "--pkcs11Cfg=$pkcs11Cfg",
            "--keystorePassword=password",
            "--certAlias=selfsigned",
            "--certFile=./certificate.pem",
            "--certPassword=password",
            "--stacktrace",
        )
        val result: BuildResult =
            runTaskAndFail(projectDir.toFile(), taskArgs)

        val task = result.task(":signModule")
        assertEquals(task?.outcome, TaskOutcome.FAILED)
        assertContains(
            result.output,
            "'--keystoreFile' flag/'ignition.signing.keystoreFile' property " +
                "in gradle.properties or " +
                "'--pkcs11Cfg' flag/'ignition.signing.pkcs11Cfg' property " +
                "in gradle.properties but not both"
        )
        assertContains(result.output, "InvalidUserDataException")
    }
/*
    @Test
    //@Tag("IGN-7871")
    fun `module signed with pkcs11 keystore in gradle properties`() {
        // FIXME mock the hw key?
        // FIXME write an integration test w/ actual hw key?
    }

    @Test
    //@Tag("IGN-7871")
    fun `module signed with pkcs11 keystore on cmdline`() {
        // FIXME mock the hw key?
        // FIXME write an integration test w/ actual hw key?
    }
*/
    // This is a test with an actual PKCS#11-compliant YubiKey 5, on Windows.
    // As such it is typically set to @Ignore.
    @Test
    @Ignore
    //@Tag("integration")
    //@Tag("IGN-7871")
    fun `module signed with physical pkcs11 HSM in gradle properties`() {
        val dirName = currentMethodName()
        val moduleName = "I Was Signed"
        val workingDir: File = tempFolder.newFolder(dirName)

        val config = GeneratorConfigBuilder()
            .moduleName(moduleName)
            .scopes("GCD")
            .packageName("check.my.signage")
            .parentDir(workingDir.toPath())
            .debugPluginConfig(true)
            .allowUnsignedModules(false)
            .settingsDsl(GradleDsl.GROOVY)
            .rootPluginConfig(
                """
                            id("io.ia.sdk.modl")
                        """.trimIndent()
            )
            .build()

        val projectDir = ModuleGenerator.generate(config)
        // Write PKCS#11 config file and and cert file, and specify them in
        // gradle.properties.
        val signingResourcesDestination =
            workingDir.toPath().resolve("i-was-signed")
        writeResourceFiles(
            signingResourcesDestination,
            listOf("certificate.pem", "pkcs11-yk5-win.cfg")
        )
        writeSigningCredentials(
            signingResourcesDestination,
            PKCS11_HSM_SIGNING_PROPERTY_ENTRIES
        )

        val result: BuildResult = runTask(projectDir.toFile(), "signModule")

        val task = result.task(":signModule")
        assertEquals(task?.outcome, TaskOutcome.SUCCESS)

        val buildDir = projectDir.resolve("build")
        val signedFileName = signedModuleName(moduleName)

        val signed = File("${buildDir.toAbsolutePath()}/$signedFileName")

        // unzip and look for signatures file
        val zm = ZipMap(signed)
        val file = zm.get("signatures.properties")

        assertTrue(signed.exists(), "signed file exists")
        assertNotNull(file, "signatures.properties found in signed modl")
        // FIXME write an integration test w/ actual hw key?
    }

    /*
    // This is a test with an actual PKCS#11-compliant YubiKey 5, on Windows.
    // As such it is typically set to @Ignore.
    @Test
    //@Ignore
    //@Tag("integration")
    //@Tag("IGN-7871")
    fun `module signed with physical pkcs11 HSM on cmdline`() {
        // FIXME mock the hw key?
        // FIXME write an integration test w/ actual hw key?
    }
 */

    // FIXME refactor the repetitive project build LOCs
}
