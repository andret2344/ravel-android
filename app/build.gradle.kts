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
        // Not 1.0: nothing has shipped, so claiming a first release is claiming something that has
        // not happened. The version the first internal build carries should be the version its
        // crash reports carry, and "1.0" is a name to spend once.
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            /*
             * Shrinking is on, and it is on now rather than at release time on purpose.
             *
             * R8 is the step that fails for reasons no test and no debug build can show: it removes
             * code nothing appears to call, and "appears" is a static judgement about an app that
             * reaches some of its code by reflection. The moment to find that out is the moment a
             * dependency is added - the ads SDK arrived a few commits ago - and not the evening of
             * the first upload, with a store listing already written.
             *
             * It is also not optional in the end. An unshrunk build ships the whole of the ads SDK
             * and the whole of Compose, and the reason to care is not the download size on its own:
             * this app already carries a megabyte of dictionaries it genuinely needs, and spending
             * the rest of the budget on unreachable library code is spending it on nothing.
             *
             * See proguard-rules.pro for what R8 had to be told, which is nearly nothing, and for
             * the note on why that file should stay that way.
             *
             * Android Studio's security inspection says obfuscation is not enabled in the release
             * build here. It is: the inspection matches on `isMinifyEnabled`, which this DSL
             * replaced, and has not learned the new spelling. Checked rather than assumed - the
             * mapping file this build produces renames `CoinsKt.CoinAmount` to `b`, along with
             * everything else the keeps in proguard-rules.pro do not hold back.
             */
            optimization {
                enable = true
            }
            proguardFiles(
                // The optimizing variant of the default file, not the plain one: the plain
                // `proguard-android.txt` disables optimisation passes wholesale, which leaves the
                // build paying R8's compile time for the shrinking alone.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

/**
 * Times the whole ladder and prints a table:
 *
 *     ./gradlew :app:sweepLevels --args="--to 300" --quiet > sweep.csv
 *
 * The CSV goes to stdout and the summary to stderr, so that line leaves a file holding nothing but
 * the table and still prints the summary to the terminal. See SweepLevels.kt for why the tool
 * reaches under `Ravel` instead of calling `boardOrNull` the way the game does.
 *
 * Gradle's own `--quiet` is wanted here, unlike in `printPuzzle`, and for the opposite reason: its
 * task logging goes to stdout and would otherwise be interleaved into the CSV, while the summary
 * this task writes is on stderr and comes through regardless. `printPuzzle` takes its `--quiet`
 * inside `--args` because there the thing being hidden is the solution, not Gradle.
 */
tasks.register<JavaExec>("sweepLevels") {
    group = "ravel"
    description = "Times every level of the ladder and reports the cost per grid size."
    dependsOn("compileDebugUnitTestKotlin", "bundleDebugClassesToRuntimeJar")
    classpath = files({ tasks.named<Test>("testDebugUnitTest").get().classpath })
    mainClass = "eu.andret.ravel.tools.SweepLevelsKt"
    // A sweep holds one dictionary and builds boards up to 10x10; the default heap is enough for
    // the game but not for a run that never gives the trie back between levels.
    maxHeapSize = "2g"
}

/**
 * Pins the tool R8 uses to fold Compose's group keys into the release mapping file to the Kotlin
 * version this project actually compiles with.
 *
 * The Compose compiler plugin asks for `compose-group-mapping` at the Kotlin version it *thinks* is
 * running, and under AGP 9's built-in Kotlin that is AGP's bundled 2.2.10 - a version that artifact
 * was never published at, so every release build failed to resolve it while debug builds, which do
 * not shrink, never noticed. Turning the feature off (`composeCompiler { includeComposeMappingFile =
 * false }`) would also have worked, at the price of release stack traces through composables that
 * cannot be read back.
 *
 * Delete this once the plugin resolves the right version by itself: comment it out, run
 * `:app:assembleRelease`, and if it passes the bug is gone.
 */
configurations.matching { it.name == "composeMappingProducerClasspath" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
            useVersion(libs.versions.kotlin.get())
            because("the Compose plugin requests AGP's bundled Kotlin version, which was never published")
        }
    }
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
    // Rewarded ads, and the consent form that has to run before they are requested. The consent SDK
    // is a separate artifact from the ads SDK on purpose - it is the Google-certified CMP the
    // privacy policy names, and it is what decides whether a request may be personalised at all.
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // No androidTest source set, and therefore no instrumentation runner and no Espresso. There
    // never was a test in it - the two files here were the ones Android Studio generates, one
    // asserting that 2 + 2 is 4 and one asserting the package name - and a build file that keeps
    // declaring the dependencies reads as a project with device coverage that is currently empty,
    // which is a worse claim than an honest zero.
    //
    // The instrumented test worth writing is a smoke test that launches the release variant, since
    // that is the build R8 can break and no unit test can see. Writing it means an emulator in CI;
    // until then the release build is installed and opened by hand before every upload.
}
