package com.smart.shepherd.learning

import android.content.Context
import com.smart.shepherd.core.ModelKind
import org.json.JSONObject
import java.io.File

class ModelRegistry(context: Context) {

    private val root =
        File(context.filesDir, "model_registry").apply {
            mkdirs()
        }

    private val activeFile =
        File(root, "active.json")

    fun active(kind: ModelKind): String? {
        if (!activeFile.exists()) {
            return null
        }

        return try {
            JSONObject(activeFile.readText())
                .optString(kind.name)
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun activate(
        kind: ModelKind,
        modelId: String
    ) {
        val json =
            if (activeFile.exists()) {
                JSONObject(activeFile.readText())
            } else {
                JSONObject()
            }

        json.put(kind.name, modelId)
        activeFile.writeText(json.toString())
    }

    fun versions(): List<String> {
        return root.listFiles()
            ?.filter {
                it.isFile && it.extension.equals("tflite", ignoreCase = true)
            }
            ?.map { it.name }
            ?: emptyList()
    }
}
