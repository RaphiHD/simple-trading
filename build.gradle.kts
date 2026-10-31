plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

description = "Allow your players to safely trade with each other"

// The plugin is compiled against the oldest supported Paper version (26.2).
// Paper keeps API compatibility across minor versions, so the same jar runs on 26.2 and 26.3.
val paperApiVersion = "26.2.build.+"
val testServerVersion = "26.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.MilkBowl") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        isTransitive = false
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version, "description" to project.description)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    jar {
        archiveFileName = "SimpleTrading-${project.version}.jar"
    }

    runServer {
        minecraftVersion(testServerVersion)
    }
}
