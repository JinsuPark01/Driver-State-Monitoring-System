package com.example.android_front.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelHandler(private val context: Context) {

    private val modelFileName = "yolov8n_float16.tflite"
    private val interpreter: Interpreter by lazy { loadModel() }

    /**
     * 이미지 분석
     * @param image ImageProxy (CameraX)
     * @param callback "DROWSINESS" / "ABNORMAL" / "NORMAL" 반환
     */
    fun analyzeImage(image: ImageProxy, callback: (String) -> Unit) {
        val bitmap = imageProxyToBitmap(image)

        // TODO: 실제 모델 추론
        val result = runModel(bitmap)

        callback(result)
        image.close()
    }

    /**
     * ImageProxy → Bitmap 변환
     * 지금은 테스트용 빈 Bitmap 반환
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * 실제 모델 추론
     * 현재는 빈 구현
     */
    private fun runModel(bitmap: Bitmap): String {
        // TODO: bitmap → 입력 tensor 변환
        // interpreter.run(inputTensor, outputTensor)
        // outputTensor → "DROWSINESS"/"ABNORMAL"/"NORMAL" 매핑
        return "NORMAL"
    }

    /**
     * TFLite 모델 로드
     */
    private fun loadModel(): Interpreter {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val mappedByteBuffer: MappedByteBuffer =
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        return Interpreter(mappedByteBuffer)
    }

    /**
     * 모델 리소스 해제
     */
    fun close() {
        interpreter.close()
    }
}
