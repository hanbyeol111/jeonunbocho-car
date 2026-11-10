plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.Jeonunbocho_car"
    compileSdk {
        version = release(37)
    }
    // 주행 규제(CarUxRestrictionsManager)용 AAOS 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.Jeonunbocho_car"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.vehicle)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}