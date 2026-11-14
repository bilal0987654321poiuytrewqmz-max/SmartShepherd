# Smart Shepherd AI — الراعي الذكي (المرحلة الثانية)

مشروع Android تجريبي متقدم للكشف المحلي عن الأغنام وتتبعها أثناء بقاء الكاميرا مفتوحة.

## ما الذي أضيف في هذه المرحلة؟

- تحليل حي لإطارات الكاميرا عبر CameraX `ImageAnalysis` مع سياسة `KEEP_ONLY_LATEST`.
- كاشف EfficientDet-Lite0 باستخدام TensorFlow Lite Task Vision.
- نموذج COCO يتضمن فئة `sheep`؛ يجري تنزيله تلقائياً أثناء Gradle `preBuild` من مستودع TensorFlow Hub الرسمي، ثم يضمّنه في APK.
- فلترة النتائج إلى فئة sheep ورسم مربعات حولها مباشرة فوق معاينة الكاميرا.
- متعقب IoU خفيف يربط الكشف نفسه تقريبياً بين الإطارات ويعرض أرقام جلسة مثل `جلسة #1`.
- عدّ الأغنام المكتشفة في الإطار الحالي وعدّ المسارات التي ظهرت خلال جلسة الكاميرا.
- إبقاء تحليل Gemini للقطة اليدوية منفصلاً عن الكشف المحلي المستمر.

## حدود مهمة جداً

1. **هذا كاشف فئة وليس نظام هوية فردية.** يستطيع النموذج اكتشاف أن الجسم خروف، لكنه لا يعرف أن هذا الخروف هو نفسه الذي شاهده التطبيق قبل دقائق.
2. أرقام `جلسة #` هي معرّفات تتبّع مؤقتة مبنية على تداخل المربعات (IoU)، وقد تتبدل عند تداخل الأغنام أو خروجها من المشهد أو إعادة تشغيل الكاميرا.
3. لا يوجد في هذه المرحلة نموذج Sheep Re-ID مخصص، ولا قاعدة بصمات مظهرية موثوقة، ولا تدريب تلقائي لأوزان الشبكة.
4. نموذج COCO عام؛ الأداء يتأثر بالمسافة والإضاءة والازدحام وزاوية التصوير، ويجب اختباره ميدانياً قبل الاعتماد على العدّ.
5. لا يمكن وصف APK بأنه منتج ميداني معتمد قبل اختبار البناء على جهاز فعلي، وقياس الدقة والسرعة والحرارة واستهلاك البطارية.

## متطلبات البناء

- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- اتصال إنترنت في أول بناء لتنزيل ملف النموذج (بعدها يبقى داخل مجلد assets).

```bash
gradle assembleDebug
```

APK:
`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

ارفع المجلد كاملاً إلى مستودع GitHub وشغّل workflow باسم **Android CI**. يقوم Gradle بتنزيل النموذج عند الحاجة ثم يبني APK Debug ويرفعه كـ artifact.

## بنية محرك الرؤية

- `vision/SheepDetector.kt`: تحميل نموذج TFLite وتنفيذ كشف فئة sheep.
- `vision/SheepTracker.kt`: ربط الكشوف بين الإطارات باستخدام IoU؛ أرقام مؤقتة للجلسة فقط.
- `vision/VisionModels.kt`: نماذج بيانات الكشف والمسارات.
- `vision/ImageProxyBitmap.kt`: تحويل إطارات RGBA من CameraX إلى Bitmap مع تصحيح الدوران.
- `ui/DetectionOverlay.kt`: رسم المربعات والأرقام فوق المعاينة.
- `MainActivity.kt`: ربط الكاميرا والكاشف والمتعقب والواجهة.

## الخطوة التالية لإكمال التعرف الفردي

نحتاج صوراً ومقاطع حقيقية من القطيع نفسه، ملتقطة في ظروف مختلفة وموسومة لكل خروف بمعرّف صحيح، ثم تدريب نموذج embedding لـ Sheep Re-ID واختباره على صور لم تدخل التدريب. يجب أن يسمح النظام بنتيجة «غير معروف» عندما تكون الثقة منخفضة، وألا يدمج خروفين في هوية واحدة بسبب تشابه اللون أو الصوف.

## الأمان والخصوصية

- معالجة الكشف محلياً على الهاتف.
- لا تُرسل إطارات الفيديو إلى Gemini تلقائياً؛ الإرسال يحدث فقط عند طلب تحليل لقطة.
- لا تضع مفاتيح API في GitHub. المفتاح داخل تطبيق العميل قابل للاستخراج رغم التخزين المشفر.
- الكاميرا لا تستمر بعد إغلاق التطبيق؛ Android يفرض قيوداً على الكاميرا والعمل في الخلفية.

## المصادر التقنية

- TensorFlow Lite Android object detection guide: https://developers.google.com/codelabs/tflite-object-detection-android
- TensorFlow Lite Task Library object detector requirements: https://android.googlesource.com/platform/external/tensorflow/+/HEAD/tensorflow/lite/g3doc/inference_with_metadata/task_library/object_detector.md
- TensorFlow Lite Android streaming camera example: https://github.com/tensorflow/examples/tree/master/lite/examples/object_detection/android
