plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val rustJniLibs = layout.buildDirectory.dir("rustJniLibs")
val engineAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val splitApks = providers.gradleProperty("splitApks").isPresent

android {
    namespace = "info.mayuragarkar.calculator"
    compileSdk = 37

    defaultConfig {
        applicationId = "info.mayuragarkar.calculator"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = providers.gradleProperty("versionName").getOrElse("2.0.0")
        if (!splitApks) ndk { abiFilters += engineAbis }
    }

    splits {
        abi {
            isEnable = splitApks
            reset()
            include(*engineAbis.toTypedArray())
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("SIGNING_KEYSTORE").orNull?.let(::file)
            storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
    }
    ndkVersion = "30.0.16248370"
    sourceSets {
        getByName("main") {
            jniLibs.directories.add(rustJniLibs.get().asFile.path)
        }
    }
}

kotlin {
    jvmToolchain(21)
}

val rustDir = rootProject.layout.projectDirectory.dir("rust")
val ndkDir = androidComponents.sdkComponents.ndkDirectory

fun Exec.rustInputs() {
    workingDir(rustDir)
    inputs.dir(rustDir.dir("src"))
    inputs.files(rustDir.file("Cargo.toml"), rustDir.file("Cargo.lock"))
}

val cargoAndroid = tasks.register<Exec>("cargoAndroid") {
    rustInputs()
    outputs.dir(rustJniLibs)
    commandLine(
        listOf("cargo", "ndk") + engineAbis.flatMap { listOf("-t", it) } +
            listOf("--platform", "26", "-o", rustJniLibs.get().asFile.path, "build", "--release"),
    )
    val ndk = ndkDir
    doFirst { environment("ANDROID_NDK_HOME", ndk.get().asFile.path) }
}

val cargoHost = tasks.register<Exec>("cargoHost") {
    rustInputs()
    outputs.dir(rustDir.dir("target/release"))
    commandLine("cargo", "build", "--release")
}

tasks.named("preBuild") { dependsOn(cargoAndroid) }
tasks.withType<Test>().configureEach {
    dependsOn(cargoHost)
    systemProperty("java.library.path", rustDir.dir("target/release").asFile.path)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
