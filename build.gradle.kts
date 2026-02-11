import com.google.protobuf.gradle.*

plugins {
    java
    application
    id("com.google.protobuf") version "0.9.4"
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
val grpcVersion = "1.62.2"
val protobufVersion = "3.25.3"

dependencies {
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:$jacksonVersion")

    implementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")

    // gRPC
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}

application {
    mainClass.set("com.pension.engine.Main")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
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
