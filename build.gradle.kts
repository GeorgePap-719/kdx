plugins {
    kotlin("multiplatform") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.10"

    id("org.springframework.boot") version "3.0.4" apply false
    id("io.spring.dependency-management") version "1.1.0" apply false
    kotlin("plugin.spring") version "1.8.0" apply false
}

java.sourceCompatibility = JavaVersion.VERSION_17

group = "github.io"
version = "1.0-SNAPSHOT"

val kotlinxHtmlJvmVersion = "0.7.2"
val kotlinWrappersReactVersion = "18.2.0-pre.346"
val kotlinWrappersEmotionVersion = "11.9.3-pre.346"
val kotlinxCoroutinesVersion: String by project
val kotlinxSerializationVersion: String by project
val r2dbcMysqlDriver: String by project
val turbineVersion: String by project
val mockkVersion: String by project
val junitJupiterVersion: String by project
val junitPlatformVersion: String by project

repositories {
    jcenter()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

kotlin {
    jvm {
        apply(plugin = "org.springframework.boot")
        apply(plugin = "io.spring.dependency-management")
        apply(plugin = "org.jetbrains.kotlin.plugin.spring")

        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
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
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                // spring
                implementation("org.springframework.boot:spring-boot-starter-webflux")
                implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
                // database
                implementation("dev.miku:r2dbc-mysql:$r2dbcMysqlDriver")
                implementation("mysql:mysql-connector-java:8.0.28")
                // kotlin
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$kotlinxCoroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinxHtmlJvmVersion")
            }
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
        val jvmTest by getting {
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-junit5")
                implementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
                implementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
                implementation("org.junit.platform:junit-platform-commons:$junitPlatformVersion")
                implementation("io.mockk:mockk:$mockkVersion")
                // utils to test `Flow`
                implementation("app.cash.turbine:turbine:$turbineVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:$kotlinWrappersReactVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:$kotlinWrappersReactVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:$kotlinWrappersEmotionVersion")
            }
        }
        val jsTest by getting
    }
}

tasks.named<Copy>("jvmProcessResources") {
    val jsBrowserDistribution = tasks.named("jsBrowserDistribution")
    from(jsBrowserDistribution)
}