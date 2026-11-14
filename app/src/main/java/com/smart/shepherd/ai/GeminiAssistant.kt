package com.smart.shepherd.ai

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAssistant {
    private val client = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS).build()

    fun analyze(apiKey: String, jpeg: ByteArray, userQuestion: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imagePart = JsonObject().apply {
                    addProperty("mime_type", "image/jpeg")
                    addProperty("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                }
                val parts = JsonArray()
                parts.add(JsonObject().apply {
                    addProperty("text",
                        "أنت مساعد لتطبيق إدارة قطيع أغنام. حلل الصورة المرئية فقط. " +
                        "اذكر ما يمكن ملاحظته، ولا تدّع معرفة هوية فردية أو عدد دقيق إذا كانت الرؤية غير واضحة. " +
                        "لا تصدر تشخيصاً طبياً. سؤال المستخدم: $userQuestion")
                })
                parts.add(JsonObject().apply { add("inline_data", imagePart) })
                val content = JsonObject().apply { add("parts", parts) }
                val contents = JsonArray().apply { add(content) }
                val body = JsonObject().apply { add("contents", contents) }
                    .toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body).build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        callback("Gemini أعاد خطأ ${response.code}: ${raw.take(500)}")
                    } else {
                        val root = JSONObject(raw)
                        val text = root.optJSONArray("candidates")
                            ?.optJSONObject(0)?.optJSONObject("content")
                            ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                        callback(text?.takeIf { it.isNotBlank() } ?: "لم يصل رد نصي من Gemini.")
                    }
                }
            } catch (e: Exception) {
                callback("تعذر الاتصال بـ Gemini: ${e.localizedMessage}")
            }
        }
    }
}
