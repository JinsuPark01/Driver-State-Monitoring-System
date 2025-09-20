package com.example.android_front.ai

import android.content.Context
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class ModelHandler(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = context.assets.open("yolo_driver.tflite").readBytes()
        interpreter = Interpreter(model)
    }

    fun analyzeImage(image: ImageProxy, callback: (String) -> Unit) {
        val inputBuffer: ByteBuffer = preprocess(image) // 전처리
        val output = Array(1) { FloatArray(2) } // 예: [졸음, 이상행동] 확률

        interpreter.run(inputBuffer, output)

        val result = if (output[0][0] > 0.8) {
            "DROWSINESS"
        } else if (output[0][1] > 0.8) {
            "ABNORMAL"
        } else {
            "NORMAL"
        }

        callback(result)
        image.close()
    }

    private fun preprocess(image: ImageProxy): ByteBuffer {
        // TODO: YOLO 모델에 맞게 ImageProxy → ByteBuffer 변환
        // 보통 resize + normalize 필요
        return ByteBuffer.allocateDirect(1)
    }
}
