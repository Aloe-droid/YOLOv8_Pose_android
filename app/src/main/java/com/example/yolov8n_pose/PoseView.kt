package com.example.yolov8n_pose

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PoseView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    private var list: ArrayList<FloatArray>? = null
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private val linePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        strokeWidth = 5f
    }

    fun setList(list: ArrayList<FloatArray>) {
        this.list = list
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        //점, 선 그리기
        drawPointsAndLines(canvas)
        super.onDraw(canvas)
    }

    private fun drawPointsAndLines(canvas: Canvas?) {
        val scaleX = width / DataProcess.INPUT_SIZE.toFloat()
        val scaleY = scaleX * 9f / 16f
        val realY = width * 9f / 16f
        val diffY = realY - height

        val kPointsThreshold = 0.35f
        list?.forEach {
            val points = FloatArray(34)
            for ((a, i) in (points.indices step 2).withIndex()) {
                if (it[i + 7 + a] > kPointsThreshold) {
                    points[i] = it[i + 5 + a] * scaleX
                    points[i + 1] = it[i + 6 + a] * scaleY - (diffY / 2f)
                }
            }
            drawPoint(canvas, points)
            drawLines(canvas, points)
        }
    }

    private fun drawPoint(canvas: Canvas?, points: FloatArray) {
        for (i in points.indices step 2) {
            val xPos = points[i]
            val yPos = points[i + 1]
            if (xPos > 0 && yPos > 0) {
                canvas?.drawPoint(xPos, yPos, pointPaint)
            }
        }
    }

    private fun drawLines(canvas: Canvas?, points: FloatArray) {
        // 점과 점사이에 직선 그리기
        // keypoint 순서
        // 0번 == 코
        // 1번 == 오른쪽 눈
        // 2번 == 왼쪽 눈
        // 3번 == 오른쪽 귀
        // 4번 == 왼쪽 귀
        // 5번 == 오른쪽 어깨
        // 6번 == 왼쪽 어깨
        // 7번 == 오른쪽 팔꿈치
        // 8번 == 왼쪽 팔꿈치
        // 9번 == 오른쪽 손목
        // 10번 == 왼쪽 손목
        // 11번 == 오른쪽 골반
        // 12번 == 왼쪽 골반
        // 13번 == 오른쪽 무릎
        // 14번 == 왼쪽 무릎
        // 15번 == 오른쪽 발
        // 16번 == 왼쪽 발

        // 코, 오른쪽 눈 연결
        var startX = points[0]
        var startY = points[1]
        var stopX = points[2]
        var stopY = points[3]
        drawLine(startX, startY, stopX, stopY, canvas)
        // 코, 왼쪽 눈 연결
        startX = points[0]
        startY = points[1]
        stopX = points[4]
        stopY = points[5]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 눈 귀 연결
        startX = points[2]
        startY = points[3]
        stopX = points[8]
        stopY = points[9]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 눈 귀 연결
        startX = points[4]
        startY = points[5]
        stopX = points[8]
        stopY = points[9]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 귀 어깨 연결
        startX = points[6]
        startY = points[7]
        stopX = points[10]
        stopY = points[11]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 귀 어깨 연결
        startX = points[8]
        startY = points[9]
        stopX = points[12]
        stopY = points[13]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 어깨 팔꿈치 연결
        startX = points[10]
        startY = points[11]
        stopX = points[14]
        stopY = points[15]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 어깨 팔꿈치 연결
        startX = points[12]
        startY = points[13]
        stopX = points[16]
        stopY = points[17]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 어깨 골반 연결
        startX = points[10]
        startY = points[11]
        stopX = points[22]
        stopY = points[23]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 어깨 골반 연결
        startX = points[12]
        startY = points[13]
        stopX = points[24]
        stopY = points[25]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 팔꿈치 손목 연결
        startX = points[14]
        startY = points[15]
        stopX = points[18]
        stopY = points[19]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 팔꿈치 손목 연결
        startX = points[16]
        startY = points[17]
        stopX = points[20]
        stopY = points[21]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 골반 무릎 연결
        startX = points[22]
        startY = points[23]
        stopX = points[26]
        stopY = points[27]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 골반 무릎 연결
        startX = points[24]
        startY = points[25]
        stopX = points[28]
        stopY = points[29]
        drawLine(startX, startY, stopX, stopY, canvas)
        //오른쪽 무릎 발 연결
        startX = points[26]
        startY = points[27]
        stopX = points[30]
        stopY = points[31]
        drawLine(startX, startY, stopX, stopY, canvas)
        //왼쪽 무릎 발 연결
        startX = points[28]
        startY = points[29]
        stopX = points[32]
        stopY = points[33]
        drawLine(startX, startY, stopX, stopY, canvas)
        //어깨 좌우 연결
        startX = points[10]
        startY = points[11]
        stopX = points[12]
        stopY = points[13]
        drawLine(startX, startY, stopX, stopY, canvas)
        //골반 좌우 연결
        startX = points[22]
        startY = points[23]
        stopX = points[24]
        stopY = points[25]
        drawLine(startX, startY, stopX, stopY, canvas)
    }

    private fun drawLine(
        startX: Float,
        startY: Float,
        stopX: Float,
        stopY: Float,
        canvas: Canvas?
    ) {
        if (startX > 0 && startY > 0 && stopX > 0 && stopY > 0) {
            canvas?.drawLine(startX, startY, stopX, stopY, linePaint)
        }
    }

}