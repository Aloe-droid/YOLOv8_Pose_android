package com.example.yolov8n_pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class DataProcess(val context: Context) {

    companion object {
        const val BATCH_SIZE = 1
        const val INPUT_SIZE = 640
        const val PIXEL_SIZE = 3
        const val FILE_NAME = "yolov8n-pose.onnx"
    }

    fun imageToBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap = imageProxy.toBitmap()
        return Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
    }

    fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val imageSTD = 255f

        val cap = BATCH_SIZE * PIXEL_SIZE * INPUT_SIZE * INPUT_SIZE
        val order = ByteOrder.nativeOrder()
        val buffer = ByteBuffer.allocateDirect(cap * Float.SIZE_BYTES).order(order).asFloatBuffer()

        val area = INPUT_SIZE * INPUT_SIZE
        val bitmapData = IntArray(area)
        bitmap.getPixels(
            bitmapData,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        ) //배열에 RGB 담기

        //하나씩 받아서 버퍼에 할당
        for (i in 0 until INPUT_SIZE - 1) {
            for (j in 0 until INPUT_SIZE - 1) {
                val idx = INPUT_SIZE * i + j
                val pixelValue = bitmapData[idx]
                // 위에서 부터 차례대로 R 값 추출, G 값 추출, B값 추출 -> 255로 나누어서 0~1 사이로 정규화
                buffer.put(idx, ((pixelValue shr 16 and 0xff) / imageSTD))
                buffer.put(idx + area, ((pixelValue shr 8 and 0xff) / imageSTD))
                buffer.put(idx + area * 2, ((pixelValue and 0xff) / imageSTD))
                //원리 bitmap == ARGB 형태의 32bit, R값의 시작은 16bit (16 ~ 23bit 가 R영역), 따라서 16bit 를 쉬프트
                //그럼 A값이 사라진 RGB 값인 24bit 가 남는다. 이후 255와 AND 연산을 통해 맨 뒤 8bit 인 R값만 가져오고, 255로 나누어 정규화를 한다.
                //다시 8bit 를 쉬프트 하여 R값을 제거한 G,B 값만 남은 곳에 다시 AND 연산, 255 정규화, 다시 반복해서 RGB 값을 buffer 에 담는다.
            }
        }
        buffer.rewind()
        return buffer
    }

    fun loadPoseModel() {
        //onnx 파일 불러오기
        val assetManager = context.assets
        val outputFile = File(context.filesDir.toString() + "/" + FILE_NAME)

        assetManager.open(FILE_NAME).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
            }
        }
    }

    fun outputsToNPMSPredictions(outputs: Array<*>): ArrayList<FloatArray> {
        val confidenceThreshold = 0.4f
        val rows: Int
        val cols: Int
        val results = ArrayList<FloatArray>()

        (outputs[0] as Array<*>).also {
            rows = it.size
            cols = (it[0] as FloatArray).size
        }

        //배열 형태를 [56 8400] -> [8400 56] 으로 변환
        val output = Array(cols) { FloatArray(rows) }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                output[j][i] = (((outputs[0] as Array<*>)[i]) as FloatArray)[j]
            }
        }


        for (i in 0 until cols) {
            // 바운딩 박스의 특정 확률을 넘긴 경우에만 xywh -> xy xy 형태로 변환 후 nms 처리
            if (output[i][4] > confidenceThreshold) {
                val xPos = output[i][0]
                val yPos = output[i][1]
                val width = output[i][2]
                val height = output[i][3]

                val x1 = max(xPos - width / 2f, 0f)
                val x2 = min(xPos + width / 2f, INPUT_SIZE - 1f)
                val y1 = max(yPos - height / 2f, 0f)
                val y2 = min(yPos + height / 2f, INPUT_SIZE - 1f)

                output[i][0] = x1
                output[i][1] = y1
                output[i][2] = x2
                output[i][3] = y2

                results.add(output[i])
            }
        }
        return nms(results)
    }

    private fun nms(results: ArrayList<FloatArray>): ArrayList<FloatArray> {
        val list = ArrayList<FloatArray>()
        //results 안에 있는 conf 값 중에서 제일 높은 애를 기준으로 NMS 가 겹치는 애들을 제거
        val pq = PriorityQueue<FloatArray>(5) { o1, o2 ->
            o1[4].compareTo(o2[4])
        }

        pq.addAll(results)

        while (pq.isNotEmpty()) {
            // 큐 안에 속한 최대 확률값을 가진 FloatArray 저장
            val detections = pq.toTypedArray()
            val max = detections[0]
            list.add(max)
            pq.clear()

            // 교집합 비율 확인하고 50% 넘기면 제거
            for (k in 1 until detections.size) {
                val detection = detections[k]
                val rectF = RectF(detection[0], detection[1], detection[2], detection[3])
                val maxRectF = RectF(max[0], max[1], max[2], max[3])
                val iouThreshold = 0.5f
                if (boxIOU(maxRectF, rectF) < iouThreshold) {
                    pq.add(detection)
                }
            }
        }
        return list
    }


    // 겹치는 비율 (교집합/합집합)
    private fun boxIOU(a: RectF, b: RectF): Float {
        return boxIntersection(a, b) / boxUnion(a, b)
    }

    //교집합
    private fun boxIntersection(a: RectF, b: RectF): Float {
        // x1, x2 == 각 rect 객체의 중심 x or y값, w1, w2 == 각 rect 객체의 넓이 or 높이
        val w = overlap(
            (a.left + a.right) / 2f, a.right - a.left,
            (b.left + b.right) / 2f, b.right - b.left
        )

        val h = overlap(
            (a.top + a.bottom) / 2f, a.bottom - a.top,
            (b.top + b.bottom) / 2f, b.bottom - b.top
        )
        return if (w < 0 || h < 0) 0f else w * h
    }

    //합집합
    private fun boxUnion(a: RectF, b: RectF): Float {
        val i = boxIntersection(a, b)
        return (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i
    }

    //서로 겹치는 길이
    private fun overlap(x1: Float, w1: Float, x2: Float, w2: Float): Float {
        val l1 = x1 - w1 / 2
        val l2 = x2 - w2 / 2
        val left = max(l1, l2)
        val r1 = x1 + w1 / 2
        val r2 = x2 + w2 / 2
        val right = min(r1, r2)
        return right - left
    }
}