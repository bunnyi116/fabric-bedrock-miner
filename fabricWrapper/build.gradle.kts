import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("java-library")
    id("maven-publish")
}

// --- 项目属性定义 ---
// 这些属性通常从 Gradle 属性文件 (如 gradle.properties) 或 settings.gradle.kts 中传入。
val modId: String = project.property("mod_id") as String
val modWrapperId: String = "$modId-wrapper"
val modName: String = project.property("mod_name") as String
val modMavenGroup: String = project.property("mod_maven_group") as String
val modVersion: String = project.property("mod_version") as String
val modArchivesBaseName: String = project.property("mod_archives_base_name") as String
val modDescription: String = project.property("mod_description") as String
val modHomepage: String = project.property("mod_homepage") as String
val modLicense: String = project.property("mod_license") as String
val modSources: String = project.property("mod_sources") as String
val loaderVersion: String = project.property("loader_version") as String

val time: String? = SimpleDateFormat("yyMMddHH").apply {
    timeZone = TimeZone.getTimeZone("GMT+08:00")
}.format(Date())

group = modMavenGroup
version = "$modVersion+$time"

base {
    archivesName.set("$modArchivesBaseName-versionpack")
}

// 获取父项目的所有子项目，并过滤掉当前项目 (fabricWrapper)
val fabricSubprojects = rootProject.subprojects.filter { it.name != "fabricWrapper" }

// 确保在评估当前项目之前，先评估所有相关的子项目
// 强制 Gradle 先“读取并执行”完所有子项目的 build.gradle 脚本，然后再继续处理当前项目（Wrapper）的脚本。
fabricSubprojects.forEach {
    evaluationDependsOn(":${it.name}")
}

tasks.register("collectSubModules") {
    // 禁用缓存
    outputs.upToDateWhen { false }

    // 依赖所有子项目的 remapJar 任务
    // 注意：我们需要使用 tasks.getByName 或 findByName 来获取动态添加的任务 (Loom 添加的 remapJar)
    dependsOn(fabricSubprojects.map {
        it.tasks.getByName("remapJar")
    })

    doFirst {
        delete(layout.buildDirectory.dir("tmp/submods/META-INF/jars"))
        delete(layout.buildDirectory.dir("build/libs"))

        // 复制所有重映射后的 JAR 文件
        copy {
            from(fabricSubprojects.map { sub ->
                sub.tasks.getByName("remapJar").outputs.files   // 获取 remapJar 任务的输出文件
            })
            into(layout.buildDirectory.dir("tmp/submods/META-INF/jars"))
        }
    }

    doFirst {
        val wrapperIcon = project.file("resources/assets/$modWrapperId/icon.png")
        println(wrapperIcon)
        if (!wrapperIcon.exists()) {
            // 从根项目复制图标
            val rootIcon = rootProject.file("src/main/resources/assets/$modId/icon.png")
            println(rootIcon)
            if (rootIcon.exists()) {
                val outputIcon =
                    layout.buildDirectory.file("/resources/main/assets/$modWrapperId/icon.png").get().asFile
                println(outputIcon)
                outputIcon.parentFile.mkdirs()  // 确保目标目录存在
                rootIcon.copyTo(outputIcon, overwrite = true)   // 复制文件
            }
        }
    }
}

tasks.named<Jar>("jar") {
    outputs.upToDateWhen { false }  // 禁用缓存
    dependsOn("collectSubModules")  // 依赖子模块收集任务
    from(rootProject.file("LICENSE"))
    from(layout.buildDirectory.dir("tmp/submods"))  // 从收集目录添加文件
}

tasks.named<ProcessResources>("processResources") {
    outputs.upToDateWhen { false }  // 禁用缓存

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "mod_id" to modWrapperId,
                "mod_name" to modName,
                "mod_version" to project.version,
                "mod_description" to modDescription,
                "mod_homepage" to modHomepage,
                "mod_license" to modLicense,
                "mod_sources" to modSources,
                "loader_version" to loaderVersion,
            )
        )
    }

    doLast {
        val jars = ArrayList<Map<String, String>>()
        val jarsDir = layout.buildDirectory.dir("tmp/submods/META-INF/jars").get().asFile

        // 方法1: 扫描实际收集到的 JAR 文件
        if (jarsDir.exists() && jarsDir.isDirectory) {
            val jarFiles = jarsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jar") &&
                        !file.name.contains("-dev.jar") &&
                        !file.name.contains("-sources.jar") &&
                        !file.name.contains("-shadow.jar")
            }

            jarFiles?.forEach { jarFile ->
                jars.add(mapOf("file" to "META-INF/jars/${jarFile.name}"))
                println("✓ 找到实际 JAR 文件: ${jarFile.name}")
            }
        }

        // 获取构建输出目录中的 fabric.mod.json
        val jsonFile = layout.buildDirectory.file("resources/main/fabric.mod.json").get().asFile
        if (jsonFile.exists()) {
            val slurper = JsonSlurper()

            @Suppress("UNCHECKED_CAST")
            val jsonContent = slurper.parse(jsonFile) as MutableMap<String, Any>

            // 设置 jars 数组
            jsonContent["jars"] = jars

            // 使用 JsonBuilder 写回
            val builder = JsonBuilder(jsonContent)
            jsonFile.bufferedWriter().use { writer ->
                writer.write(builder.toPrettyString())
            }

            println("- JAR 文件数量: ${jars.size}")
            jars.forEach { jar ->
                println("  - ${jar["file"]}")
            }
        } else {
            println("警告: 找不到生成的 fabric.mod.json 文件: ${jsonFile.absolutePath}")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            groupId = modMavenGroup
            artifactId = modId
            version = "versionpack-${project.version}"
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
        maven {
            url = uri("${rootProject.projectDir}/publish")
        }
    }
}