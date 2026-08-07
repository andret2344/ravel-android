plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.andret.ravel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "eu.andret.ravel"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/**
 * Prints the board a level or a date actually gets:
 *
 *     ./gradlew :app:printPuzzle --args="--level 12"
 *     ./gradlew :app:printPuzzle --args="--daily today --quiet"
 *
 * The tool is in the unit test sources - see PrintPuzzle.kt for why - so it runs on the same
 * classpath the unit tests do, which is where the puzzle ids and the size ramp already live.
 * `--quiet` is passed through to the tool and hides the solution; Gradle's own `--quiet` silences
 * Gradle and would take the board with it.
 */
tasks.register<JavaExec>("printPuzzle") {
    group = "ravel"
    description = "Prints one level's or one day's board, with its solution."
    // The compilers, and not the test task: depending on the task whose classpath this borrows
    // would run the whole unit test suite before printing a board.
    dependsOn("compileDebugUnitTestKotlin", "bundleDebugClassesToRuntimeJar")
    // The unit test task's own classpath, rather than paths into the build directory: it is
    // assembled by the Android plugin and stays correct when the plugin changes where things land.
    classpath = files({ tasks.named<Test>("testDebugUnitTest").get().classpath })
    mainClass = "eu.andret.ravel.tools.PrintPuzzleKt"
}

dependencies {
    implementation(project(":generator"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
