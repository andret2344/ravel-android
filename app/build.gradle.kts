plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json, which is committed on purpose: what is in it identifies the
    // Firebase project and is no secret - Firebase's own documentation says so - and the public
    // repository has to build in CI without one. Access is guarded by the key's restrictions in the
    // Google Cloud console (package name and signing certificate), not by hiding the file.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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
        versionCode = 8
        // Not 1.0: nothing has shipped, so claiming a first release is claiming something that has
        // not happened. The version the first internal build carries should be the version its
        // crash reports carry, and "1.0" is a name to spend once.
        versionName = "0.1.0-alpha.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Every test starts from a fresh install's data - see testOptions below.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    /*
     * The release smoke tests in src/androidTest, run by hand before a signed bundle - see CLAUDE.md,
     * "Shipping a release". Not part of CI: they need an emulator, and they take minutes.
     *
     * Through the orchestrator, so each test runs in a process of its own with the app's data cleared
     * first. The tests are about what a fresh install does - the tutorial, the starting balance, level
     * one - and a second test inheriting the first one's coins and ladder would test neither.
     */
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        compose = true
        // For the one `resValue` below, `telemetry`. Off by default from AGP 9.
        resValues = true
    }

    /*
     * Every language in the base APK, not one split per language.
     *
     * Play installs only the language splits that match the phone's own languages. The game's
     * language is the player's choice, not the phone's, so a Polish choice on an English phone got
     * a locale with no strings installed for it and read English everywhere - while the boards,
     * which are not split, were Polish. Seen on a Play install of 0.1.0-alpha.6: base, en, arm64
     * and xxhdpi splits, and no pl. A universal APK built from the bundle holds every language, so
     * the pre-upload check never showed it.
     *
     * From API 33 Play would fetch a missing split for a per-app language by itself, but late, over
     * the network and only for Play installs, and below 33 never. Two languages of strings cost a
     * few kilobytes.
     */
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        /*
         * Whether this build may send analytics and crash reports at all - see
         * eu.andret.ravel.analytics.Telemetry. Only `release` does.
         *
         * Debug shares the release's application id, so its events would land in the players' data,
         * and the developer is the heaviest player there is: during the alpha the developer's own
         * testing would outweigh every tester. `measure` is the developer timing boards. Neither is
         * a player. To watch events arrive, install a release build and turn on DebugView
         * (`adb shell setprop debug.firebase.analytics.app eu.andret.ravel`).
         */
        debug {
            resValue("bool", "telemetry", "false")
        }

        release {
            resValue("bool", "telemetry", "true")
            /*
             * Shrinking is on, and it is on now rather than at release time on purpose.
             *
             * R8 is the step that fails for reasons no test and no debug build can show: it removes
             * code nothing appears to call, and "appears" is a static judgment about an app that
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
                // `proguard-android.txt` disables optimization passes wholesale, which leaves the
                // build paying R8's compile time for the shrinking alone.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        /*
         * A release build to measure on a phone: shrunk and optimized exactly like `release`, signed
         * with the debug key, carrying the debug tools.
         *
         * For timing the generator where it matters - on a mid-range phone, on the big boards past
         * level 126. A debug build is the wrong thing to time: ART runs a debuggable app markedly
         * slower, so its numbers overstate the phone. And the build from Play cannot reach those
         * levels, because the level jump is a debug-only tool (see `LevelJump.kt`).
         *
         * Its own application id, so it installs beside the game from Play instead of over it: a
         * different signing key would otherwise mean uninstalling the player's game and its
         * progress first. The debug source set supplies the jump and the shop's day controls; the
         * release set's empty twins are not part of this variant, so nothing in either pair changes.
         *
         * Never uploaded. The AdMob ids are live and this package is not the one registered with
         * them, so the shop is left alone on it.
         */
        create("measure") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".measure"
            versionNameSuffix = "-measure"
            matchingFallbacks += listOf("release")
            resValue("bool", "telemetry", "false")
            // It sends no crash reports, so there is nothing to read a mapping file against.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }

    sourceSets {
        getByName("measure") {
            kotlin.srcDir("src/debug/java")
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
    // privacy policy names, and it is what decides whether a request may be personalized at all.
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    // Analytics and crash reports - see eu.andret.ravel.analytics.Telemetry, and the privacy policy
    // for what they send. The BoM picks both versions, so the two cannot drift apart.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // The smoke tests in src/androidTest. They run against the debug variant, which is not shrunk,
    // so they say nothing about what R8 does to a release - that is still the release build
    // installed and two words dragged by hand. What they cover is everything no JVM test can reach:
    // the five back stacks, a turn of the phone mid-generation, the wide layouts, and progress
    // written to disk rather than held in memory.
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Named only for its version. Compose's test library pulls in an older Espresso, whose idling
    // check calls InputManager.getInstance - gone from recent Android, so every test failed on its
    // first wait with NoSuchMethodException before touching the app.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
