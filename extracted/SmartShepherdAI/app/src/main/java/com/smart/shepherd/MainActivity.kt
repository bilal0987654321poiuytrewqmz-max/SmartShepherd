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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smart.shepherd.ai.GeminiAssistant
import com.smart.shepherd.ai.GeminiKeyStore
import com.smart.shepherd.data.HerdRepository
import com.smart.shepherd.ui.DetectionOverlay
import com.smart.shepherd.vision.SheepDetector
import com.smart.shepherd.vision.SheepTracker
import com.smart.shepherd.vision.toUprightBitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var identities: TextView
    private lateinit var assistantMessage: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var analysisExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var sheepDetector: SheepDetector? = null
    private val sheepTracker = SheepTracker()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else status.text = "يلزم السماح بالكاميرا لاستخدام التطبيق"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.detectionOverlay)
        status = findViewById(R.id.modelStatus)
        count = findViewById(R.id.countValue)
        identities = findViewById(R.id.identityValue)
        assistantMessage = findViewById(R.id.assistantMessage)
        cameraExecutor = Executors.newSingleThreadExecutor()
        analysisExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.btnGemini).setOnClickListener { showGeminiDialog() }
        findViewById<Button>(R.id.btnHerd).setOnClickListener { showHerdSummary() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }

        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)

        updateHerdCounters()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                sheepDetector?.close()
                sheepDetector = SheepDetector(this)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyzeFrame(image) }
                imageAnalysis = analysis

                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
                )
                status.text = "محرك الكشف: EfficientDet Lite0 — فئة الأغنام"
                assistantMessage.text = "الكشف المحلي يعمل. أرقام الجلسة للتتبّع المؤقت وليست هويات دائمة."
            } catch (e: Exception) {
                sheepDetector?.close()
                sheepDetector = null
                status.text = "تعذر تحميل نموذج الكشف"
                assistantMessage.text = "خطأ تشغيل المحرك: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: androidx.camera.core.ImageProxy) {
        try {
            val bitmap = image.toUprightBitmap()
            val detections = sheepDetector?.detect(bitmap).orEmpty()
            val tracked = sheepTracker.update(detections)
            runOnUiThread {
                overlay.submitDetections(tracked, bitmap.width, bitmap.height)
                count.text = "الأغنام الآن: ${tracked.size}"
                identities.text = "تتبّع الجلسة: ${sheepTracker.totalSeenThisSession}"
                if (tracked.isNotEmpty()) {
                    status.text = "الكشف نشط — ثقة أعلى من 30%"
                }
            }
            bitmap.recycle()
        } catch (e: Exception) {
            runOnUiThread {
                status.text = "حدث خطأ أثناء تحليل الإطار"
                assistantMessage.text = e.localizedMessage ?: "تعذر تحليل صورة الكاميرا"
            }
        } finally {
            image.close()
        }
    }

    private fun showGeminiDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_gemini, null)
        val keyInput = view.findViewById<EditText>(R.id.apiKeyInput)
        val questionInput = view.findViewById<EditText>(R.id.geminiQuestion)
        keyInput.setText(GeminiKeyStore(this).getKey().orEmpty())

        MaterialAlertDialogBuilder(this)
            .setTitle("مساعد Gemini")
            .setView(view)
            .setNegativeButton("إغلاق", null)
            .setNeutralButton("حفظ المفتاح") { _, _ ->
                val key = keyInput.text.toString().trim()
                if (key.isNotEmpty()) {
                    GeminiKeyStore(this).saveKey(key)
                    assistantMessage.text = "تم حفظ المفتاح محلياً. يمكنك سؤال Gemini."
                }
            }
            .setPositiveButton("تحليل لقطة") { _, _ ->
                val key = keyInput.text.toString().trim()
                if (key.isNotEmpty()) GeminiKeyStore(this).saveKey(key)
                val question = questionInput.text.toString().ifBlank {
                    "حلل هذه اللقطة من ناحية وجود الأغنام، ولا تخمّن هويات فردية."
                }
                captureAndAskGemini(question)
            }.show()
    }

    private fun captureAndAskGemini(question: String) {
        val capture = imageCapture ?: run {
            assistantMessage.text = "الكاميرا غير جاهزة"
            return
        }
        assistantMessage.text = "جارٍ التقاط لقطة وتحضيرها..."
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                try {
                    val bitmap = image.toBitmapCompat()
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                    val bytes = stream.toByteArray()
                    runOnUiThread { assistantMessage.text = "جارٍ سؤال Gemini..." }
                    val key = GeminiKeyStore(this@MainActivity).getKey()
                    if (key.isNullOrBlank()) {
                        runOnUiThread { assistantMessage.text = "أدخل مفتاح Gemini أولاً." }
                    } else {
                        GeminiAssistant().analyze(key, bytes, question) { result ->
                            runOnUiThread { assistantMessage.text = result }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { assistantMessage.text = "تعذر تجهيز الصورة: ${e.message}" }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { assistantMessage.text = "فشل التقاط الصورة: ${exception.message}" }
            }
        })
    }

    private fun showHerdSummary() {
        val all = HerdRepository(this).all()
        val text = if (all.isEmpty()) "لا توجد هويات مسجلة بعد. لا يتم إنشاء هوية تلقائياً قبل وجود نموذج تعرف موثوق."
        else all.joinToString("\n") { "• ${it.id} — ${it.label}" }
        AlertDialog.Builder(this).setTitle("سجل القطيع").setMessage(text)
            .setPositiveButton("حسناً", null).show()
    }

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle("إعدادات النظام")
            .setMessage(
                "الكاميرا: تحليل إطارات محلياً عبر CameraX\n" +
                "الكاشف: EfficientDet Lite0 / COCO — فئة sheep\n" +
                "التتبّع: ربط مؤقت بين الإطارات بواسطة IoU\n" +
                "أرقام الجلسة: مؤقتة وتتغير بعد إعادة تشغيل الجلسة\n" +
                "Gemini: تحليل لقطة عند الطلب\n\n" +
                "لا يوجد حتى الآن نموذج يميّز الخروف نفسه بعد مغادرته المشهد؛ ذلك يحتاج Sheep Re-ID وبيانات خاصة بقطيعك."
            )
            .setPositiveButton("موافق", null).show()
    }

    private fun updateHerdCounters() {
        val all = HerdRepository(this).all()
        count.text = "الأغنام الآن: --"
        identities.text = "سجلات القطيع: ${all.size}"
    }

    override fun onDestroy() {
        super.onDestroy()
        imageAnalysis?.clearAnalyzer()
        cameraExecutor.shutdown()
        // Queue model disposal after any in-flight inference on the same serial executor.
        analysisExecutor.execute { sheepDetector?.close() }
        analysisExecutor.shutdown()
    }
}
