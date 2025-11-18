plugins {
    id("maven-publish")
    id("com.github.hierynomus.license") version "0.16.1" apply false
    id("fabric-loom") version "1.13-SNAPSHOT" apply false

    // https://github.com/ReplayMod/preprocessor
    // https://github.com/Fallen-Breath/preprocessor
    id("com.replaymod.preprocess") version "d452ef7612"

    // https://github.com/Fallen-Breath/yamlang
    id("me.fallenbreath.yamlang") version "1.5.0" apply false
}

preprocess {
    strictExtraMappings = false

//    val mc1_16_05 = createNode("1.16.5", 1_16_05, "")
//    val mc1_17_00 = createNode("1.17", 1_17_00, "")
//    val mc1_17_01 = createNode("1.17.1", 1_17_01, "")
//    val mc1_18_00 = createNode("1.18", 1_18_00, "")
//    val mc1_18_01 = createNode("1.18.1", 1_18_01, "")
//    val mc1_18_02 = createNode("1.18.2", 1_18_02, "")
    val mc1_19_00 = createNode("1.19", 1_19_00, "")
    val mc1_19_01 = createNode("1.19.1", 1_19_01, "")
    val mc1_19_02 = createNode("1.19.2", 1_19_02, "")
    val mc1_19_03 = createNode("1.19.3", 1_19_03, "")
    val mc1_19_04 = createNode("1.19.4", 1_19_04, "")
    val mc1_20_00 = createNode("1.20", 1_20_00, "")
    val mc1_20_01 = createNode("1.20.1", 1_20_01, "")
    val mc1_20_02 = createNode("1.20.2", 1_20_02, "")
    val mc1_20_03 = createNode("1.20.3", 1_20_03, "")
    val mc1_20_04 = createNode("1.20.4", 1_20_04, "")
    val mc1_20_05 = createNode("1.20.5", 1_20_05, "")
    val mc1_20_06 = createNode("1.20.6", 1_20_06, "")
    val mc1_21_00 = createNode("1.21", 1_21_00, "")
    val mc1_21_01 = createNode("1.21.1", 1_21_01, "")
    val mc1_21_02 = createNode("1.21.2", 1_21_02, "")
    val mc1_21_03 = createNode("1.21.3", 1_21_03, "")
    val mc1_21_04 = createNode("1.21.4", 1_21_04, "")
    val mc1_21_05 = createNode("1.21.5", 1_21_05, "")
    val mc1_21_06 = createNode("1.21.6", 1_21_06, "")
    val mc1_21_07 = createNode("1.21.7", 1_21_07, "")
    val mc1_21_08 = createNode("1.21.8", 1_21_08, "")
    val mc1_21_09 = createNode("1.21.9", 1_21_09, "")
    val mc1_21_10 = createNode("1.21.10", 1_21_10, "")

    mc1_21_10.link(mc1_21_09, null)
    mc1_21_09.link(mc1_21_08, file("versions/mapping-1.21.8-1.21.9.txt"))
    mc1_21_08.link(mc1_21_07, null)
    mc1_21_07.link(mc1_21_06, null)
    mc1_21_06.link(mc1_21_05, file("versions/mapping-1.21.6-1.21.8.txt"))
    mc1_21_05.link(mc1_21_04, null)
    mc1_21_04.link(mc1_21_03, null)
    mc1_21_03.link(mc1_21_02, null)
    mc1_21_02.link(mc1_21_01, null)
    mc1_21_01.link(mc1_21_00, null)
    mc1_21_00.link(mc1_20_06, null)
    mc1_20_06.link(mc1_20_05, null)
    mc1_20_05.link(mc1_20_04, null)
    mc1_20_04.link(mc1_20_03, null)
    mc1_20_03.link(mc1_20_02, null)
    mc1_20_02.link(mc1_20_01, null)
    mc1_20_01.link(mc1_20_00, null)
    mc1_20_00.link(mc1_19_04, null)
    mc1_19_04.link(mc1_19_03, null)
    mc1_19_03.link(mc1_19_02, file("versions/mapping-1.19.2-1.19.3.txt"))
    mc1_19_02.link(mc1_19_01, null)
    mc1_19_01.link(mc1_19_00, null)
//    mc1_19_00.link(mc1_18_02, null)
//    mc1_18_02.link(mc1_18_01, null)
//    mc1_18_01.link(mc1_18_00, null)
//    mc1_18_00.link(mc1_17_01, null)
//    mc1_17_01.link(mc1_17_00, null)
//    mc1_17_00.link(mc1_16_05, null)
}

tasks.register("buildAndGather") {
    subprojects {
        dependsOn(tasks.named("build"))
    }
    doFirst {
        println("Gathering builds")
        val buildLibs = { p: Project ->
            p.layout.buildDirectory.dir("libs").get().asFile.toPath()
        }
        delete(fileTree(buildLibs(rootProject)) {
            include("*")
        })
        subprojects {
            copy {
                from(buildLibs(project)) {
                    include("*.jar")
                    exclude("*-dev.jar", "*-sources.jar", "*-shadow.jar")
                }
                into(buildLibs(rootProject))
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
    }
}
