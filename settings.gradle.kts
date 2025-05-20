pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }

    resolutionStrategy {
        plugins {
            val kotlinVersion = extra["kotlinVersion"] as String
            kotlin("multiplatform") version kotlinVersion
            kotlin("jvm") version kotlinVersion
            kotlin("plugin.serialization") version kotlinVersion
        }
    }
}

rootProject.name = "kdx"

fun module(name: String, path: String) {
    include(name)
    val projectDir = rootDir.resolve(path).normalize().absoluteFile
    if (!projectDir.exists()) {
        throw AssertionError("file $projectDir does not exist")
    }
    project(name).projectDir = projectDir
}

module(":kdx-core", "kdx-core")