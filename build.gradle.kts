plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    application
}

group = "chat"
version = "1.0.0"

repositories {
    mavenCentral()
}

val koogVersion = "1.3.0-beta"
val ktorVersion = "3.3.3"

dependencies {
    // Koog framework: Ktor plugin (agents, prompt executor, tools)
    implementation("ai.koog:koog-ktor:$koogVersion")
    // Ktor-based HTTP client factory for Koog LLM clients
    implementation("ai.koog:http-client-ktor:1.3.0")
    // DeepSeek LLM client and model definitions
    implementation("ai.koog:prompt-executor-deepseek-client:$koogVersion")

    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Ktor client (engine for Koog's HTTP client)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")

    // Console logging
    implementation("ch.qos.logback:logback-classic:1.5.13")

    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("chat.ApplicationKt")
}
