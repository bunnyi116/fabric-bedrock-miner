import groovy.json.JsonSlurper

plugins {
    id("java")
    id("maven-publish")
    id("net.fabricmc.fabric-loom")
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
    maven("https://api.modrinth.com/maven")
    maven("https://maven.shedaniel.me/")
}

// https://github.com/FabricMC/fabric-loader/issues/783
configurations.all {
    resolutionStrategy {
        force("net.fabricmc:fabric-loader:$loaderVersion")
    }
}

val modmenuVersion = when {
    minecraftVersion.startsWith("1.19") -> "7.2.2"
    minecraftVersion == "1.20" || minecraftVersion == "1.20.1" -> "7.2.2"
    minecraftVersion == "1.20.2" -> "8.0.1"
    minecraftVersion == "1.20.3" || minecraftVersion == "1.20.4" -> "9.0.0"
    minecraftVersion == "1.20.5" || minecraftVersion == "1.20.6" -> "10.0.0"
    minecraftVersion == "1.21.11" -> "17.0.0"
    minecraftVersion.startsWith("1.21") -> "11.0.3"
    minecraftVersion.startsWith("26.") -> "17.0.0"
    else -> "11.0.3"
}

val clothConfigVersion = when {
    minecraftVersion.startsWith("1.19") -> "8.3.115"
    minecraftVersion == "1.20" || minecraftVersion == "1.20.1" -> "11.1.118"
    minecraftVersion == "1.20.2" -> "12.0.109"
    minecraftVersion == "1.20.3" || minecraftVersion == "1.20.4" -> "13.0.121"
    minecraftVersion == "1.20.5" || minecraftVersion == "1.20.6" -> "14.0.126"
    minecraftVersion == "1.21.11" -> "21.11.153"
    minecraftVersion.startsWith("1.21") -> "15.0.130"
    minecraftVersion == "26.1" -> "26.1.154"
    minecraftVersion == "26.2" -> "26.2.155"
    minecraftVersion.startsWith("26.") -> "26.2.155"
    else -> "15.0.130"
}

dependencies {
    compileOnly("org.projectlombok:lombok:${properties["lombok_version"]}")
    annotationProcessor("org.projectlombok:lombok:${properties["lombok_version"]}")

    minecraft("com.mojang:minecraft:$minecraftVersion") // Minecraft 客户端依赖
    implementation("net.fabricmc:fabric-loader:$loaderVersion") // Fabric 加载器依赖
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion") // Fabric API 依赖
    compileOnly("maven.modrinth:modmenu:$modmenuVersion")
    runtimeOnly("maven.modrinth:modmenu:$modmenuVersion")
    compileOnly("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion")
    runtimeOnly("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion")
//    runtimeOnly(project(":fabricWrapper"))
}

loom {
    enableTransitiveAccessWideners.set(false)
    runs {
        named("client") {
            client()
            runDirectory.set(file("../../run/client"))
            jvmArguments.add("-Dmixin.debug.export=true")
            jvmArguments.add("-Dmixin.debug.countInjections=true")
            programArguments.addAll("--width", "1280")
            programArguments.addAll("--height", "720")
            programArguments.addAll("--username", "bedrockminer")
            generateRunConfig.set(true)
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
            "compatibility_level" to mixinCompatibilityLevel
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
        // 将 LICENSE 文件添加到 JAR 包中
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${modArchivesBaseName}" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(jar.map { it.archiveFile })
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