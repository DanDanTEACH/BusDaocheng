plugins {
 id("com.android.application")
 id("dev.flutter.flutter-gradle-plugin")
}
android {
 namespace = "com.led.demo.phone"
 compileSdk = flutter.compileSdkVersion
 ndkVersion = flutter.ndkVersion
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 defaultConfig { applicationId = "com.led.demo.phone"; minSdk = 26; targetSdk = 35; versionCode = flutter.versionCode; versionName = "0.01" }
 sourceSets.getByName("main") { res.srcDir("../../../android/res") }
 buildTypes { release { signingConfig = signingConfigs.getByName("debug") } }
}
flutter { source = "../.." }

