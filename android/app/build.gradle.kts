plugins {
    id("com.android.application")
}

android {
    namespace = "com.nekodown.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nekodown.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.6.1"
        buildConfigField("String", "CRASH_REPORT_ENDPOINT", "\"\"")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.mpatric:mp3agic:0.9.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
