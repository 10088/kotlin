import com.github.jk1.tcdeps.KotlinScriptDslAdapter.tc
import com.github.jk1.tcdeps.KotlinScriptDslAdapter.teamcityServer

plugins {
    base
    id("com.github.jk1.tcdeps") version "0.18"
}

rootProject.apply {
    from(project.file("../../../gradle/cidrPluginProperties.gradle.kts"))
}

repositories {
    teamcityServer {
        setUrl("https://buildserver.labs.intellij.net")
        credentials {
            username = "guest"
            password = "guest"
        }
    }
}

val clionRepo: String by rootProject.extra
val clionVersion: String by rootProject.extra
val clionPlatformDepsOrJavaPluginDir: File by rootProject.extra
val clionUnscrambledJarDir: File by rootProject.extra
val clionUseJavaPlugin: Boolean by rootProject.extra

val appcodeRepo: String by rootProject.extra
val appcodeVersion: String by rootProject.extra
val appcodePlatformDepsOrJavaPluginDir: File by rootProject.extra
val appcodeUnscrambledJarDir: File by rootProject.extra
val appcodeUseJavaPlugin: Boolean by rootProject.extra

val clionUnscrambledJar: Configuration by configurations.creating
val clionPlatformDepsZip: Configuration by configurations.creating

val appcodeUnscrambledJar: Configuration by configurations.creating
val appcodePlatformDepsZip: Configuration by configurations.creating

dependencies {
    clionUnscrambledJar(tc("$clionRepo:$clionVersion:unscrambled/clion.jar"))
    clionPlatformDepsZip(tc("$clionRepo:$clionVersion:CL-plugins/${platformDepsArtifactName(clionUseJavaPlugin, clionVersion)}"))

    appcodeUnscrambledJar(tc("$appcodeRepo:$appcodeVersion:unscrambled/appcode.jar"))
    appcodePlatformDepsZip(tc("$appcodeRepo:$appcodeVersion:OC-plugins/${platformDepsArtifactName(appcodeUseJavaPlugin, appcodeVersion)}"))
}

val downloadCLionUnscrambledJar: Task by downloading(clionUnscrambledJar, clionUnscrambledJarDir)
val downloadCLionPlatformDeps: Task by downloading(
        clionPlatformDepsZip,
        clionPlatformDepsOrJavaPluginDir,
        pathRemap = { it.substringAfterLast('/') }
) { zipTree(it.singleFile) }

val downloadAppCodeUnscrambledJar: Task by downloading(appcodeUnscrambledJar, appcodeUnscrambledJarDir)
val downloadAppCodePlatformDeps: Task by downloading(
        appcodePlatformDepsZip,
        appcodePlatformDepsOrJavaPluginDir,
        pathRemap = { it.substringAfterLast('/') }
) { zipTree(it.singleFile) }

tasks["build"].dependsOn(
        downloadCLionUnscrambledJar,
        downloadCLionPlatformDeps,
        downloadAppCodeUnscrambledJar,
        downloadAppCodePlatformDeps
)

fun Project.downloading(
        sourceConfiguration: Configuration,
        targetDir: File,
        pathRemap: (String) -> String = { it },
        extractor: (Configuration) -> Any = { it }
) = tasks.creating {
    // don't re-check status of the artifact at the remote server if the artifact is already downloaded
    val isUpToDate = targetDir.isDirectory && targetDir.walkTopDown().firstOrNull { !it.isDirectory } != null
    outputs.upToDateWhen { isUpToDate }

    if (!isUpToDate) {
        doFirst {
            copy {
                from(extractor(sourceConfiguration))
                into(targetDir)
                includeEmptyDirs = false
                duplicatesStrategy = DuplicatesStrategy.FAIL
                eachFile {
                    path = pathRemap(path)
                }
            }
        }
    }
}

fun platformDepsArtifactName(useJavaPlugin: Boolean, productVersion: String): String =
        if (useJavaPlugin) "java.zip" else "kotlinNative-platformDeps-$productVersion.zip"
