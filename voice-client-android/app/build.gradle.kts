plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="dev.truc5738.voicechannel.android"
    compileSdk=36
    defaultConfig {
        applicationId="dev.truc5738.voicechannel"
        minSdk=26
        targetSdk=36
        versionCode=1
        versionName="1.0.0"
    }
}
