import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

val desktopMainClass = "io.github.kunal26das.startup.sample.MainKt"
val nativeEntryPoint = "io.github.kunal26das.startup.sample.main"
val nativeBuildTypes = listOf(NativeBuildType.DEBUG)
val iosApplication = "SampleApp"
val iosApplicationBundle = layout.buildDirectory.dir("iosApp/$iosApplication.app")
val objCFramework = "Sample"
val exportedHeader = layout.buildDirectory.file(
    "bin/iosSimulatorArm64/exportedDebugFramework/$objCFramework.framework/Headers/$objCFramework.h"
)
val bareHeader = layout.buildDirectory.file(
    "bin/iosSimulatorArm64/bareDebugFramework/$objCFramework.framework/Headers/$objCFramework.h"
)

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    applyDefaultHierarchyTemplate()

    android {
        namespace = "io.github.kunal26das.startup.sample"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun { mainClass.set(desktopMainClass) }
    }
    iosArm64 { binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint } }
    iosSimulatorArm64 {
        binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint }
        binaries.framework("exported", nativeBuildTypes) {
            baseName = objCFramework
            export(project(":startup"))
        }
        binaries.framework("bare", nativeBuildTypes) { baseName = objCFramework }
    }
    iosX64 { binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint } }
    macosArm64 { binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint } }
    @Suppress("DEPRECATION")
    macosX64 { binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint } }
    linuxX64 { binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint } }
    mingwX64 { binaries.executable(nativeBuildTypes) { entryPoint = nativeEntryPoint } }
    js {
        browser()
        nodejs()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        binaries.executable()
    }

    sourceSets {
        val commonMain = getByName("commonMain")
        val commonTest = getByName("commonTest")
        val nonAndroidMain = create("nonAndroidMain") { dependsOn(commonMain) }
        val nonAndroidTest = create("nonAndroidTest") { dependsOn(commonTest) }
        listOf("desktop", "js", "wasmJs", "native").forEach {
            getByName("${it}Main").dependsOn(nonAndroidMain)
            getByName("${it}Test").dependsOn(nonAndroidTest)
        }
        val consoleMain = create("consoleMain") { dependsOn(nonAndroidMain) }
        listOf("desktop", "js", "wasmJs", "macos", "linux", "mingw").forEach {
            getByName("${it}Main").dependsOn(consoleMain)
        }
        commonMain.dependencies {
            api(project(":startup"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val assembleIosSimulatorApp = tasks.register<Sync>("assembleIosSimulatorApp") {
    group = "build"
    description = "Lays out the iOS simulator app bundle around the linked Kotlin/Native binary."
    dependsOn("linkDebugExecutableIosSimulatorArm64")
    from(layout.buildDirectory.file("bin/iosSimulatorArm64/debugExecutable/sample.kexe")) {
        rename { iosApplication }
        filePermissions { unix("755") }
    }
    from(layout.projectDirectory.file("iosApp/Info.plist"))
    into(iosApplicationBundle)
}

tasks.register<Exec>("iosSimulatorApp") {
    group = "build"
    description = "Builds the signed iOS simulator app bundle, ready for xcrun simctl install."
    dependsOn(assembleIosSimulatorApp)
    commandLine("codesign", "--force", "--sign", "-", iosApplicationBundle.get().asFile.path)
}

listOf("js", "wasmJs").forEach { target ->
    listOf("Node", "Browser").forEach { environment ->
        tasks.register("${target}${environment}Run") {
            group = "run"
            description = "Runs the sample on $target in ${environment.lowercase()}."
            dependsOn("${target}${environment}DevelopmentRun")
        }
    }
}

tasks.withType<Test>().configureEach {
    val manifest = layout.projectDirectory.file("src/androidMain/AndroidManifest.xml")
    inputs.file(manifest)
        .withPropertyName("androidMainManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("startup.sample.androidManifest", manifest.asFile.absolutePath)
}

val checkConsumerObjCExport = tasks.register("checkConsumerObjCExport") {
    group = "verification"
    description = "Asserts what a consumer's own framework exports with and without export(:startup)."
    dependsOn(
        "linkExportedDebugFrameworkIosSimulatorArm64",
        "linkBareDebugFrameworkIosSimulatorArm64",
    )
    onlyIf { HostManager.hostIsMac }
    inputs.file(exportedHeader).withPropertyName("exportedHeader")
    inputs.file(bareHeader).withPropertyName("bareHeader")
    outputs.file(layout.buildDirectory.file("reports/consumerObjCExport.txt"))
    doLast {
        val exported = exportedHeader.get().asFile.readText()
        val bare = bareHeader.get().asFile.readText()
        val missing = listOf(
            "swift_name(\"Initializer\")",
            "swift_name(\"BaseInitializer\")",
            "swift_name(\"InitializerKey\")",
            "swift_name(\"InitializerKeyKt\")",
            "swift_name(\"initializerKey(initializer:)\")",
            "swift_name(\"StartupContext\")",
            "swift_name(\"DefaultContext\")",
            "swift_name(\"Startup\")",
            "swift_name(\"StartupManifest\")",
            "swift_name(\"StartupManifestBuilder\")",
            "swift_name(\"AppInitializer\")",
        ).filterNot(exported::contains)
        val unprefixed = listOf(
            "swift_name(\"StartupInitializer\")",
            "swift_name(\"StartupStartupManifest\")",
        ).filterNot(bare::contains)
        val leaked = listOf(
            "swift_name(\"InitializerKeyKt\")",
        ).filter(bare::contains)
        val failures = missing.map {
            "$it is missing from the framework that exports :startup, so README's Swift snippet does not compile against it."
        } + unprefixed.map {
            "$it is missing from the framework that only depends on :startup, so the module-prefixed names README warns about are no longer what a consumer gets by default."
        } + leaked.map {
            "$it reaches the framework that only depends on :startup, so README's reason for requiring export() is stale."
        }
        outputs.files.singleFile.writeText(failures.joinToString("\n").ifEmpty { "ok" })
        check(failures.isEmpty()) {
            failures.joinToString("\n", prefix = "What a consumer's own framework exports changed.\n")
        }
    }
}

tasks.named("check") { dependsOn(checkConsumerObjCExport) }
