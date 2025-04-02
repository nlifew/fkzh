plugins {
    kotlin("jvm") version "2.1.10"
}

group = "com.toybox"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val nettyVersion = "4.1.118.Final"
    val brotliVersion = "1.18.0"

    implementation("com.google.code.gson:gson:2.10.1")
//    implementation("org.jsoup:jsoup:1.18.3")
//    implementation("io.netty:netty-all:$nettyVersion")
    implementation("io.netty:netty-buffer:$nettyVersion")
    implementation("io.netty:netty-transport:$nettyVersion")
    implementation("io.netty:netty-codec-http:$nettyVersion")
    implementation("io.netty:netty-codec-dns:$nettyVersion")
    implementation("io.netty:netty-resolver-dns:$nettyVersion")
//    implementation("io.netty:netty-resolver-dns-native-macos:$nettyVersion")
//    implementation("io.netty:netty-resolver-dns-native-linux:$nettyVersion")

    implementation("com.aayushatharva.brotli4j:brotli4j:${brotliVersion}")
    implementation("com.aayushatharva.brotli4j:native-osx-aarch64:${brotliVersion}")
//    implementation("com.aayushatharva.brotli4j:native-linux-x86_64:${brotliVersion}")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}