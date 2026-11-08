import java.util.Properties
import java.util.zip.ZipFile

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val rootProps = Properties().apply { file("../gradle.properties").inputStream().use { load(it) } }
val modVersion: String = rootProps.getProperty("mod_version")
val jomlVersion: String = rootProps.getProperty("joml_version")
val fastutilVersion: String = rootProps.getProperty("fastutil_version")
val annotationsVersion: String = rootProps.getProperty("jetbrains_annotations_version")
val modid = "sodiumlegacy"

group = "me.sodiumlegacy"
version = modVersion

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}


val java8Launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(8))
}

tasks.withType<JavaExec>().configureEach {
    val runTask = this
    doFirst {
        runTask.executable = java8Launcher.get().executablePath.asFile.absolutePath
    }
}

sourceSets.named("main") {
    // :core (shim LWJGL3 + common)
    java.srcDir("../core/src/main/java")

    output.setResourcesDir(java.classesDirectory)
}

loom {
    launchConfigs {
        "client" {
            property("mixin.debug", "false")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("sodiumlegacy.mixins.json")
    }
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    // Runtime: 0.7.11 (works with the ASM 5.0.3 shipped by LaunchWrapper in 1.8.9;
    // 0.8.x needs asm-commons 9 and crashes with NoClassDefFoundError: ClassRemapper)
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    // Compile-time/annotation processor can stay on 0.8.5
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")

    shadowImpl("org.joml:joml:$jomlVersion")
    compileOnly("it.unimi.dsi:fastutil:$fastutilVersion")
    shadowImpl(files(project.provider { fastutilTrimmedJar.get().outputs.files }))

    compileOnly("org.jetbrains:annotations:$annotationsVersion")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    archiveBaseName.set("sodiumlegacy-forge")
    archiveVersion.set("$modVersion-beta")
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "sodiumlegacy.mixins.json"
    }
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching(listOf("mcmod.info", "sodiumlegacy-forge.properties")) {
        expand(mapOf("version" to project.version))
    }
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    archiveFileName.set("sodiumlegacy-forge-$modVersion-beta.jar")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("badjars"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("badjars"))
    archiveClassifier.set("all-dev")
    configurations = listOf(shadowImpl)

    exclude("module-info.class", "META-INF/versions/**")
}

tasks.assemble.get().dependsOn(tasks.remapJar)

// Packages only the fastutil classes the mod can actually reach at runtime.
val fastutilKeepClasses = rootProject.file("../gradle/fastutil-keep-classes.txt")
val fastutilFull: Configuration = configurations.detachedConfiguration(
    dependencies.create("it.unimi.dsi:fastutil:$fastutilVersion")
)

val fastutilTrimmedJar = tasks.register<Zip>("fastutilTrimmedJar") {
    group = "build"
    description = "Packages only the fastutil classes used by the mod (keeps ~450)."
    archiveBaseName.set("fastutil")
    archiveVersion.set("$fastutilVersion-trimmed")
    archiveFileName.set("fastutil-$fastutilVersion-trimmed.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fastutil-trimmed"))

    val fastutilJar = fastutilFull
        .first { it.name.endsWith(".jar") }
        ?: throw GradleException("fastutil jar could not be resolved")

    from(zipTree(fastutilJar)) {
        include(fastutilKeepClasses.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(".", "/") + ".class" })
    }

    doLast {
        val zip = ZipFile(outputs.files.first())
        val entries = zip.entries().asSequence().count { it.name.endsWith(".class") }
        val expected = fastutilKeepClasses.readLines().count { it.isNotBlank() }
        zip.close()
        if (entries != expected) {
            throw GradleException(
                "fastutilTrimmedJar packaged $entries classes but the keep list has $expected"
            )
        }
    }
}

tasks.shadowJar {
    dependsOn(fastutilTrimmedJar)
}
