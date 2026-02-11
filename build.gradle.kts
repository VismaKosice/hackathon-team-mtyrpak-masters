plugins {
    java
    application
}

group = "com.pension"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

val vertxVersion = "4.5.11"
val jacksonVersion = "2.18.2"

dependencies {
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:$jacksonVersion")

    implementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
}

application {
    mainClass.set("com.pension.engine.Main")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.pension.engine.Main"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    archiveFileName.set("pension-engine.jar")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
