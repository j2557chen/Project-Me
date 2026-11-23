import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Room writes a description of every database version here, so upgrades between them can be
// tested (app/src/androidTest/.../MigrationTest.kt).
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.example.myfitnessapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The app's permanent ID. Android treats a different ID as a different app, so this
        // must never change once people have it installed.
        applicationId = "io.github.j2557chen.projectme"
        minSdk = 31
        targetSdk = 37
        // versionCode goes up by one for every APK given out, or phones won't take it as an
        // update. versionName is what people see.
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key lives outside the project, in ~/.projectme-signing, so it never goes
    // into git. Every update must be signed with this same key, or phones refuse it.
    // Without the file, release builds still work but come out unsigned.
    val signingFile = File(System.getProperty("user.home"), ".projectme-signing/keystore.properties")
    val releaseSigning = if (signingFile.exists()) {
        val props = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        debug {
            // Test builds install as a separate app, "Project Me (dev)", so they never touch the
            // real app's data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            signingConfig = releaseSigning
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.compose.material:material-icons-core")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.health.connect)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(libs.kotlinx.serialization.core)
    // Automatic daily backups, to a folder the user picks.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)
    // Google's fused location: GPS blended with the phone's motion sensors and Wi-Fi.
    implementation(libs.play.services.location)
}