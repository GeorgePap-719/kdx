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

allprojects {
    tasks.withType<Test> {
        useJUnitPlatform()
        afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            // Only execute on the outermost suite.
            if (desc.parent == null) {
                println("Tests: ${result.testCount}")
                println("Passed: ${result.successfulTestCount}")
                println("Failed: ${result.failedTestCount}")
                println("Skipped: ${result.skippedTestCount}")
            }
        }))
    }
}