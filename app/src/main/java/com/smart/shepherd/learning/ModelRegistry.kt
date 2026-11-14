package com.smart.shepherd.learning

import android.content.Context
import com.smart.shepherd.core.ModelKind
import org.json.JSONObject
import java.io.File

class ModelRegistry(context: Context) {
    private val root = File(context.filesDir, "model_registry").apply { mkdirs() }
    private val activeFile = File(root, "active.json")

    fun active(kind: ModelKind): String? = if (!activeFile.exists()) null else {
        JSONObject(activeFile.readText()).optString(kind.name, null)
    }

    fun activate(kind: ModelKind, modelId: String) {
        val json = if (activeFile.exists()) JSONObject(activeFile.readText()) else JSONObject()
        json.put(kind.name, modelId)
        activeFile.writeText(json.toString())
    }

    fun versions(): List<String> = root.listFiles()?.filter { it.isFile && it.extension == "tflite" }
        ?.map { it.name } ?: emptyList()
}
