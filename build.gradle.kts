plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("dev.langchain4j:langchain4j-anthropic:1.22.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.22.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17") // LangChain4j logs via SLF4J; keep stderr clean

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// One runnable jar with all dependencies: java -jar build/libs/code-mapper.jar <file>
tasks.jar {
    manifest { attributes["Main-Class"] = "codemapper.MainKt" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
