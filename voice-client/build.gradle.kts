plugins {
    application
}

application {
    mainClass.set("dev.truc5738.voiceclient.VoiceClient")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("Minecraft-VoiceChannel-VoiceClient")
    manifest.attributes["Main-Class"] = application.mainClass.get()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
}

tasks.test {
    useJUnitPlatform()
}
