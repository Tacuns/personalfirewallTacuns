plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tacu.nsfwzerotrust"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tacu.nsfwzerotrust"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "1.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Whole-app screenshot block (FLAG_SECURE). The screens with browsing data or
        // passwords (activity log + details, alerts, change history, App Lock) stay protected
        // either way (SecureScreen).
        buildConfigField("boolean", "BLOCK_ALL_SCREENSHOTS", "true")
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // App Lock uses a salted PBKDF2 password check (javax.crypto, no dependency).
    // androidx.biometric was removed: driving its fragment-based prompt from Compose
    // state failed intermittently — a dismissed prompt could skip its error callback
    // and leave the unlock screen unresponsive.

    // Offline IP-to-country lookup (MaxMind GeoLite2-Country.mmdb).
    // maxmind-db 3.x is a self-contained decoder with no Jackson dependency, so the
    // jackson-databind library that used to sit here was unused and has been removed.
    implementation("com.maxmind.db:maxmind-db:3.1.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

}

