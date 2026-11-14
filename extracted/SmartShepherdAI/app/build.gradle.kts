plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smart.shepherd"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smart.shepherd"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // TensorFlow Lite runtime aligned with the Task Vision 0.4.4 artifacts.
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
}

// Fetch the official EfficientDet-Lite0 COCO model before Android packaging.
// The model includes the COCO "sheep" class and is used only for category detection.
val downloadSheepDetectorModel by tasks.registering {
    val modelFile = layout.projectDirectory.file("src/main/assets/sheep_detector.tflite")
    outputs.file(modelFile)
    doLast {
        val destination = modelFile.asFile
        if (!destination.exists() || destination.length() < 1_000_000L) {
            destination.parentFile.mkdirs()
            val temporary = destination.resolveSibling("${destination.name}.part")
            val modelUrl = java.net.URL(
                "https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1?lite-format=tflite"
            )
            modelUrl.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 90_000
            }.getInputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.length() > 1_000_000L) {
                "Downloaded TFLite model is unexpectedly small; refusing to package it."
            }
            check(temporary.renameTo(destination)) {
                "Could not move the downloaded model into app/src/main/assets."
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadSheepDetectorModel)
}
