//buildscript {
//    repositories {
//        mavenCentral()
//    }
//
//    //dependencies {
//    // uncomment if needed
////        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.20.2")
//    //}
//}

repositories {
    jcenter()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions {
                    freeCompilerArgs = listOf("-Xjsr305=strict")
                    jvmTarget = "17"
                }
            }
            tasks.withType<Test> {
                useJUnitPlatform()
            }
        }
        val jsMain by getting
        val jsTest by getting
    }

}