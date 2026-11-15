plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.chanceride.speech"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    testImplementation(libs.junit)
}
