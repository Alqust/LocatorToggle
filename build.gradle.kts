plugins {
    java
}

group = "me.locatortoggle"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Версия Paper API под вашу сборку сервера 26.3.
// Точную строку версии смотрите на https://repo.papermc.io (группа io.papermc.paper, артефакт paper-api)
// и при необходимости поменяйте её здесь или в gradle.properties.
val paperApiVersion: String = (findProperty("paperApiVersion") as String?) ?: "26.3.build.244-stable"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    // Minecraft 26.x требует Java 25
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
