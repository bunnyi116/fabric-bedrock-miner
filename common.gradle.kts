plugins {
//    id("java") // 启用 Java 插件 - 当前被注释掉，但通常是必需的。
    id("maven-publish") // 启用 Maven 发布插件 (用于发布构建产物到仓库)
    id("fabric-loom") // 启用 Fabric Loom 插件 (Minecraft Mod 开发工具链)
    id("com.replaymod.preprocess") // 启用 ReplayMod 预处理器插件 (用于处理跨 Minecraft 版本的代码/资源)
    id("me.fallenbreath.yamlang") // 启用 yamlang 插件 (用于处理语言文件，例如 YAML 格式)
}

// 这些属性通常从 Gradle 属性文件 (如 gradle.properties) 或 settings.gradle.kts 中传入。
val mcVersion: Int = project.property("mcVersion") as Int
val modId: String = project.property("mod_id") as String
val modWrapperId: String = project.property("mod_wrapper_id") as String
val modName: String = project.property("mod_name") as String
val modVersion: String = project.property("mod_version") as String
val modMavenGroup: String = project.property("mod_maven_group") as String
val modArchivesBaseName: String = project.property("mod_archives_base_name") as String
val minecraftDependency: String = project.property("minecraft_dependency") as String
val minecraftVersion: String = project.property("minecraft_version") as String
val yarnMappings: String = project.property("yarn_mappings") as String
val loaderVersion: String = project.property("loader_version") as String
val fabricApiVersion: String = project.property("fabric_api_version") as String

// 常量
val mixinConfigPath = "blockminer.mixins.json"
val langDir = "assets/blockminer/lang"

// 根据 Minecraft 版本确定所需的 Java 兼容性版本
val javaCompatibility = when {
    mcVersion >= 12005 -> JavaVersion.VERSION_21 // 1.20.5+ 需要 Java 21
    mcVersion >= 11800 -> JavaVersion.VERSION_17 // 1.18 - 1.20.4 需要 Java 17
    mcVersion >= 11700 -> JavaVersion.VERSION_16 // 1.17.x 需要 Java 16
    else -> JavaVersion.VERSION_1_8 // 1.16.x 及以下使用 Java 8
}
val mixinCompatibilityLevel = javaCompatibility // Mixin 兼容性级别与 Java 兼容性版本保持一致

repositories {
    maven("https://maven.fabricmc.net")
    maven("https://jitpack.io")
}

// https://github.com/FabricMC/fabric-loader/issues/783
configurations {
    // 将 fabric-loader 从 modRuntimeOnly 配置中排除，避免在运行时包含重复的加载器。
    named("modRuntimeOnly") {
        exclude(group = "net.fabricmc", module = "fabric-loader")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion") // Minecraft 客户端依赖
    mappings("net.fabricmc:yarn:$yarnMappings:v2") // Yarn 混淆映射
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion") // Fabric 加载器依赖
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion") // Fabric API 依赖
}

loom {
    // 设置 Access Widener (访问增强器) 文件的路径
    accessWidenerPath.set(file("../../src/main/resources/bedrockminer.accesswidener"))

    val commonVmArgs = listOf("-Dmixin.debug.export=true", "-Dmixin.debug.countInjections=true") // 通用 JVM 参数
    runs.configureEach {
        // 配置 IDE 运行时设置。
        // 确保它生成所有 "Minecraft Client (:subproject_name)" 应用程序
        runDir = "../../run" // 设置运行目录
        vmArgs(commonVmArgs) // 应用通用的 JVM 参数
    }

    //  // [功能] MIXIN 审计器 (MIXIN_AUDITOR) - 被注释掉的 Mixin 审计配置
    //  runs {
    //     val auditVmArgs = commonVmArgs + "-DmixinAuditor.audit=true"
    //     create("serverMixinAudit") {
    //        server() // 配置为运行服务器
    //        vmArgs(auditVmArgs)
    //        ideConfigGenerated.set(false) // 禁用 IDE 配置生成
    //     }
    //  }
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
    val buildNumber = System.getenv("BUILD_ID")
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

// --- 资源处理 (Resource Processing) ---
// 如果 IDEA 抱怨 "Cannot resolve resource filtering of MatchingCopyAction"，并且你想知道原因
// 请参阅 https://youtrack.jetbrains.com/issue/IDEA-296490
tasks.withType<ProcessResources> {
    // 设置输入属性，以便 Gradle 知道在这些属性变化时需要重新运行任务
    inputs.property("mod_id", modId)
    inputs.property("mod_wrapper_id", modWrapperId)
    inputs.property("mod_name", modName)
    inputs.property("mod_version", fullModVersion)
    inputs.property("loader_version", loaderVersion)
    inputs.property("minecraft_dependency", minecraftDependency)

    filesMatching("fabric.mod.json") {
        // 使用 expand 替换 fabric.mod.json 中的占位符
        expand(
            mapOf(
                "mod_id" to modId,
                "mod_wrapper_id" to modWrapperId,
                "mod_name" to modName,
                "mod_version" to fullModVersion,
                "loader_version" to loaderVersion,
                "minecraft_dependency" to minecraftDependency,
            )
        )
    }
    filesMatching(mixinConfigPath) {
        // 替换 Mixin 配置中的 COMPATIBILITY_LEVEL 占位符
        filter { it.replace("{{COMPATIBILITY_LEVEL}}", "JAVA_${mixinCompatibilityLevel.ordinal + 1}") }
    }
}

// https://github.com/Fallen-Breath/yamlang
yamlang {
    targetSourceSets.set(setOf(sourceSets.main.get())) // 指定要处理的源集
    inputDir.set(langDir) // 指定语言文件目录
}

// --- Java 编译配置 ---
// 确保编码设置为 UTF-8，无论系统默认值是什么
// 这修复了某些特殊字符无法正确显示的边缘情况
// 参阅 http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // 添加编译器参数以显示弃用和未检查的警告
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    if (javaCompatibility <= JavaVersion.VERSION_1_8) {
        // 如果使用 Java 8 或更低版本，压制 "source/target value 8 is obsolete..." 的警告
        options.compilerArgs.add("-Xlint:-options")
    }
}

java {
    sourceCompatibility = javaCompatibility // 设置源码兼容性
    targetCompatibility = javaCompatibility // 设置目标字节码兼容性

    // 如果存在，Loom 会自动将 sourcesJar 附加到 RemapSourcesJar 任务和 "build" 任务
    // 如果移除此行，则不会生成源码 JAR。
    withSourcesJar()
}

tasks.withType<Jar> {
    // 将 LICENSE 文件添加到 JAR 包中
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${modArchivesBaseName}" }
    }
}

// --- Maven 发布配置 ---
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