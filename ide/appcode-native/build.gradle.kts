import java.util.Locale

plugins {
    kotlin("jvm")
}

val ultimateTools: Map<String, Any> by rootProject.extensions
val ijProductBranch: (String) -> Int by ultimateTools
val disableBuildTasks: Project.(String) -> Unit by ultimateTools
val proprietaryRepositories: Project.() -> Unit by ultimateTools

val appcodeVersion: String by rootProject.extra
val appcodeUnscrambledJarDir: File by rootProject.extra

proprietaryRepositories()

dependencies {
    compile(project(":kotlin-ultimate:ide:common-cidr-native"))
    compileOnly(fileTree(appcodeUnscrambledJarDir) { include("**/*.jar") })
}

the<JavaPluginConvention>().sourceSets["main"].apply {
    java.setSrcDirs(listOf("src"))
    resources.setSrcDirs(listOf("resources"))
}

if (ijProductBranch(appcodeVersion) < 193)
    disableBuildTasks("Too old AppCode version: $appcodeVersion")
else
    System.getProperty("os.name")!!.toLowerCase(Locale.US).takeIf { "windows" in it }?.let {
        disableBuildTasks("Can't build AppCode plugin under Windows")
    }
