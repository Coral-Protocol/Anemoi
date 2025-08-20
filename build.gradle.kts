plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

application {
    mainClass.set("org.coralprotocol.coralserver.gaia.GaiaScoringApplicationKt")
}

group = "org.coralprotocol"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        name = "sonatypeSnapshots"
    }

    maven("https://repo.repsy.io/mvn/chrynan/public")
}


dependencies {
    implementation("io.ktor:ktor-client-core-jvm:3.0.2")
    implementation("io.ktor:ktor-client-apache:3.0.2")
    testImplementation(kotlin("test"))
    implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.fusesource.jansi:jansi:2.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.charleskorn.kaml:kaml:0.78.0") // YAML serialization
    implementation("io.github.pdvrieze.xmlutil:core:0.91.0") // XML serialization
    implementation("io.github.pdvrieze.xmlutil:serialization:0.91.0")
    implementation("io.github.pdvrieze.xmlutil:core-jdk:0.91.0")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.91.0")
    implementation("com.github.docker-java:docker-java:3.5.1")


    // Hoplite for configuration
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.9.0")

    val ktorVersion = "3.0.2"
    implementation(enforcedPlatform("io.ktor:ktor-bom:$ktorVersion"))

    val uriVersion="0.5.0"
    implementation("com.chrynan.uri.core:uri-core:$uriVersion")
    implementation("com.chrynan.uri.core:uri-ktor-client:$uriVersion")

    // Ktor testing dependencies
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock")
    val arcVersion = "0.126.0"
    // Arc agents for E2E tests
    testImplementation("org.eclipse.lmos:arc-agents:$arcVersion")
    testImplementation("org.eclipse.lmos:arc-mcp:$arcVersion")
    testImplementation("org.eclipse.lmos:arc-server:$arcVersion")
    testImplementation("org.eclipse.lmos:arc-azure-client:$arcVersion")
    testImplementation("org.eclipse.lmos:arc-langchain4j-client:$arcVersion")
    testImplementation("io.modelcontextprotocol.sdk:mcp:0.11.0-SNAPSHOT") // Override MCP Java client for Arc 0.126.0
    testImplementation("io.mockk:mockk:1.14.2")

    // kotest
    // TODO: Use kotest for some or all tests
//    val kotestVersion = "5.9.1"
//    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
//    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
//    testImplementation("io.kotest:kotest-property:$kotestVersion")

    // Ktor client dependencies
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-cio-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-plugins")

    implementation("net.pwall.json:json-kotlin-schema:0.56")

    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-sse")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-content-negotiation")
    testImplementation("io.ktor:ktor-server-core")
    testImplementation("io.ktor:ktor-server-cio")
    testImplementation("io.ktor:ktor-server-sse")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.coralprotocol.coralserver.gaia.GaiaScoringApplicationKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

kotlin {
    jvmToolchain(21)
}
