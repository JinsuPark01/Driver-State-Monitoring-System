package com.example.android_front.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

// -----------------------------------------------------
// 🔹 감지 결과 구조체
// -----------------------------------------------------
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val box: RectF
)

// -----------------------------------------------------
// 🔹 모델 종류 (확장성 고려)
// -----------------------------------------------------
enum class ModelType {
    CIGARETTE,
    PHONE,
    SEATBELT
}

// -----------------------------------------------------
// 🔹 ModelHandler: 모든 모델을 관리하는 싱글톤 객체
// -----------------------------------------------------
object ModelHandler {

    private val interpreters = mutableMapOf<ModelType, Interpreter>()
    private val labels = mutableMapOf<ModelType, List<String>>()
    private var initialized = false

    // -----------------------------------------------------
    // 🔹 초기화 (앱 시작 시 1회 호출)
    // -----------------------------------------------------
    fun init(context: Context) {
        if (initialized) return
        try {
            loadModel(context, ModelType.CIGARETTE, "cigarette_best_float16.tflite", "cigarette_best_labels.txt")
            initialized = true
            Log.d("ModelHandler", "✅ All models initialized successfully")
        } catch (e: Exception) {
            Log.e("ModelHandler", "❌ Model initialization failed: ${e.message}")
        }
    }

    // -----------------------------------------------------
    // 🔹 모델 로드 함수
    // -----------------------------------------------------
    private fun loadModel(context: Context, type: ModelType, modelFile: String, labelFile: String) {
        try {
            val modelBytes = context.assets.open(modelFile).use { it.readBytes() }
            val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            byteBuffer.order(ByteOrder.nativeOrder())
            byteBuffer.put(modelBytes)
            byteBuffer.rewind()

            interpreters[type] = Interpreter(byteBuffer)
            labels[type] = context.assets.open(labelFile).bufferedReader().readLines()
            Log.d("ModelHandler", "$type model loaded ✅")
        } catch (e: Exception) {
            Log.e("ModelHandler", "Failed to load $type: ${e.message}")
        }
    }

    // -----------------------------------------------------
    // 🔹 이미지 전처리 (YOLOv8 스타일, 640x640 기준)
    // -----------------------------------------------------
    private fun preprocessImage(bitmap: Bitmap, inputSize: Int = 640): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        var pixelIndex = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = pixels[pixelIndex++]
                inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((pixel and 0xFF) / 255.0f))
            }
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    // -----------------------------------------------------
    // 🔹 YOLO 출력 디코딩
    // -----------------------------------------------------
    private fun parseOutputs(
        output: Array<Array<FloatArray>>,
        labels: List<String>,
        threshold: Float = 0.5f
    ): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val detections = output[0]

        for (det in detections) {
            val confidence = det[4]
            if (confidence > threshold) {
                val classId = det[5].toInt()
                val label = if (classId < labels.size) labels[classId] else "unknown"
                val box = RectF(det[0], det[1], det[2], det[3])
                results.add(DetectionResult(label, confidence, box))
            }
        }
        return results
    }

    // -----------------------------------------------------
    // 🔹 모델 추론
    // -----------------------------------------------------
    private fun runModel(bitmap: Bitmap, modelType: ModelType = ModelType.CIGARETTE): List<DetectionResult> {
        val interpreter = interpreters[modelType]
        val labelList = labels[modelType]

        if (interpreter == null || labelList == null) {
            Log.e("ModelHandler", "❌ Model not loaded: $modelType")
            return emptyList()
        }

        val inputBuffer = preprocessImage(bitmap)
        val outputBuffer = Array(1) { Array(300) { FloatArray(6) } }

        interpreter.run(inputBuffer, outputBuffer)
        return parseOutputs(outputBuffer, labelList)
    }

    // -----------------------------------------------------
    // 🔹 실제 분석 함수 (CameraX용)
    // -----------------------------------------------------
    fun analyzeImage(imageProxy: ImageProxy, callback: (String) -> Unit) {
        val bitmap = imageProxy.toBitmap()
        val results = runModel(bitmap)

        val abnormalDetected = results.any { it.label.lowercase() == "cigarette" }
        callback(if (abnormalDetected) "ABNORMAL" else "NORMAL")

        imageProxy.close()
    }

    // -----------------------------------------------------
    // 🔹 리소스 해제
    // -----------------------------------------------------
    fun close() {
        interpreters.values.forEach { it.close() }
        interpreters.clear()
        initialized = false
    }
}

// -----------------------------------------------------
// 🔹 ImageProxy → Bitmap 변환 확장 함수
// -----------------------------------------------------
fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
