import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signing credentials stay on the developer's machine and are never committed.
val releaseSigningFile = rootProject.file("signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseSigning.getProperty(it).isNullOrBlank() } &&
    rootProject.file(releaseSigning.getProperty("storeFile", "")).isFile

android {
    namespace = "com.example.chanceride"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.chanceride"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":speech"))
    implementation(project(":driving-restrictions"))
    implementation(project(":conversation-core"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

val validateSubmissionSigning = tasks.register("validateSubmissionSigning") {
    group = "distribution"
    description = "Requires a local release signing key before packaging a submission."
    val configured = hasReleaseSigning
    doLast {
        check(configured) {
            "Release signing is missing. Configure signing.properties using signing.properties.example."
        }
    }
}

tasks.register<Copy>("packageSubmissionApk") {
    group = "distribution"
    description = "Builds a signed Release APK and copies it into the repository's apk directory."
    dependsOn(validateSubmissionSigning, "assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    val submissionDirectory = rootProject.layout.projectDirectory.dir("apk")
    into(submissionDirectory)
    rename("app-release.apk", "친해지길바래_어쩌다동승_release.apk")

    doLast {
        val apk = submissionDirectory.file("친해지길바래_어쩌다동승_release.apk").asFile
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        submissionDirectory.file("SHA256SUMS").asFile
            .writeText("$checksum  친해지길바래_어쩌다동승_release.apk\n")
    }
}
