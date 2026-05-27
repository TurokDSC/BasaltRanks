plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.modrinth.minotaur") version "2.8.7"
}

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.6.1") }
}

group = "dev.turok_ss"
version = "2.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

kotlin {
    jvmToolchain(21)
}

val testServerPlugins = rootProject.projectDir.parentFile.parentFile.resolve("test-server/plugins")

val proguardLibs: Configuration by configurations.creating {
    extendsFrom(configurations.compileOnly.get())
    isCanBeResolved = true
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("BasaltRanks")
        archiveVersion.set("2.2.0")
        mergeServiceFiles()
        relocate("kotlin", "dev.turok_ss.basaltranks.libs.kotlin")
        relocate("org.bstats", "dev.turok_ss.basaltranks.libs.bstats")

        // Exclude SLF4J from shading - Paper provides it at runtime
        exclude("org/slf4j/**")
        exclude("META-INF/services/org.slf4j.*")

        doLast {
            copy {
                from(archiveFile)
                into(testServerPlugins)
            }
            println("Copied to: $testServerPlugins")
        }
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.4")
    }
}

tasks.register<proguard.gradle.ProGuardTask>("obfuscate") {
    dependsOn(tasks.shadowJar, tasks.jar)

    val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
    val obfuscatedFile = layout.buildDirectory.file("obfuscated/BasaltRanks-${version}.jar").get().asFile

    injars(shadowJarFile)
    outjars(obfuscatedFile)

    val javaHome = System.getProperty("java.home")
    libraryjars("$javaHome/jmods/java.base.jmod")
    libraryjars("$javaHome/jmods/java.logging.jmod")
    libraryjars("$javaHome/jmods/java.desktop.jmod")
    libraryjars("$javaHome/jmods/java.sql.jmod")
    libraryjars("$javaHome/jmods/java.net.http.jmod")
    libraryjars("$javaHome/jmods/jdk.httpserver.jmod")
    libraryjars(proguardLibs.files)

    printmapping(layout.buildDirectory.file("obfuscated/proguard-mapping.txt").get().asFile)

    keep("public class dev.turok_ss.basaltranks.BasaltRanks { *; }")
    keep("class dev.turok_ss.basaltranks.BasaltRanks\$Companion { *; }")
    keep("interface dev.turok_ss.basaltranks.modules.Module { *; }")
    keepclassmembers("class * implements org.bukkit.event.Listener { @org.bukkit.event.EventHandler <methods>; }")
    keepclassmembers("class * implements org.bukkit.command.CommandExecutor { *; }")
    keepclassmembers("class * implements org.bukkit.command.TabCompleter { *; }")
    keep("class dev.turok_ss.basaltranks.hooks.BasaltRanksExpansion { *; }")
    keep("class dev.turok_ss.basaltranks.libs.bstats.** { *; }")

    keepattributes("Signature,*Annotation*,InnerClasses,EnclosingMethod")
    keepnames("class * implements java.io.Serializable")

    dontwarn()
    dontoptimize()
    repackageclasses("dev.turok_ss.basaltranks.internal")
    overloadaggressively()
    allowaccessmodification()
    adaptresourcefilecontents("plugin.yml")

    doLast {
        if (obfuscatedFile.exists()) {
            val destFile = testServerPlugins.resolve("BasaltRanks-${version}.jar")
            obfuscatedFile.copyTo(destFile, overwrite = true)
            println("Obfuscated JAR: ${obfuscatedFile.name} (${obfuscatedFile.length() / 1024}KB)")
            println("Copied to: $testServerPlugins")
        }
    }
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("basaltranks")
    versionNumber.set(version.toString())
    versionName.set("BasaltRanks v${version}")
    versionType.set("release")
    uploadFile.set(layout.buildDirectory.file("obfuscated/BasaltRanks-${version}.jar"))
    tasks.named("modrinth") { dependsOn("obfuscate") }
    gameVersions.addAll("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
        "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4")
    loaders.addAll("paper", "purpur", "folia")
    dependencies {
        optional.project("luckperms")
        optional.project("placeholderapi")
    }
    changelog.set("""
        ## BasaltRanks v${version}

        ### New: Animated Badges
        - 4 animation types: Rainbow, Pulse, Glow, Gradient Shift
        - Configurable speed (5-40 ticks) and frame count (2-8)
        - Animations visible in tab list and nametags
        - Full GUI editor controls for animation
        - Web editor animation support
        - `/badge animate <id> <type|off> [speed] [frames]`

        ### Improvements
        - Animation scheduler with zero overhead when no animated badges exist
        - 10 PlaceholderAPI placeholders (including animated, animation_type)
        - REST API supports animation fields
        - Resource pack auto-includes animation frames

        ### Requirements
        - Paper 1.20.x - 1.21.x (or Purpur/Folia)
        - Java 21+
        - Optional: LuckPerms, PlaceholderAPI
    """.trimIndent())
}
