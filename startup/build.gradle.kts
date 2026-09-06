import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.HostManager
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
}

val artifactVersion = "1.1.0"
val androidMinSdk = 21
val androidMinCompileSdk = 34
val androidAar = layout.buildDirectory.file("outputs/aar/startup.aar")
val objCFramework = "Startup"
val objCHeader = layout.buildDirectory.file(
    "bin/iosSimulatorArm64/debugFramework/$objCFramework.framework/Headers/$objCFramework.h"
)

publishing {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/kunal26das/startup")
            credentials {
                username = providers.gradleProperty("githubPackagesUsername").orNull
                password = providers.gradleProperty("githubPackagesPassword").orNull
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    coordinates("io.github.kunal26das", "startup", artifactVersion)
    if (providers.gradleProperty("signPublications").map(String::toBoolean).getOrElse(false)) {
        signAllPublications()
    }
    pom {
        name.set("startup")
        description.set("A Kotlin Multiplatform port of AndroidX App Startup.")
        inceptionYear.set("2026")
        url.set("https://github.com/kunal26das/startup")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("kunal26das")
                name.set("Kunal Das")
                url.set("https://github.com/kunal26das")
            }
        }
        scm {
            url.set("https://github.com/kunal26das/startup")
            connection.set("scm:git:git://github.com/kunal26das/startup.git")
            developerConnection.set("scm:git:ssh://git@github.com/kunal26das/startup.git")
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    applyDefaultHierarchyTemplate()

    android {
        namespace = "io.github.kunal26das.startup"
        compileSdk = 37
        minSdk = androidMinSdk
        aarMetadata { minCompileSdk = androidMinCompileSdk }
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64 {
        binaries.framework(listOf(NativeBuildType.DEBUG)) { baseName = objCFramework }
    }
    iosX64()
    macosArm64()
    @Suppress("DEPRECATION") macosX64()
    linuxX64()
    mingwX64()
    js { browser(); nodejs() }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }

    sourceSets {
        val commonMain = getByName("commonMain")
        val commonTest = getByName("commonTest")
        val nonAndroidMain = create("nonAndroidMain") { dependsOn(commonMain) }
        val nonAndroidTest = create("nonAndroidTest") { dependsOn(commonTest) }
        listOf("desktop", "js", "wasmJs", "native").forEach {
            getByName("${it}Main").dependsOn(nonAndroidMain)
            getByName("${it}Test").dependsOn(nonAndroidTest)
        }
        androidMain.dependencies {
            api(libs.androidx.startup)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val checkObjCExport = tasks.register("checkObjCExport") {
    group = "verification"
    description = "Asserts that the Objective-C header exports the registration API Swift can call."
    dependsOn("linkDebugFrameworkIosSimulatorArm64")
    onlyIf { HostManager.hostIsMac }
    inputs.file(objCHeader).withPropertyName("objCHeader")
    outputs.file(layout.buildDirectory.file("reports/objCExport.txt"))
    doLast {
        val header = objCHeader.get().asFile.readText()
        val erased = listOf(
            "swift_name(\"metaData(factory:)\")",
            "swift_name(\"lazyInitializer(factory:)\")",
            "swift_name(\"remove()\")",
            "swift_name(\"initializerKey()\")",
        ).filter(header::contains)
        val callable = listOf(
            "swift_name(\"metaData(component:factory:)\")",
            "swift_name(\"lazyInitializer(component:factory:)\")",
            "swift_name(\"remove(component:)\")",
            "swift_name(\"initializerKey(initializer:)\")",
            "swift_name(\"initializerKey(kClass:)\")",
            "swift_name(\"initializerKey(objCClass:)\")",
        ).filterNot(header::contains)
        val colliding = listOf(
            "swift_name(\"Context\")",
        ).filter(header::contains)
        val aliased = listOf(
            "swift_name(\"StartupContext\")",
        ).filterNot(header::contains)
        val failures = erased.map {
            "$it is exported with its reified type argument erased, so every Swift call site names the same component."
        } + callable.map {
            "$it is missing, so Swift cannot register a component under a key it computed."
        } + colliding.map {
            "$it is exported, and a bare Context collides with UIViewControllerRepresentable.Context in every Compose Multiplatform host."
        } + aliased.map {
            "$it is missing, so the exported name no longer matches the StartupContext alias this library tells Kotlin authors to use."
        }
        outputs.files.singleFile.writeText(failures.joinToString("\n").ifEmpty { "ok" })
        check(failures.isEmpty()) {
            failures.joinToString("\n", prefix = "The Objective-C export of the registration API regressed.\n")
        }
    }
}

val checkAndroidFloors = tasks.register("checkAndroidFloors") {
    group = "verification"
    description = "Asserts that the published AAR raises neither floor above androidx.startup's."
    dependsOn("bundleAndroidMainAar")
    inputs.file(androidAar).withPropertyName("androidAar")
    outputs.file(layout.buildDirectory.file("reports/androidFloors.txt"))
    doLast {
        val archive = ZipFile(androidAar.get().asFile)
        val entries = try {
            listOf("AndroidManifest.xml", "META-INF/com/android/build/gradle/aar-metadata.properties")
                .associateWith { name ->
                    val entry = archive.getEntry(name)
                    if (entry == null) "" else archive.getInputStream(entry).reader().readText()
                }
        } finally {
            archive.close()
        }
        val minSdk = Regex("android:minSdkVersion=\"(\\d+)\"")
            .find(entries.getValue("AndroidManifest.xml"))?.groupValues?.get(1)?.toInt()
        val minCompileSdk = Regex("(?m)^minCompileSdk=(\\d+)$")
            .find(entries.getValue("META-INF/com/android/build/gradle/aar-metadata.properties"))
            ?.groupValues?.get(1)?.toInt()
        val failures = listOfNotNull(
            "The AAR declares minSdkVersion $minSdk. androidx.startup:startup-runtime declares 21, so anything above $androidMinSdk fails a lower consumer's manifest merger for a wrapper that calls nothing newer than API 1."
                .takeIf { minSdk == null || minSdk > androidMinSdk },
            "The AAR declares minCompileSdk $minCompileSdk. androidx.startup:startup-runtime declares 34, so anything above $androidMinCompileSdk forces every consumer to move compileSdk, and checkAarMetadata has no override."
                .takeIf { minCompileSdk == null || minCompileSdk > androidMinCompileSdk },
        )
        outputs.files.singleFile.writeText(failures.joinToString("\n").ifEmpty { "ok" })
        check(failures.isEmpty()) {
            failures.joinToString("\n", prefix = "The Android artifact narrowed what a consumer may be built against.\n")
        }
    }
}

tasks.named("check") { dependsOn(checkObjCExport, checkAndroidFloors) }
