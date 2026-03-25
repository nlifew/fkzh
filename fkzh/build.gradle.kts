plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api(libs.gson)
    implementation(libs.netty.buffer)
    implementation(libs.netty.transport)
    implementation(libs.netty.codec.http)

    // 默认运行时添加所有依赖
    implementation(libs.brotli4j)
    runtimeOnly(libs.brotli4j.linux.amd64)
    runtimeOnly(libs.brotli4j.macos.arm64)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

// 提取依赖的拷贝任务
val copyDependencies by tasks.registering(Copy::class) {
    group = "build"
    description = "拷贝桌面端依赖项(包含Native库)到 build/output 目录"

    from(configurations.runtimeClasspath.get())
    into(layout.buildDirectory.dir("output"))
}

// 新增一个任务，打包 jar 及其依赖
val desktopJar by tasks.registering(Jar::class) {
    group = "build"
    description = "专门打包用于桌面端/服务端的 JAR 包"

    dependsOn(copyDependencies)

    // 因为这是自定义任务，我们需要手动告诉它打包哪些编译好的 class 文件
    from(sourceSets.main.get().output)
    destinationDirectory.set(layout.buildDirectory.dir("output"))

    manifest {
        // 动态生成 Manifest 中的 Class-Path
        val classPath = provider {
            configurations.runtimeClasspath.get().files.joinToString(" ") { "${it.name}" }
        }
        attributes(
            "Main-Class" to "com.toybox.MainKt",
            "Class-Path" to classPath
        )
    }
}