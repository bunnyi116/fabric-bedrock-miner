import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import propStr
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Project.propOrNull(key: String) = findProperty(key)
fun Project.prop(key: String) = propOrNull(key) ?: throw GradleException("buildSrc: 属性 $key 未配置/值为空")

fun Project.propStrOrNull(key: String): String? = propOrNull(key)?.toString()
fun Project.propStr(key: String): String = propStrOrNull(key)
    ?: throw GradleException("buildSrc: 属性 $key 未配置/值为空，或无法转换为字符串")

val Project.mcVersion get() = propOrNull("mcVersion") as Int

val Project.modId get() = propStr("mod_id")
val Project.modWrapperId get() = propStr("mod_wrapper_id")
val Project.modName get() = propStr("mod_name")
val Project.modMavenGroup get() = propStr("mod_maven_group")
val Project.modVersion get() = propStr("mod_version")
val Project.modArchivesBaseName get() = propStr("mod_archives_base_name")

val Project.modDescription get() = propStrOrNull("mod_description")
val Project.modHomepage get() = propStrOrNull("mod_homepage")
val Project.modLicense get() = propStrOrNull("mod_license")
val Project.modSources get() = propStrOrNull("mod_sources")

val Project.loaderVersion get() = propStr("loader_version")
val Project.minecraftDependency get() = propStr("minecraft_dependency")
val Project.minecraftVersion get() = propStr("minecraft_version")
val Project.fabricApiVersion get() = propStr("fabric_api_version")

val Project.lombok_version get() = propStr("lombok_version")

val Project.modmenuVersion get() = propStr("modmenu")
val Project.clothConfigVersion get() =propStr("cloth-config")

val Project.javaVersion
    get() = when {
        mcVersion >= 260000 -> JavaVersion.VERSION_25   // 26+          需要 Java 25
        mcVersion >= 12005 -> JavaVersion.VERSION_21    // 1.20.5+      需要 Java 21
        mcVersion >= 11800 -> JavaVersion.VERSION_17    // 1.18-1.20.4  需要 Java 17
        mcVersion >= 11700 -> JavaVersion.VERSION_16    // 1.17.x       需要 Java 16
        else -> JavaVersion.VERSION_1_8                 // 1.16.x 及以下使用 Java 8
    }
val Project.mixinCompatibilityLevel get() = "JAVA_${javaVersion.majorVersion.toInt()}"