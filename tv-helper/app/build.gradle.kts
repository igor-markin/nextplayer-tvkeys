plugins {
    id("com.android.application")
}

android {
    namespace = "dev.markin.tvhelper"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.markin.tvhelper"
        minSdk = 28
        targetSdk = 37
        versionCode = 6
        versionName = "0.3.3"
        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
