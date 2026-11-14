package com.smart.shepherd.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class TrainingExampleRepository(context: Context) {
    private val root = File(context.filesDir, "ai_dataset").apply { mkdirs() }

    fun saveJpeg(bytes: ByteArray, source: String, label: String? = null): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val image = File(root, "${stamp}_${System.nanoTime()}.jpg")
        image.writeBytes(bytes)
        File(image.parentFile, image.nameWithoutExtension + ".json").writeText(
            JSONObject().put("source", source).put("label", label ?: "unlabeled").toString()
        )
        return image
    }

    fun count(): Int = root.listFiles()?.count { it.extension.lowercase() == "jpg" } ?: 0
    fun directory(): File = root
}
