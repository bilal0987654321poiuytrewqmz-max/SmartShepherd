package com.smart.shepherd

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smart.shepherd.ai.GeminiAssistant
import com.smart.shepherd.ai.GeminiKeyStore
import com.smart.shepherd.ai.GeminiSupervisor
import com.smart.shepherd.ai.SupervisorCommand
import com.smart.shepherd.core.ModelCatalog
import com.smart.shepherd.data.HerdRepository
import com.smart.shepherd.data.TrainingExampleRepository
import com.smart.shepherd.learning.ModelRegistry
import com.smart.shepherd.learning.TrainingEngine
import com.smart.shepherd.ui.DetectionOverlay
import com.smart.shepherd.vision.LiveVisionEngine
import com.smart.shepherd.vision.SheepDetector
import com.smart.shepherd.vision.toUprightBitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var identities: TextView
    private lateinit var assistantMessage: TextView
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var captureExecutor: ExecutorService
    private lateinit var training: TrainingEngine
    private lateinit var dataset: TrainingExampleRepository
    private lateinit var registry: ModelRegistry
    private var imageCapture: ImageCapture? = null
    private var vision: LiveVisionEngine? = null
    private val geminiSupervisor = GeminiSupervisor()
    private var latestTelemetry = "camera=stopped"
    private var lastGeminiRun = 0L

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status.text = "يلزم السماح بالكاميرا لاستخدام التطبيق"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.detectionOverlay)
        status = findViewById(R.id.modelStatus)
        count = findViewById(R.id.countValue)
        identities = findViewById(R.id.identityValue)
        assistantMessage = findViewById(R.id.assistantMessage)
        analysisExecutor = Executors.newSingleThreadExecutor()
        captureExecutor = Executors.newSingleThreadExecutor()
        training = TrainingEngine(this)
        dataset = TrainingExampleRepository(this)
        registry = ModelRegistry(this)

        findViewById<Button>(R.id.btnGemini).setOnClickListener { showGeminiDialog() }
        findViewById<Button>(R.id.btnHerd).setOnClickListener { showHerdSummary() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSystemStatus() }
        findViewById<Button>(R.id.btnSupervisor).setOnClickListener { runGeminiSupervisor(false) }
        findViewById<Button>(R.id.btnSaveSample).setOnClickListener { captureTrainingSample() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            try {
                vision?.let { }
                val detector = SheepDetector(this)
                vision = LiveVisionEngine(detector)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(previewView.display.rotation).build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyzeFrame(image) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
                status.text = "الكشف المحلي يعمل — EfficientDet Lite0 (~4.56 MB)"
                assistantMessage.text = "الكاميرا محلية. Gemini مشرف عند الطلب، وليس داخل حلقة كل إطار."
            } catch (e: Exception) {
                status.text = "تعذر تحميل محرك الرؤية"
                assistantMessage.text = e.localizedMessage ?: e.javaClass.simpleName
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: androidx.camera.core.ImageProxy) {
        try {
            val bitmap = image.toUprightBitmap()
            val result = vision?.analyze(bitmap)
            val tracked = result?.tracked.orEmpty()
            latestTelemetry = "sheep=${tracked.size}, dataset=${dataset.count()}, detector=efficientdet_lite0"
            runOnUiThread {
                overlay.submitDetections(tracked, bitmap.width, bitmap.height)
                count.text = "الأغنام الآن: ${tracked.size}"
                identities.text = "تتبّع مؤقت: ${tracked.size}"
                status.text = "كشف محلي نشط — ${ModelCatalog.specs.size} أدوار نماذج معرفة"
            }
            bitmap.recycle()
        } catch (e: Exception) {
            runOnUiThread { status.text = "خطأ في تحليل الإطار: ${e.localizedMessage ?: "unknown"}" }
        } finally { image.close() }
    }

    private fun captureTrainingSample() {
        val capture = imageCapture ?: run { assistantMessage.text = "الكاميرا غير جاهزة"; return }
        assistantMessage.text = "حفظ عينة محلية..."
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                try {
                    val bitmap = image.toBitmapCompat()
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    val file = dataset.saveJpeg(out.toByteArray(), "manual_camera_sample")
                    bitmap.recycle()
                    runOnUiThread { assistantMessage.text = "تم حفظ عينة محلية: ${file.name}" }
                } catch (e: Exception) { runOnUiThread { assistantMessage.text = "فشل حفظ العينة: ${e.message}" } }
                finally { image.close() }
            }
            override fun onError(exception: ImageCaptureException) { runOnUiThread { assistantMessage.text = "فشل التقاط العينة: ${exception.message}" } }
        })
    }

    private fun runGeminiSupervisor(withImage: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastGeminiRun < 5000) { assistantMessage.text = "انتظر قليلاً قبل تشغيل المشرف مرة أخرى."; return }
        lastGeminiRun = now
        val key = GeminiKeyStore(this).getKey()
        if (key.isNullOrBlank()) { assistantMessage.text = "أدخل مفتاح Gemini أولاً."; return }
        assistantMessage.text = "Gemini يراجع حالة النظام..."
        val task = suspend { geminiSupervisor.supervise(key, latestTelemetry, if (withImage) captureJpeg() else null) }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val command = task()
            executeSupervisorCommand(command)
        }
    }

    private suspend fun captureJpeg(): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        null // Keep the live loop local; image attachment is opt-in in this v0.3 UI.
    }

    private fun executeSupervisorCommand(command: SupervisorCommand) {
        when (command) {
            is SupervisorCommand.SetDetectorThreshold -> assistantMessage.text = "Gemini اقترح عتبة كشف ${command.value.coerceIn(0.1f, 0.9f)} — التغيير الآمن التالي سيُطبق عبر إعدادات الكاشف."
            is SupervisorCommand.SaveTrainingSample -> { assistantMessage.text = "Gemini اختار عينة تدريب: ${command.reason}"; captureTrainingSample() }
            is SupervisorCommand.RequestTraining -> assistantMessage.text = training.requestTraining(command.modelId).exceptionOrNull()?.message ?: "تم طلب التدريب"
            SupervisorCommand.RunEvaluation -> assistantMessage.text = "التقييم محلي: ${training.status()}"
            SupervisorCommand.NoOp -> assistantMessage.text = "Gemini لم يطلب إجراءً."
        }
    }

    private fun showGeminiDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_gemini, null)
        val key = view.findViewById<EditText>(R.id.apiKeyInput)
        val question = view.findViewById<EditText>(R.id.geminiQuestion)
        key.setText(GeminiKeyStore(this).getKey().orEmpty())
        MaterialAlertDialogBuilder(this).setTitle("Gemini المشرف") .setView(view)
            .setNegativeButton("إغلاق", null)
            .setNeutralButton("حفظ المفتاح") { _, _ -> GeminiKeyStore(this).saveKey(key.text.toString().trim()); assistantMessage.text = "تم حفظ المفتاح محلياً." }
            .setPositiveButton("اسأل Gemini") { _, _ ->
                val k = key.text.toString().trim(); if (k.isNotEmpty()) GeminiKeyStore(this).saveKey(k)
                captureAndAskGemini(question.text.toString().ifBlank { "حلل اللقطة من ناحية الأغنام فقط." })
            }.show()
    }

    private fun captureAndAskGemini(question: String) {
        val capture = imageCapture ?: run { assistantMessage.text = "الكاميرا غير جاهزة"; return }
        assistantMessage.text = "جارٍ تجهيز لقطة واحدة لـ Gemini..."
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                try {
                    val bitmap = image.toBitmapCompat(); val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out); bitmap.recycle()
                    val key = GeminiKeyStore(this@MainActivity).getKey()
                    if (key.isNullOrBlank()) runOnUiThread { assistantMessage.text = "أدخل مفتاح Gemini أولاً." }
                    else GeminiAssistant().analyze(key, out.toByteArray(), question) { result -> runOnUiThread { assistantMessage.text = result } }
                } catch (e: Exception) { runOnUiThread { assistantMessage.text = "تعذر تجهيز الصورة: ${e.message}" } }
                finally { image.close() }
            }
            override fun onError(exception: ImageCaptureException) { runOnUiThread { assistantMessage.text = "فشل التقاط الصورة: ${exception.message}" } }
        })
    }

    private fun showHerdSummary() {
        val all = HerdRepository(this).all()
        AlertDialog.Builder(this).setTitle("سجل القطيع").setMessage(if (all.isEmpty()) "لا توجد هويات مسجلة. Re-ID لم يُفعّل بعد." else all.joinToString("\n") { "• ${it.id} — ${it.label}" }).setPositiveButton("حسناً", null).show()
    }

    private fun showSystemStatus() {
        val text = buildString {
            append("Gemini: مشرف سحابي عند الطلب\n")
            append("Detector: EfficientDet Lite0 (~4.56 MB)\n")
            append("Re-ID: عقدة جاهزة، النموذج غير مضمّن بعد\n")
            append("Parts: عقدة جاهزة، النموذج غير مضمّن بعد\n")
            append("Attributes: عقدة جاهزة، النموذج غير مضمّن بعد\n")
            append("Behavior: عقدة جاهزة، النموذج غير مضمّن بعد\n")
            append("\n${training.status()}")
            append("\nإصدارات النماذج: ${registry.versions().ifEmpty { listOf("none") }.joinToString()}")
        }
        AlertDialog.Builder(this).setTitle("حالة SmartShepherd AI").setMessage(text).setPositiveButton("حسناً", null).show()
    }

    override fun onDestroy() {
        super.onDestroy(); analysisExecutor.shutdown(); captureExecutor.shutdown(); vision = null
    }
}
