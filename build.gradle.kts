plugins {
    id("dev.clojurephant.clojure") version "0.8.0-beta.7"
    id("fabric-loom") version "1.6-SNAPSHOT"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    maven("https://repo.clojars.org/")
}

dependencies {
    mappings(loom.officialMojangMappings())
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    implementation("org.clojure:clojure:latest.release")
    include("org.clojure:clojure:latest.release")
    include("org.clojure:core.specs.alpha:0.4.74")
    include("org.postgresql:postgresql:42.7.4")
    include("org.clojure:spec.alpha:0.5.238")

    compileOnly("net.luckperms:api:5.4")
    implementation("org.clojure:java.jdbc:0.7.12")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.clojure:tools.logging:1.2.3")
    implementation("org.clojure:data.json:2.4.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
                "version" to project.version,
                "minecraft_version" to project.property("minecraft_version"),
                "loader_version" to project.property("loader_version")
        )
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

clojure {
    builds {
        named("main") {
            compiler.directLinking.set(true)
            classpath.setFrom(sourceSets["main"].compileClasspath)
            aotAll()
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    classpath += files(sourceSets["main"].clojure.classesDirectory)
}
