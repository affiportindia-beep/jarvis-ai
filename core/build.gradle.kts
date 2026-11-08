// Pure Kotlin module: assistant logic that must work without Android, unit-tested in CI.
plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    // Live web responses fetched by CI (see android.yml); the test is skipped when unset.
    System.getenv("RESEARCH_FIXTURE_DIR")?.let { environment("RESEARCH_FIXTURE_DIR", it) }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
