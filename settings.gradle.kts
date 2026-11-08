pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }

        maven {
            name = "legacy-fabric"
            url = uri("https://maven.legacyfabric.net/")
        }

        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "sodiumlegacy"

// :core   -> code with no dependency on Minecraft or the loader (LWJGL3 shim + common). Shared by Fabric and Forge.
// :fabric -> Legacy Fabric (Legacy Yarn names): api + main (mixins, render, GUI) + resources.
// :forge  -> It is included only to expose :forge:runClient, :forge:buildForge and :forge:cleanForge, which
//            delegate to forge/gradlew.
include(":core", ":fabric", ":forge")
project(":forge").buildFileName = "root-delegate.gradle"
