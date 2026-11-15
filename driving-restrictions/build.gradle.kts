plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.chanceride.drivingrestrictions"
    compileSdk = 37

    useLibrary("android.car", false)

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
