package com.example.android_front.ai

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.util.LinkedList
import java.util.Queue
import kotlin.math.max
import kotlin.math.min

// -----------------------------------------------------
// 🔹 감지 결과 구조체
// -----------------------------------------------------
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val box: RectF
)

// -----------------------------------------------------
// 🔹 모델 종류
// -----------------------------------------------------
enum class ModelType {
    CIGARETTE, PHONE, SEATBELT,
    FACE, DROWSINESS
}

// -----------------------------------------------------
// 🔹 ModelHandler (콤팩트)
// -----------------------------------------------------
object ModelHandler {

    private val interpreters = mutableMapOf<ModelType, Interpreter>()
    private val labels       = mutableMapOf<ModelType, List<String>>()
    private var initialized  = false

    // --- 공통/YOLO 입력 크기 ---
    private const val YOLO_IN = 640
    private const val CLF_IN  = 224

    // --- Face YOLO / 공통 NMS 파라미터 ---
    private const val YOLO_CONF = 0.25f
    private const val YOLO_IOU  = 0.45f
    private const val YOLO_TOPK = 50

    // --- Drowsiness 정책 ---
    private const val DROWSY_TH = 0.9f
    private const val DROWSY_MIN_FRAMES = 2
    private val drowsyWindow: Queue<Int> = LinkedList()

    // --- Seatbelt(새 모델) 정책 ---
    private const val SB_CONF = 0.35f     // obj 임계값
    private const val SB_IOU  = 0.45f
    private const val SB_TOPK = 100
    private const val SEATBELT_MIN_MISS_FRAMES = 7 // 연속 미탐지 10프레임 → 경고
    private var seatbeltMissStreak = 0

    // -----------------------------------------------------
    // 🔹 초기화
    // -----------------------------------------------------
    fun init(context: Context) {
        if (initialized) return
        try {
            // 기존 3종 그대로 (담배/폰은 기존 파서 사용)
            //loadModel(context, ModelType.CIGARETTE, "cigarette_best_float16.tflite", "cigarette_best_labels.txt")
            //loadModel(context, ModelType.PHONE,     "phone_float16.tflite",         "phone_labels.txt")
            // ⚠️ seatbelt는 새 YOLO(1×5×8400) 모델 -> label 파일 없음
            loadModel(context, ModelType.SEATBELT,  "seatbelt_best_float16.tflite", null)

            // 얼굴 + 졸음
            //loadModel(context, ModelType.FACE,       "yolov8n-face_float32.tflite", null)
            //loadModel(context, ModelType.DROWSINESS, "mobilenetv2_drowsiness_optimized.tflite", null)

            initialized = true
            Log.d("ModelHandler", "✅ All models initialized")
        } catch (e: Exception) {
            Log.e("ModelHandler", "❌ init failed: ${e.message}")
        }
    }

    private fun loadModel(context: Context, type: ModelType, modelFile: String, labelFile: String?) {
        val mapped = mapAsset(context, modelFile)
        interpreters[type] = Interpreter(mapped, Interpreter.Options().apply { /* setNumThreads(4) */ })
        if (labelFile != null) labels[type] = context.assets.open(labelFile).bufferedReader().readLines()
        Log.d("ModelHandler", "$type loaded")
    }

    private fun mapAsset(context: Context, name: String): MappedByteBuffer {
        val afd = context.assets.openFd(name)
        val fis = java.io.FileInputStream(afd.fileDescriptor)
        return fis.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
    }

    // -----------------------------------------------------
    // 🔹 공통: NHWC RGB /255 전처리
    // -----------------------------------------------------
    private fun toNHWC_RGB(bitmap: Bitmap, size: Int): ByteBuffer {
        val bmp = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val buf = ByteBuffer.allocateDirect(4 * size * size * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        var i = 0
        for (y in 0 until size) for (x in 0 until size) {
            val p = px[i++]
            buf.putFloat(((p ushr 16) and 0xFF) / 255f)
            buf.putFloat(((p ushr 8)  and 0xFF) / 255f)
            buf.putFloat(( p         and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    // -----------------------------------------------------
    // 🔹 (구) 3종 감지: 기존 placeholder 유지 (담배/폰에서만 사용)
    // -----------------------------------------------------
    private fun runModel(bitmap: Bitmap, modelType: ModelType): List<DetectionResult> {
        val t = interpreters[modelType] ?: return emptyList()
        val labs = labels[modelType] ?: return emptyList()
        val input = toNHWC_RGB(bitmap, YOLO_IN)
        val out = Array(1) { Array(300) { FloatArray(6) } } // 기존 포맷 가정
        t.run(input, out)
        return parseOutputs(out, labs)
    }

    private fun parseOutputs(
        output: Array<Array<FloatArray>>,
        labels: List<String>,
        threshold: Float = 0.5f
    ): List<DetectionResult> {
        val res = mutableListOf<DetectionResult>()
        val dets = output[0]
        for (det in dets) {
            val conf = det[4]
            if (conf > threshold) {
                val id = det[5].toInt()
                val lbl = if (id in labels.indices) labels[id] else "unknown"
                res.add(DetectionResult(lbl, conf, RectF(det[0], det[1], det[2], det[3])))
            }
        }
        return res
    }

    // -----------------------------------------------------
    // 🔹 YOLOv8-face: [1,20,8400] 간단 디코딩(+letterbox 역보정 + NMS)
    //   - 단일 클래스 가정 → obj만 사용
    // -----------------------------------------------------
    private fun detectFaces(frame: Bitmap): List<RectF> {
        val t = interpreters[ModelType.FACE] ?: return emptyList()

        val (inputBmp, scale, padX, padY) = letterbox(frame, YOLO_IN, YOLO_IN)
        val input = toNHWC_RGB(inputBmp, YOLO_IN)

        val outTensor: Tensor = t.getOutputTensor(0)
        val shp = outTensor.shape() // [1,20,8400] or [1,8400,20]
        val cFirst = (shp[1] == 20)
        val C = if (cFirst) shp[1] else shp[2]
        val N = if (cFirst) shp[2] else shp[1]
        if (C != 20 || N != 8400) {
            Log.e("YOLO_FACE", "Unexpected shape: ${shp.joinToString()}")
            return emptyList()
        }

        val boxes = ArrayList<RectF>()
        val scores = ArrayList<Float>()

        if (cFirst) {
            val out = Array(1) { Array(20) { FloatArray(N) } }
            t.run(input, out)
            for (i in 0 until N) {
                val x = out[0][0][i]; val y = out[0][1][i]
                val w = out[0][2][i]; val h = out[0][3][i]
                val obj = out[0][4][i]
                addDet(frame, x, y, w, h, obj, scale, padX, padY, boxes, scores)
            }
        } else {
            val out = Array(1) { Array(N) { FloatArray(20) } }
            t.run(input, out)
            for (i in 0 until N) {
                val v = out[0][i]
                val x = v[0]; val y = v[1]; val w = v[2]; val h = v[3]
                val obj = v[4]
                addDet(frame, x, y, w, h, obj, scale, padX, padY, boxes, scores)
            }
        }

        if (boxes.isEmpty()) return emptyList()
        val keep = nms(boxes, scores.toFloatArray(), YOLO_IOU, YOLO_TOPK)
        return keep.map { boxes[it] }
    }

    // 공용: YOLO(cx,cy,w,h in 0~1) → 원본 좌표 변환 & 임계값 필터 (얼굴용)
    private fun addDet(
        src: Bitmap,
        x: Float, y: Float, w: Float, h: Float, obj: Float,
        scale: Float, padX: Float, padY: Float,
        boxes: MutableList<RectF>, scores: MutableList<Float>
    ) {
        if (obj < YOLO_CONF) return
        val x640 = x * YOLO_IN; val y640 = y * YOLO_IN
        val w640 = w * YOLO_IN; val h640 = h * YOLO_IN
        val cx = (x640 - padX) / scale; val cy = (y640 - padY) / scale
        val ww =  w640 / scale;        val hh =  h640 / scale
        val x1 = max(0f, cx - ww / 2f); val y1 = max(0f, cy - hh / 2f)
        val x2 = min(src.width.toFloat(),  cx + ww / 2f)
        val y2 = min(src.height.toFloat(), cy + hh / 2f)
        if (x2 > x1 && y2 > y1) {
            boxes.add(RectF(x1, y1, x2, y2)); scores.add(obj)
        }
    }

    // -----------------------------------------------------
    // 🔹 Seatbelt(새 모델) 디코더: [1,5,8400] 또는 [1,8400,5]
    //     - 단일 클래스 obj → 박스가 하나라도 나오면 "벨트 감지됨"
    //     - 박스 “미탐지”가 연속 10프레임이면 noseatbelt 이벤트
    // -----------------------------------------------------
    private fun detectSeatbeltBoxes(src: Bitmap): List<RectF> {
        val t = interpreters[ModelType.SEATBELT] ?: return emptyList()

        val (inputBmp, scale, padX, padY) = letterbox(src, YOLO_IN, YOLO_IN)
        val input = toNHWC_RGB(inputBmp, YOLO_IN)

        val shp = t.getOutputTensor(0).shape() // [1,5,8400] or [1,8400,5]
        val cFirst = (shp[1] == 5)
        val C = if (cFirst) shp[1] else shp[2]
        val N = if (cFirst) shp[2] else shp[1]
        if (!(C == 5 && N == 8400)) {
            Log.e("SEATBELT", "Unexpected out shape: ${shp.joinToString()}")
            return emptyList()
        }

        val boxes = ArrayList<RectF>()
        val scores = ArrayList<Float>()

        if (cFirst) {
            val out = Array(1) { Array(5) { FloatArray(N) } }   // [1,5,8400]
            t.run(input, out)
            for (i in 0 until N) {
                val x = out[0][0][i]; val y = out[0][1][i]
                val w = out[0][2][i]; val h = out[0][3][i]
                val obj = out[0][4][i]
                addDetWithConf(src, x, y, w, h, obj, scale, padX, padY, boxes, scores, YOLO_IN, SB_CONF)
            }
        } else {
            val out = Array(1) { Array(N) { FloatArray(5) } }   // [1,8400,5]
            t.run(input, out)
            for (i in 0 until N) {
                val v = out[0][i]
                val x = v[0]; val y = v[1]; val w = v[2]; val h = v[3]
                val obj = v[4]
                addDetWithConf(src, x, y, w, h, obj, scale, padX, padY, boxes, scores, YOLO_IN, SB_CONF)
            }
        }

        if (boxes.isEmpty()) return emptyList()
        val keep = nms(boxes, scores.toFloatArray(), SB_IOU, SB_TOPK)

        // ✅ 한 줄 로그: 임계값 통과한 것들 중 최대 obj와 keep 수
       // Log.d("SEATBELT_CONF", "max=${"%.3f".format(scores.maxOrNull() ?: 0f)} kept=${keep.size}")

        return keep.map { boxes[it] }
    }

    // Seatbelt용: 임계값 파라미터 있는 좌표 변환
    private fun addDetWithConf(
        src: Bitmap,
        x: Float, y: Float, w: Float, h: Float, obj: Float,
        scale: Float, padX: Float, padY: Float,
        boxes: MutableList<RectF>, scores: MutableList<Float>,
        inSize: Int, confTh: Float
    ) {
        if (obj < confTh) return
        val cxL = x * inSize; val cyL = y * inSize
        val wL  = w * inSize; val hL  = h * inSize
        val cx = (cxL - padX) / scale; val cy = (cyL - padY) / scale
        val ww =  wL / scale;         val hh =  hL / scale
        val x1 = max(0f, cx - ww / 2f); val y1 = max(0f, cy - hh / 2f)
        val x2 = min(src.width.toFloat(),  cx + ww / 2f)
        val y2 = min(src.height.toFloat(), cy + hh / 2f)
        if (x2 > x1 && y2 > y1) { boxes.add(RectF(x1, y1, x2, y2)); scores.add(obj) }
    }

    // -----------------------------------------------------
    // 🔹 졸음 분류(간단): DB+DD ≥ 0.6, 연속 10프레임
    // -----------------------------------------------------
    private fun classifyDrowsy(faceBmp: Bitmap): Float {
        val t = interpreters[ModelType.DROWSINESS] ?: return 0f
        val input = toNHWC_RGB(faceBmp, CLF_IN)
        val out = Array(1) { FloatArray(4) }
        t.run(input, out)
        var dB = out[0][0]; var dD = out[0][1]; var nB = out[0][2]; var nD = out[0][3]
        val s = dB + dD + nB + nD
        if (s < 0.99f || s > 1.01f) { // logits → softmax
            val m = max(max(dB, dD), max(nB, nD))
            val eDB = kotlin.math.exp((dB - m).toDouble()).toFloat()
            val eDD = kotlin.math.exp((dD - m).toDouble()).toFloat()
            val eNB = kotlin.math.exp((nB - m).toDouble()).toFloat()
            val eND = kotlin.math.exp((nD - m).toDouble()).toFloat()
            val sum = eDB + eDD + eNB + eND
            dB = eDB / sum; dD = eDD / sum
        }
        val score = (dB + dD).coerceIn(0f, 1f)

        // 🔵 여기서 확률/점수 로그
//        Log.d(
//            "DROWSY_PROB",
//            "DB=${"%.3f".format(dB)} DD=${"%.3f".format(dD)} NB=${"%.3f".format(nB)} ND=${"%.3f".format(nD)} | score(DB+DD)=${"%.3f".format(score)}"
//        )

        return score
    }

    // -----------------------------------------------------
    // 🔹 실제 분석 (CameraX)
    //   - 회전 보정 포함 (중요!)
    // -----------------------------------------------------
    fun analyzeImage(imageProxy: ImageProxy, callback: (List<String>) -> Unit) {
        val raw = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val frame = raw.rotate(rotation)

        // 담배/폰은 기존 파서 사용
        val cigaretteResults = runModel(frame, ModelType.CIGARETTE)
        val phoneResults     = runModel(frame, ModelType.PHONE)

        // 새 안전벨트: 1×5×8400 → obj/NMS 후 박스 존재 여부로 판단(=벨트 존재)
        val seatbeltBoxes = detectSeatbeltBoxes(frame)
        val seatbeltDetected = seatbeltBoxes.isNotEmpty()
        // 연속 미탐지 → 경고
        if (seatbeltDetected) seatbeltMissStreak = 0 else seatbeltMissStreak += 1
        val seatbeltAlert = seatbeltMissStreak >= SEATBELT_MIN_MISS_FRAMES

        // 얼굴 + 졸음
        val faces = detectFaces(frame)
        val drowsyState = if (faces.isEmpty()) {
            drowsyWindow.clear(); "NO_FACE"
        } else {
            val face = faces.maxByOrNull { it.width() * it.height() }!!
            val crop = crop(frame, face)
            val score = classifyDrowsy(crop)
            val hit = if (score >= DROWSY_TH) 1 else 0
            drowsyWindow.add(hit)
            if (drowsyWindow.size > DROWSY_MIN_FRAMES) drowsyWindow.poll()
            if (drowsyWindow.sumFast() >= DROWSY_MIN_FRAMES) "DROWSINESS" else "AWAKE"
        }

        val events = mutableListOf<String>()
        if (cigaretteResults.any { it.label.equals("cigarette", true) }) events.add("cigarette")
        if (phoneResults.any     { it.label.equals("phone", true) })     events.add("phone")
        if (seatbeltAlert)                                            events.add("noseatbelt")
        if (drowsyState == "DROWSINESS")                               events.add("DROWSINESS")

        callback(events)
        imageProxy.close()
    }

    // -----------------------------------------------------
    // 🔹 유틸
    // -----------------------------------------------------
    private fun crop(src: Bitmap, r: RectF): Bitmap {
        val l = r.left.toInt().coerceAtLeast(0)
        val t = r.top.toInt().coerceAtLeast(0)
        val w = r.width().toInt().coerceAtMost(src.width - l)
        val h = r.height().toInt().coerceAtMost(src.height - t)
        return Bitmap.createBitmap(src, l, t, w, h)
    }

    private data class LbRes(val bmp: Bitmap, val scale: Float, val padX: Float, val padY: Float)
    private fun letterbox(src: Bitmap, tw: Int, th: Int): LbRes {
        val s = min(tw.toFloat() / src.width, th.toFloat() / src.height)
        val nw = (src.width * s).toInt()
        val nh = (src.height * s).toInt()
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val px = (tw - nw) / 2f
        val py = (th - nh) / 2f
        c.drawColor(Color.BLACK)
        c.drawBitmap(resized, px, py, null)
        return LbRes(out, s, px, py)
    }

    private fun nms(boxes: List<RectF>, scores: FloatArray, iou: Float, topk: Int): List<Int> {
        val order = scores.indices.sortedByDescending { scores[it] }
        val keep = ArrayList<Int>()
        val off = BooleanArray(scores.size)
        fun iou(a: RectF, b: RectF): Float {
            val x1 = max(a.left, b.left); val y1 = max(a.top, b.top)
            val x2 = min(a.right, b.right); val y2 = min(a.bottom, b.bottom)
            val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
            val uni = a.width() * a.height() + b.width() * b.height() - inter
            return if (uni <= 0f) 0f else inter / uni
        }
        for (i in order) {
            if (off[i]) continue
            keep.add(i)
            if (keep.size >= topk) break
            val a = boxes[i]
            for (j in order) {
                if (off[j] || j == i) continue
                if (iou(a, boxes[j]) >= iou) off[j] = true
            }
        }
        return keep
    }

    private fun Queue<Int>.sumFast(): Int {
        var s = 0; for (v in this) s += v; return s
    }

    private fun Bitmap.rotate(deg: Int): Bitmap {
        if (deg % 360 == 0) return this
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }
}

// -----------------------------------------------------
// 🔹 ImageProxy → Bitmap
// -----------------------------------------------------
fun ImageProxy.toBitmap(): Bitmap {
    return try {
        val y = planes[0].buffer
        val u = planes[1].buffer
        val v = planes[2].buffer
        val ySize = y.remaining()
        val uSize = u.remaining()
        val vSize = v.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        y.get(nv21, 0, ySize)
        v.get(nv21, ySize, vSize)
        u.get(nv21, ySize + vSize, uSize)
        val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuv.compressToJpeg(android.graphics.Rect(0, 0, width, height), 95, out)
        val bytes = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("ImageProxyExt", "toBitmap failed: ${e.message}")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    }
}
