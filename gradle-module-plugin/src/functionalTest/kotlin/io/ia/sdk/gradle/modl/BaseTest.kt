package io.ia.sdk.gradle.modl

import io.ia.ignition.module.generator.api.GeneratorConfig
import io.ia.ignition.module.generator.api.GeneratorConfigBuilder
import io.ia.sdk.gradle.modl.api.Constants
import io.ia.sdk.gradle.modl.api.Constants.ALIAS_FLAG
import io.ia.sdk.gradle.modl.api.Constants.CERT_FILE_FLAG
import io.ia.sdk.gradle.modl.api.Constants.CERT_PW_FLAG
import io.ia.sdk.gradle.modl.api.Constants.KEYSTORE_FILE_FLAG
import io.ia.sdk.gradle.modl.api.Constants.KEYSTORE_PW_FLAG
import io.ia.sdk.gradle.modl.api.Constants.PKCS11_CFG_FLAG
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class SigningResources(
    val keystore: Path?,
    val certFile: Path?,
    val pkcs11Cfg: Path?,
    val signPropFile: Path?
)

open class BaseTest {
    companion object {
        val CLIENT_DEP = "// add client scoped dependencies here"
        val DESIGNER_DEP = "// add designer scoped dependencies here"
        val GW_DEP = "// add gateway scoped dependencies here"
        val COMMON_DEP = "// add common scoped dependencies here"
        val SIGNING_PROPERTY_ENTRIES = """
            ${Constants.SIGNING_PROPERTIES[ALIAS_FLAG]}=selfsigned
            ${Constants.SIGNING_PROPERTIES[KEYSTORE_PW_FLAG]}=password
            ${Constants.SIGNING_PROPERTIES[CERT_FILE_FLAG]}=./certificate.pem
            ${Constants.SIGNING_PROPERTIES[CERT_PW_FLAG]}=password
        """.trimIndent()
        val KEYSTORE_PROPERTY_ENTRIES = """
            ${Constants.SIGNING_PROPERTIES[KEYSTORE_FILE_FLAG]}=./keystore.jks
        """.trimIndent()
        val PKCS11_PROPERTY_ENTRIES = """
            ${Constants.SIGNING_PROPERTIES[PKCS11_CFG_FLAG]}=./pkcs11.cfg
        """.trimIndent()
    }

    @Rule @JvmField
    val tempFolder = TemporaryFolder()

    /**
     * Returns the name of the method/function that calls this.
     */
    protected fun currentMethodName(): String {
        val name = Thread.currentThread().stackTrace[2].methodName.replace(" ", "")
        return name
    }

    // writes a gradle.properties file containing the signing credentials needed for signing a module using test
    // resources
    protected fun writeSigningCredentials(
        targetDirectory: Path,
        keystoreProps: String
    ): Path {
        val gradleProps: Path = targetDirectory.resolve("gradle.properties")
        gradleProps.toFile().let { propsFl ->
            StringBuilder().let { props ->

                // add a trailing EOL if necessary, then common props
                if (
                    propsFl.exists() &&
                        !propsFl.readText().matches(Regex("""\R$"""))
                ) {
                    // FIXME zap after regression test troubleshoot
                    println("[$propsFl] Lacks EOF EOL, adding one now ;-)")
                    props.append("\n")
                }
                props.append(SIGNING_PROPERTY_ENTRIES)
                props.append("\n")

                // this could be file-based or PKCS#11 HSM-based keystore props
                props.append(keystoreProps)

                // FIXME zap after regression test troubleshoot
                println("props:\n${props.toString()}")

                propsFl.appendText(props.toString())
            }
        }

        return gradleProps
    }

    fun moduleDirName(moduleName: String): String {
        return moduleName.replace(" ", "-").lowercase()
    }

    // file-based keystore
    // returns the path to the 'gradle.properties' file
    protected fun prepareSigningTestResources(targetDirectory: Path, withPropFile: Boolean = true): SigningResources {
        val paths = writeResourceFiles(
            targetDirectory,
            listOf("certificate.pem", "keystore.jks")
        )

        val propFile =
            if (withPropFile)
                writeSigningCredentials(targetDirectory, KEYSTORE_PROPERTY_ENTRIES)
            else null
        return SigningResources(
            certFile = paths[0] as Path,
            keystore = paths[1] as Path,
            pkcs11Cfg = null,
            signPropFile = propFile,
        )
    }

    // PKCS#11 HSM-based keystore
    // returns the path to the 'gradle.properties' file
    protected fun preparePKCS11SigningTestResources(
        targetDirectory: Path,
        withPropFile: Boolean = true
    ): SigningResources {
        val paths = writeResourceFiles(
            targetDirectory,
            // future feature: cert also on the HSM
            listOf("certificate.pem", "pkcs11.cfg")
        )

        val propFile =
            if (withPropFile)
                writeSigningCredentials(targetDirectory, PKCS11_PROPERTY_ENTRIES)
            else null
        return SigningResources(
            certFile = paths[0] as Path,
            keystore = null,
            pkcs11Cfg = paths[1] as Path,
            signPropFile = propFile,
        )
    }

    private fun writeResourceFiles(targetDir: Path, resources: List<String>): List<Path?> {
        Files.createDirectories(targetDir)

        return resources.map { resource ->
            ClassLoader.getSystemResourceAsStream("certs/$resource").let { inputStream ->
                if (inputStream == null) {
                    throw Exception("Failed to read test resource 'certs/$resource")
                }
                val writeTo = targetDir.resolve(resource)
                inputStream.copyTo(writeTo.toFile().outputStream(), 1024)

                writeTo
            }
        }
    }

    open fun config(name: String, scope: String, pkg: String): GeneratorConfig {
        val testDir = listOf(
            name.replace(" ", "_"),
            scope,
            pkg.replace(".", "_")
        ).joinToString("")

        return GeneratorConfigBuilder()
            .moduleName(name)
            .scopes(scope)
            .packageName(pkg)
            .parentDir(tempFolder.newFolder(testDir).toPath())
            .build()
    }

    open fun runTask(projectDir: File, taskArgs: List<String>): BuildResult {
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments(taskArgs)
        runner.withProjectDir(projectDir)
        return runner.build()
    }

    open fun runTask(projectDir: File, task: String): BuildResult {
        return runTask(projectDir, listOf(task))
    }
}
