plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.zeppcamera.poc"
    compileSdk = 36
    defaultConfig { applicationId = "dev.zeppcamera.poc"; minSdk = 31; targetSdk = 36; versionCode = 43; versionName = "0.9.8"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        create("publicRelease") {
            val keystorePath = System.getenv("WRISTSNAP_KEYSTORE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("WRISTSNAP_STORE_PASSWORD")
                keyAlias = System.getenv("WRISTSNAP_KEY_ALIAS") ?: "wristsnap"
                keyPassword = System.getenv("WRISTSNAP_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (!System.getenv("WRISTSNAP_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("publicRelease")
            }
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-ui:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    val camera = "1.6.2"
    implementation("androidx.camera:camera-camera2:$camera")
    implementation("androidx.camera:camera-lifecycle:$camera")
    implementation("androidx.camera:camera-view:$camera")
    implementation("androidx.camera:camera-video:$camera")
}
