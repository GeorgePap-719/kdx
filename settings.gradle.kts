pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }

    resolutionStrategy {
        plugins {
            val kotlinVersion = extra["kotlinVersion"] as String
            val springBootVersion = extra["springBootVersion"] as String
            val springDependencyManagement = extra["springDependencyManagement"] as String

            kotlin("multiplatform") version kotlinVersion
            kotlin("jvm") version kotlinVersion
            kotlin("plugin.serialization") version kotlinVersion
            id("org.springframework.boot") version springBootVersion apply false
            id("io.spring.dependency-management") version springDependencyManagement apply false
            kotlin("plugin.spring") version kotlinVersion apply false
        }
    }
}

rootProject.name = "keb"

fun module(name: String, path: String) {
    include(name)
    val projectDir = rootDir.resolve(path).normalize().absoluteFile
    if (!projectDir.exists()) {
        throw AssertionError("file $projectDir does not exist")
    }
    project(name).projectDir = projectDir
}

module(":keb-rope", "keb-rope")
module(":keb-server", "keb-server")