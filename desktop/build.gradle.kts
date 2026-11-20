import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Platform-independent code is compiled straight from the Android app's sources, so
// both versions share one scheduler, parser, backup format and set of quiz games.
val appSrc = "../app/src"

val sharedMain = tasks.register<Sync>("syncSharedMain") {
    from("$appSrc/main/java") {
        include(
            "com/flashcards/data/AnswerMatcher.kt",
            "com/flashcards/data/CardFiles.kt",
            "com/flashcards/data/CardParser.kt",
            "com/flashcards/data/Cloze.kt",
            "com/flashcards/data/Entities.kt",
            "com/flashcards/data/Scheduler.kt",
            "com/flashcards/data/SyncMerge.kt",
            "com/flashcards/sync/SyncProtocol.kt",
            "com/flashcards/ocr/OcrCleaner.kt",
            "com/flashcards/ui/QuizGames.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared/main"))
}

val sharedTest = tasks.register<Sync>("syncSharedTest") {
    from("$appSrc/test/java") {
        include("com/flashcards/data/**", "com/flashcards/ocr/OcrCleanerTest.kt")
    }
    into(layout.buildDirectory.dir("generated/shared/test"))
}

kotlin {
    sourceSets {
        main { kotlin.srcDir(sharedMain) }
        test { kotlin.srcDir(sharedTest) }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.flashcards.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Flashcards"
            // Windows Installer only upgrades in place when this goes up, so bump it
            // with every build you intend to install over an existing one.
            packageVersion = "1.0.2"
            description = "Spaced-repetition flashcards"
            // jdk.httpserver carries com.sun.net.httpserver, which Wi-Fi sharing needs:
            // without it the bundled runtime has no HTTP server and sharing cannot start.
            modules("java.instrument", "jdk.unsupported", "jdk.httpserver")
            windows {
                menuGroup = "Flashcards"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f0c7a52-3c1e-4d8a-9a43-0b2f6c1d9e71"
            }
        }
    }
}
