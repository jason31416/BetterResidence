plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "8.3.10"
}

group = "cn.jason31416"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.jason31416")
        }
    }
    mavenLocal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("com.github.jason31416:planetlib:1.4.1")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.runServer {
    minecraftVersion("1.21.8")
    doFirst {
        delete(layout.projectDirectory.dir("run/plugins/BetterResidence"))
    }
}

tasks.withType<ProcessResources> {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    relocate("cn.jason31416.planetlib", "cn.jason31416.betterresidence.lib.planetlib")
    mergeServiceFiles()
}
