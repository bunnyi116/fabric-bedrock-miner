import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.fabricmc.net")
        maven("https://jitpack.io") {
            content { includeGroupAndSubgroups("com.github") }
        }
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.replaymod.preprocess" -> {
                    useModule("com.github.Fallen-Breath:preprocessor:${requested.version}")
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
val settings = JsonSlurper().parseText(file("settings.json").readText()) as Map<String, List<String>>

for (version in settings["versions"]!!) {
    include(":$version")
    project(":$version").apply {
        projectDir = file("versions/$version")
        buildFileName = if (parseMcVersionToNumber(version) > 260000) {
            "../../build.fabric.gradle.kts"
        } else {
            "../../build.fabric.remap.gradle.kts"
        }
    }
}

//include(":fabricWrapper")

fun parseMcVersionToNumber(mcVersionStr: String): Int {
    val cleanVersion = mcVersionStr.split("-")[0] // 去掉 -fabric/-pre/-rc 等后缀
        .replace(Regex("[^0-9.]"), "") // 移除所有非数字、非点的字符
    val versionParts = cleanVersion.split(".")
        .filter { it.isNotEmpty() } // 过滤空字符串（避免异常分割）
    val major = versionParts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = versionParts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}