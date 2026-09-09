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

val artifactVersion = "3.0.1"
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
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("nativeMain").dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val androidxStartupCoordinate = libs.androidx.startup.get().let { "${it.module}:${it.version}" }

val androidxStartupAarScope = configurations.dependencyScope("androidxStartupAarScope")

val androidxStartupAar = configurations.resolvable("androidxStartupAar") {
    extendsFrom(androidxStartupAarScope.get())
    isTransitive = false
}

dependencies.addProvider(
    androidxStartupAarScope.name,
    libs.androidx.startup.map { "${it.module}:${it.version}@aar" },
)

fun androidFloors(aar: File): Pair<Int?, Int?> {
    val archive = ZipFile(aar)
    val entries = try {
        listOf("AndroidManifest.xml", "META-INF/com/android/build/gradle/aar-metadata.properties")
            .associateWith { name ->
                val entry = archive.getEntry(name)
                if (entry == null) "" else archive.getInputStream(entry).reader().readText()
            }
    } finally {
        archive.close()
    }
    return Regex("android:minSdkVersion=\"(\\d+)\"")
        .find(entries.getValue("AndroidManifest.xml"))?.groupValues?.get(1)?.toInt() to
        Regex("(?m)^minCompileSdk=(\\d+)$")
            .find(entries.getValue("META-INF/com/android/build/gradle/aar-metadata.properties"))
            ?.groupValues?.get(1)?.toInt()
}

val androidFloorDocumentation = mapOf(
    rootProject.layout.projectDirectory.file("README.md") to Regex(
        """two Android floors that dependency does:\s+\*\*`minSdk`\s+(\d+)\*\*\s+and\s+\*\*`minCompileSdk`\s+(\d+)\*\*""",
    ),
    rootProject.layout.projectDirectory.file("CLAUDE.md") to Regex(
        """(?m)^-\s+`minSdk\s*=\s*(\d+)`\s+and\s+`aarMetadata\s*\{\s*minCompileSdk\s*=\s*(\d+)\s*\}`,\s+which are exactly the floors""",
    ),
)

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
        ).filterNot(header::contains)
        val colliding = listOf(
            "swift_name(\"Context\")",
        ).filter(header::contains)
        val aliased = listOf(
            "swift_name(\"StartupContext\")",
            "swift_name(\"StartupTask\")",
            "swift_name(\"initializeComponentOrNull(component:)\")",
        ).filterNot(header::contains)
        val untyped = listOf(
            "StartupStartupTask *> *)wave",
        ).filterNot(header::contains)
        val unthrowing = listOf(
            "swift_name(\"install(context:manifest:)\")",
            "swift_name(\"install(context:manifest:runner:)\")",
            "swift_name(\"getInstance(context:)\")",
            "swift_name(\"initializeComponent(component:)\")",
            "swift_name(\"initializeComponentOrNull(component:)\")",
            "swift_name(\"isEagerlyInitialized(component:)\")",
            "swift_name(\"invoke()\")",
            "swift_name(\"plan(manifest:roots:satisfied:)\")",
            "swift_name(\"validate(manifest:)\")",
        ).filterNot { name ->
            header.lineSequence().any { it.contains(name) && it.contains("(NSError") }
        }
        val failures = erased.map {
            "$it is exported with its reified type argument erased, so every Swift call site names the same component."
        } + callable.map {
            "$it is missing, so Swift cannot register a component under a key it computed."
        } + colliding.map {
            "$it is exported, and a bare Context collides with UIViewControllerRepresentable.Context in every Compose Multiplatform host."
        } + aliased.map {
            "$it is missing, so a name this library documents for Swift is not in the header."
        } + untyped.map {
            "$it is missing, so WaveRunner no longer hands Swift tasks it can tell apart."
        } + unthrowing.map {
            "$it carries no NSError parameter, so a StartupException terminates the process instead of reaching a Swift catch."
        }
        outputs.files.singleFile.writeText(failures.joinToString("\n").ifEmpty { "ok" })
        check(failures.isEmpty()) {
            failures.joinToString("\n", prefix = "The Objective-C export of the registration API regressed.\n")
        }
    }
}

val checkAndroidFloors = tasks.register("checkAndroidFloors") {
    group = "verification"
    description = "Asserts that this library's AAR, androidx.startup's AAR and the documented pair declare the same two Android floors."
    dependsOn("bundleAndroidMainAar")
    inputs.file(androidAar).withPropertyName("androidAar")
    inputs.files(androidxStartupAar).withPropertyName("androidxStartupAar")
    inputs.files(androidFloorDocumentation.keys)
        .withPropertyName("androidFloorDocumentation")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(layout.buildDirectory.file("reports/androidFloors.txt"))
    doLast {
        val (ourMinSdk, ourMinCompileSdk) = androidFloors(androidAar.get().asFile)
        val (theirMinSdk, theirMinCompileSdk) = androidFloors(androidxStartupAar.get().singleFile)
        val failures = listOfNotNull(
            "startup.aar carries no android:minSdkVersion in its AndroidManifest.xml, so the artifact records no device range at all and nothing here can be held against $androidxStartupCoordinate."
                .takeIf { ourMinSdk == null },
            "$androidxStartupCoordinate carries no android:minSdkVersion in its AndroidManifest.xml, so the floor this library mirrors cannot be read and nothing here can say whether the two artifacts still agree."
                .takeIf { theirMinSdk == null },
            "startup.aar declares minSdkVersion $ourMinSdk and $androidxStartupCoordinate declares $theirMinSdk, so this artifact narrows the device range a plain androidx.startup application already reaches and fails a lower consumer's manifest merger, for a wrapper that calls nothing newer than API 1."
                .takeIf { ourMinSdk != null && theirMinSdk != null && ourMinSdk > theirMinSdk },
            "startup.aar declares minSdkVersion $ourMinSdk and $androidxStartupCoordinate declares $theirMinSdk, which api(libs.androidx.startup) puts in every consumer's graph, so the manifest merger enforces $theirMinSdk however low this artifact goes and the lower number is one it cannot keep."
                .takeIf { ourMinSdk != null && theirMinSdk != null && ourMinSdk < theirMinSdk },
            "startup.aar carries no minCompileSdk in META-INF/com/android/build/gradle/aar-metadata.properties, so the artifact records no compileSdk requirement at all and nothing here can be held against $androidxStartupCoordinate."
                .takeIf { ourMinCompileSdk == null },
            "startup.aar declares minCompileSdk $ourMinCompileSdk and $androidxStartupCoordinate declares none at all, so adopting this library constrains a consumer's compileSdk where plain androidx.startup constrains it not at all, and checkAarMetadata is unconditional and has no override."
                .takeIf { ourMinCompileSdk != null && theirMinCompileSdk == null },
            "startup.aar declares minCompileSdk $ourMinCompileSdk and $androidxStartupCoordinate declares $theirMinCompileSdk, so this artifact forces every consumer to move compileSdk where that dependency does not, and checkAarMetadata has no override."
                .takeIf { ourMinCompileSdk != null && theirMinCompileSdk != null && ourMinCompileSdk > theirMinCompileSdk },
            "startup.aar declares minCompileSdk $ourMinCompileSdk and $androidxStartupCoordinate declares $theirMinCompileSdk, which api(libs.androidx.startup) puts in every consumer's graph, so checkAarMetadata enforces $theirMinCompileSdk against them whatever this artifact says, and this one understates the floor adopting it imposes."
                .takeIf { ourMinCompileSdk != null && theirMinCompileSdk != null && ourMinCompileSdk < theirMinCompileSdk },
        ) + androidFloorDocumentation.flatMap { (document, pattern) ->
            val name = document.asFile.name
            val text = runCatching { document.asFile.readText() }.getOrNull()
                ?: return@flatMap listOf("$name could not be read, so its documented Android floors cannot be checked.")
            val match = pattern.findAll(text).singleOrNull()
            val documentedMinSdk = match?.groupValues?.get(1)?.toIntOrNull()
            val documentedMinCompileSdk = match?.groupValues?.get(2)?.toIntOrNull()
            if (documentedMinSdk == null || documentedMinCompileSdk == null) {
                return@flatMap listOf("$name must contain exactly one readable minSdk and minCompileSdk pair in its Android floor statement; examples and release history do not establish the documented requirements.")
            }
            listOfNotNull(
                "$name documents minSdk $documentedMinSdk, but startup.aar declares ${ourMinSdk ?: "none"} and $androidxStartupCoordinate declares ${theirMinSdk ?: "none"}. Update the documented requirement with an intentional floor change, or hold libs.versions.toml at a version that has not moved."
                    .takeIf { documentedMinSdk != ourMinSdk || documentedMinSdk != theirMinSdk },
                "$name documents minCompileSdk $documentedMinCompileSdk, but startup.aar declares ${ourMinCompileSdk ?: "none"} and $androidxStartupCoordinate declares ${theirMinCompileSdk ?: "none"}. Update the documented requirement with an intentional floor change, or hold libs.versions.toml at a version that has not moved."
                    .takeIf { documentedMinCompileSdk != ourMinCompileSdk || documentedMinCompileSdk != theirMinCompileSdk },
            )
        }
        outputs.files.singleFile.writeText(failures.joinToString("\n").ifEmpty { "ok" })
        check(failures.isEmpty()) {
            failures.joinToString(
                "\n",
                prefix = "The two Android floors no longer agree. startup.aar, $androidxStartupCoordinate and the pair README.md and CLAUDE.md document have to be the same two numbers.\n",
            )
        }
    }
}

tasks.named("check") { dependsOn(checkObjCExport, checkAndroidFloors) }
