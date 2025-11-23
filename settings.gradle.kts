import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.fabricmc.net")
        maven("https://jitpack.io")
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
        buildFileName = "../../common.gradle.kts"
    }
}

include(":fabricWrapper")
