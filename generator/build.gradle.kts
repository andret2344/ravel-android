import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Java 11, not the JDK's own level: this module is consumed by :app, which compiles at 11, and an
// Android build cannot read newer class files. `release` and `-Xjdk-release` also hide the newer
// JDK's API from both compilers, so nothing here can bind to a method Android does not ship -
// java.util.List.removeLast being the one that already tried.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

application {
    mainClass = "eu.andret.ravel.generator.MainKt"
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Builds a ranked game dictionary from the raw corpora. Run after the sources or a blocklist
 * change; see DICTIONARY.md for where the inputs come from.
 */
fun registerDictionaryTask(
    language: String,
    admissibility: String,
    frequency: String,
    inflection: String,
) = tasks.register<JavaExec>("buildDictionary${language.replaceFirstChar(Char::uppercase)}") {
    group = "ravel"
    description = "Builds the $language game dictionary from its word list and frequency data."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "eu.andret.ravel.generator.BuildDictionaryKt"
    maxHeapSize = "3g"
    args = listOf(
        language,
        "$projectDir/src/main/resources/slownik-$language.txt",
        "$projectDir/blocklist-$language.txt",
        "${layout.buildDirectory.get()}/removed-$language.txt",
        "$projectDir/data/$admissibility",
        "$projectDir/data/$frequency",
        "$projectDir/data/$inflection",
    )
}

registerDictionaryTask(
    "pl",
    admissibility = "sjp-slowa.txt",
    frequency = "kwjp100-orth_lc-all.csv",
    inflection = "polimorf.tab.gz",
)
registerDictionaryTask(
    "en",
    admissibility = "enable1.txt",
    frequency = "oanc",
    inflection = "wordnet",
)

tasks.register("buildDictionaries") {
    group = "ravel"
    description = "Builds every language dictionary."
    dependsOn("buildDictionaryPl", "buildDictionaryEn")
}
