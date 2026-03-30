import groovy.json.JsonSlurper
import org.gradle.kotlin.dsl.dependencies

plugins {
    id("java")
    id("maven-publish")
    id("net.fabricmc.fabric-loom-remap")
    id("com.replaymod.preprocess")
}

val mcVersion = project.property("mcVersion") as Int
val modId = project.property("mod_id") as String
val modWrapperId = project.property("mod_wrapper_id") as String
val modName = project.property("mod_name") as String
val modMavenGroup = project.property("mod_maven_group") as String
val modVersion = project.property("mod_version") as String
val modArchivesBaseName = project.property("mod_archives_base_name") as String
val modDescription = project.property("mod_description") as String
val modHomepage = project.property("mod_homepage") as String
val modLicense = project.property("mod_license") as String
val modSources = project.property("mod_sources") as String
val loaderVersion = project.property("loader_version") as String
val minecraftDependency = project.property("minecraft_dependency") as String
val minecraftVersion = project.property("minecraft_version") as String
val fabricApiVersion = project.property("fabric_api_version") as String
val accessWidener = "$modId.remap.accesswidener"

val javaVersion = when {
    mcVersion >= 260000 -> JavaVersion.VERSION_25   // 26+          需要 Java 25
    mcVersion >= 12005 -> JavaVersion.VERSION_21    // 1.20.5+      需要 Java 21
    mcVersion >= 11800 -> JavaVersion.VERSION_17    // 1.18-1.20.4  需要 Java 17
    mcVersion >= 11700 -> JavaVersion.VERSION_16    // 1.17.x       需要 Java 16
    else -> JavaVersion.VERSION_1_8                 // 1.16.x 及以下使用 Java 8
}
val mixinCompatibilityLevel = "JAVA_${javaVersion.majorVersion.toInt()}"

repositories {
    maven("https://maven.fabricmc.net")
    maven("https://jitpack.io")
}

// https://github.com/FabricMC/fabric-loader/issues/783
configurations.all {
    resolutionStrategy {
        force("net.fabricmc:fabric-loader:$loaderVersion")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion") // Minecraft 客户端依赖
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion") // Fabric 加载器依赖
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion") // Fabric API 依赖

//    runtimeOnly(project(":fabricWrapper"))
}

loom {

    accessWidenerPath.set(file("../../src/main/resources/$accessWidener"))

    var programArgs = listOf(
        "--width", "1280",
        "--height", "720"
    )
    val profileFile = file("../../profile.json")
    if (profileFile.exists()) {
        @Suppress("UNCHECKED_CAST")
        val profile = JsonSlurper().parseText(profileFile.readText()) as Map<String, List<String>>
        val username = profile["username"].toString()
        val uuid = profile["uuid"].toString()
        val xuid = profile["xuid"].toString()
        val accessToken = profile["accessToken"].toString()
        programArgs = programArgs + listOf(
            "--username", username,
            "--uuid", uuid,
            "--xuid", xuid,
            "--accessToken", accessToken,
            "--userType", "msa",
            "--versionType", "release"
        )
    }
    var commonVmArgs = listOf(
        "-Dmixin.debug.export=true",
        "-Dmixin.debug.countInjections=true",
//        "-DmixinAuditor.audit=true"
    )
    runs {
        named("client") {
            client()
            runDir = "../../run/client"
            vmArgs(commonVmArgs)
            programArgs(programArgs)
            ideConfigGenerated(true);
        }
        named("server") {
            server()
            runDir = "../../run/server"
        }
    }
}

// 示例版本值:
//   project.mod_version     1.0.3                      (基础 Mod 版本)
//   modVersionSuffix        +build.88                  (如果可能, 使用 GitHub Action 构建号)
//   artifactVersionSuffix   -SNAPSHOT
//   fullModVersion          1.0.3+build.88             (在 Mod 中使用的实际版本)
//   fullProjectVersion      v1.0.3-mc1.15.2+build.88   (在构建输出 jar 包名称中使用的版本)
//   fullArtifactVersion     1.0.3-mc1.15.2-SNAPSHOT    (Maven 产物版本)
var modVersionSuffix = ""
val artifactVersion = modVersion
var artifactVersionSuffix = ""

// 检测 GitHub Action 环境变量，用于设置版本后缀
// https://docs.github.com/en/actions/learn-github-actions/environment-variables#default-environment-variables
if (System.getenv("BUILD_RELEASE") != "true") {
//    val buildNumber = System.getenv("BUILD_ID")
    val buildNumber = System.getenv("GITHUB_RUN_NUMBER")
    // 如果存在构建号，则使用 +build.<号>，否则使用 -SNAPSHOT
    modVersionSuffix += if (buildNumber != null) "+build.$buildNumber" else "-SNAPSHOT"
    // 非发布版本产物通常是 SNAPSHOT 版本
    artifactVersionSuffix = "-SNAPSHOT"
}
val fullModVersion = "${modVersion}-mc${minecraftVersion}${modVersionSuffix}" // 完整的 Mod 版本 (用于 fabric.mod.json)
var fullProjectVersion: String  // 完整的项目版本 (用于 JAR 文件名)
var fullArtifactVersion: String // 完整的 Maven 产物版本
// 根据是否在 JITPACK 环境中运行进行版本和产物名称配置
if (System.getenv("JITPACK") == "true") {
    base.archivesName.set(
        "$modArchivesBaseName-mc$minecraftVersion"
    )
    fullProjectVersion = "v$modVersion$modVersionSuffix" // 例如 v1.0.3+build.88
    fullArtifactVersion = artifactVersion + artifactVersionSuffix // 例如 1.0.3-SNAPSHOT
} else {
    base.archivesName.set(modArchivesBaseName)
    fullProjectVersion = "v$modVersion-mc$minecraftVersion$modVersionSuffix" // 例如 v1.0.3-mc1.15.2+build.88
    fullArtifactVersion = "$artifactVersion-mc$minecraftVersion$artifactVersionSuffix" // 例如 1.0.3-mc1.15.2-SNAPSHOT
}

group = modMavenGroup // 设置 Maven Group ID
version = fullProjectVersion // 设置项目的版本号

tasks {
    // --- 资源处理 (Resource Processing) ---
    // 如果 IDEA 抱怨 "Cannot resolve resource filtering of MatchingCopyAction"，并且你想知道原因
    // 请参阅 https://youtrack.jetbrains.com/issue/IDEA-296490
    withType<ProcessResources> {
        val propertyMap = mapOf(
            "mod_id" to modId,
            "mod_wrapper_id" to modWrapperId,
            "mod_name" to modName,
            "mod_version" to fullModVersion,
            "mod_description" to modDescription,
            "mod_homepage" to modHomepage,
            "mod_license" to modLicense,
            "mod_sources" to modSources,
            "loader_version" to loaderVersion,
            "minecraft_dependency" to minecraftDependency,
            "compatibility_level" to mixinCompatibilityLevel,
            "accessWidener" to accessWidener
        )
        inputs.properties(propertyMap)
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
            expand(propertyMap)
        }
    }

    // --- Java 编译配置 ---
    // 确保编码设置为 UTF-8，无论系统默认值是什么
    // 这修复了某些特殊字符无法正确显示的边缘情况
    // 参阅 http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // 添加编译器参数以显示弃用和未检查的警告
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
        if (javaVersion <= JavaVersion.VERSION_1_8) {
            // 如果使用 Java 8 或更低版本，压制 "source/target value 8 is obsolete..." 的警告
            options.compilerArgs.add("-Xlint:-options")
        }
    }

    withType<Jar> {
        exclude("$modId.accesswidener")
        // 将 LICENSE 文件添加到 JAR 包中
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${modArchivesBaseName}" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod_version")}"))
        dependsOn("build")
    }
}

java {
    sourceCompatibility = javaVersion // 设置源码兼容性
    targetCompatibility = javaVersion // 设置目标字节码兼容性
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"]) // 从 Java 插件获取要发布的组件
            artifactId = base.archivesName.get() // 设置产物 ID (Artifact ID)
            version = fullArtifactVersion // 设置产物版本
        }
    }

    // 选择要发布到的仓库
    repositories {
        // 本地 Maven 仓库
        // mavenLocal()

//     // [功能] FALLENS_MAVEN 仓库 - 被注释掉的自定义 Maven 仓库配置
//     maven {
//        // 如果是 SNAPSHOT 版本，发布到快照仓库；否则发布到发布仓库
//        url = uri(if (fullArtifactVersion.endsWith("SNAPSHOT")) "https://maven.fallenbreath.me/snapshots" else "https://maven.fallenbreath.me/releases")
//        credentials {
//           username = "fallen"
//           // 从环境变量获取密码/令牌
//           password = System.getenv("FALLENS_MAVEN_TOKEN")
//        }
//        authentication {
//           // 使用 Basic 认证
//           create<BasicAuthentication>("basic")
//        }
//     }
    }
}