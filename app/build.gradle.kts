import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Upload-key credentials live outside the repo. Point SARALSS_KEYSTORE_PROPERTIES at a
// keystore.properties file (storeFile is resolved relative to it); CI writes one from secrets.
val keystorePropsFile = System.getenv("SARALSS_KEYSTORE_PROPERTIES")?.let(::File)
val keystoreProps = Properties().apply {
    if (keystorePropsFile?.exists() == true) keystorePropsFile.inputStream().use(::load)
}

android {
    namespace = "com.saraltechies.saralsmssync"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.saraltechies.saralsmssync"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = keystorePropsFile!!.parentFile.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Sends messages that had to wait for a connection.
    implementation(libs.androidx.work.runtime.ktx)

    // Pairing: draw the QR code, and scan it through Play services without a camera permission.
    implementation(libs.zxing.core)
    implementation(libs.play.code.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
