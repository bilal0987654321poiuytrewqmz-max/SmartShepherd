package com.smart.shepherd.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SupervisorCommand {
    data class SetDetectorThreshold(val value: Float): SupervisorCommand()
    data class SaveTrainingSample(val reason: String): SupervisorCommand()
    data class RequestTraining(val modelId: String): SupervisorCommand()
    object RunEvaluation: SupervisorCommand()
    object NoOp: SupervisorCommand()
}

class GeminiSupervisor {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()

    suspend fun supervise(apiKey: String, telemetry: String, jpeg: ByteArray? = null): SupervisorCommand = withContext(Dispatchers.IO) {
        val parts = com.google.gson.JsonArray()
        parts.add(JsonObject().apply {
            addProperty("text", """
                أنت المشرف الذكي لتطبيق SmartShepherd. لا تكتب كوداً ولا تغير ملفات التطبيق.
                اختر إجراءً واحداً فقط من الأوامر الآمنة التالية:
                SET_DETECTOR_THRESHOLD(value 0.10..0.90), SAVE_TRAINING_SAMPLE(reason),
                REQUEST_TRAINING(modelId), RUN_EVALUATION, NO_OP.
                لا تطلب إرسال الفيديو الحي باستمرار. استخدم الصورة فقط إذا أرفقت.
                telemetry: $telemetry
                أعد JSON فقط بالشكل {"command":"...","value":0.0,"reason":"..."}.
            """.trimIndent())
        })
        if (jpeg != null) {
            parts.add(JsonObject().apply {
                add("inline_data", JsonObject().apply {
                    addProperty("mime_type", "image/jpeg")
                    addProperty("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                })
            })
        }
        val body = JsonObject().apply {
            add("contents", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { add("parts", parts) })
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext SupervisorCommand.NoOp
            val raw = response.body?.string().orEmpty()
            val text = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            parseCommand(text)
        }
    }

    private fun parseCommand(text: String): SupervisorCommand {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return SupervisorCommand.NoOp
        return try {
            val o = JsonParser.parseString(text.substring(start, end + 1)).asJsonObject
            when (o.get("command")?.asString) {
                "SET_DETECTOR_THRESHOLD" -> SupervisorCommand.SetDetectorThreshold(o.get("value")?.asFloat ?: 0.30f)
                "SAVE_TRAINING_SAMPLE" -> SupervisorCommand.SaveTrainingSample(o.get("reason")?.asString ?: "Gemini selected sample")
                "REQUEST_TRAINING" -> SupervisorCommand.RequestTraining(o.get("modelId")?.asString ?: "unknown")
                "RUN_EVALUATION" -> SupervisorCommand.RunEvaluation
                else -> SupervisorCommand.NoOp
            }
        } catch (_: Exception) { SupervisorCommand.NoOp }
    }
}
