plugins {
    java
}

group = "dev.truc5738"
version = "0.1.0-SNAPSHOT"

description = "Cross-platform voice channel plugin for Paper 26.2"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    // Paper 26.2 build 129 is the verified API coordinate used by this project.
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

val pluginVersion = project.version.toString()

tasks.processResources {
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

// Paper 26.1+ runs plugins on Mojang-mapped runtime names, so no legacy reobfuscation task is required.
