import org.gradle.jvm.tasks.Jar
import java.net.URL

plugins {
    kotlin("jvm")
}

val ultimateTools: Map<String, Any> by rootProject.extensions
val enableTasksIfAtLeast: (Project, String, Int) -> Unit by ultimateTools
val enableTasksIfOsIsNot: (Project, List<String>) -> Unit by ultimateTools

val cidrPluginTools: Map<String, Any> by rootProject.extensions
val preparePluginXml: (Project, String, String, Boolean, String, Boolean) -> Copy by cidrPluginTools
val pluginJar: (Project, Configuration, List<Task>) -> Jar by cidrPluginTools
val patchedPlatformDepsJar: (Project, File) -> Zip by cidrPluginTools
val otherPlatformDepsJars: (Project, File) -> Task by cidrPluginTools
val packageCidrPlugin: (Project, String, File, List<Task>) -> Copy by cidrPluginTools
val zipCidrPlugin: (Project, Task, File) -> Zip by cidrPluginTools
val cidrUpdatePluginsXml: (Project, Task, String, File, URL) -> Task by cidrPluginTools

val appcodeVersion: String by rootProject.extra
val appcodeFriendlyVersion: String by rootProject.extra
val appcodeVersionStrict: Boolean by rootProject.extra
val appcodePlatformDepsDir: File by rootProject.extra
val appcodePluginDir: File by rootProject.extra
val appcodePluginVersionFull: String by rootProject.extra
val appcodePluginZipPath: File by rootProject.extra
val appcodeCustomPluginRepoUrl: URL by rootProject.extra
val appcodeUseJavaPlugin: Boolean by rootProject.extra

val cidrPlugin: Configuration by configurations.creating

dependencies {
    cidrPlugin(project(":kotlin-ultimate:prepare:cidr-plugin"))
    embedded(project(":kotlin-ultimate:ide:appcode-native")) { isTransitive = false }
}

val preparePluginXmlTask: Task = preparePluginXml(
        project,
        ":kotlin-ultimate:ide:appcode-native",
        appcodeVersion,
        appcodeVersionStrict,
        appcodePluginVersionFull,
        appcodeUseJavaPlugin
)

val pluginJarTask: Task = pluginJar(project, cidrPlugin, listOf(preparePluginXmlTask))

val jarTasks = if (appcodeUseJavaPlugin)
    listOf(pluginJarTask)
else {
    val patchedPlatformDepsJar: Task = patchedPlatformDepsJar(project, appcodePlatformDepsDir)
    val otherPlatformDepsJars: Task = otherPlatformDepsJars(project, appcodePlatformDepsDir)
    listOf(pluginJarTask, patchedPlatformDepsJar, otherPlatformDepsJars)
}

val appcodePluginTask: Task = packageCidrPlugin(
        project,
        ":kotlin-ultimate:ide:appcode-native",
        appcodePluginDir,
        jarTasks
)

val zipAppCodePluginTask: Task = zipCidrPlugin(project, appcodePluginTask, appcodePluginZipPath)

val appcodeUpdatePluginsXmlTask: Task = cidrUpdatePluginsXml(
        project,
        preparePluginXmlTask,
        appcodeFriendlyVersion,
        appcodePluginZipPath,
        appcodeCustomPluginRepoUrl
)

enableTasksIfAtLeast(project, appcodeVersion, 191)
enableTasksIfOsIsNot(project, listOf("Windows"))
