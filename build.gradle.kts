buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.21.0")
    }
}

plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.serialization") apply false

    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    kotlin("plugin.spring") apply false
}


group = "github.io"
version = "1.0-SNAPSHOT"
