package com.example.repscountingapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

class PoseOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var currentPose: Pose? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val pointPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 12f
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    fun drawPose(pose: Pose, sourceWidth: Int, sourceHeight: Int) {
        currentPose = pose
        imageWidth = sourceWidth
        imageHeight = sourceHeight
        // Memaksa view untuk digambar ulang
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        currentPose?.let { pose ->
            if (imageWidth == 0 || imageHeight == 0) return

            // Gambar semua titik landmark
            for (landmark in pose.allPoseLandmarks) {
                drawPoint(canvas, landmark)
            }

            // Gambar garis-garis yang menghubungkan landmark
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
            val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
            val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
            val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
            val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
            val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
            val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

            // Garis tubuh
            drawLine(canvas, leftShoulder, rightShoulder)
            drawLine(canvas, leftHip, rightHip)
            drawLine(canvas, leftShoulder, leftHip)
            drawLine(canvas, rightShoulder, rightHip)

            // Garis tangan kiri
            drawLine(canvas, leftShoulder, leftElbow)
            drawLine(canvas, leftElbow, leftWrist)

            // Garis tangan kanan
            drawLine(canvas, rightShoulder, rightElbow)
            drawLine(canvas, rightElbow, rightWrist)

            // Garis kaki kiri
            drawLine(canvas, leftHip, leftKnee)
            drawLine(canvas, leftKnee, leftAnkle)

            // Garis kaki kanan
            drawLine(canvas, rightHip, rightKnee)
            drawLine(canvas, rightKnee, rightAnkle)
        }
    }

    private fun drawPoint(canvas: Canvas, landmark: PoseLandmark) {
        val (scaledX, scaledY) = scale(landmark.position.x, landmark.position.y)
        canvas.drawCircle(scaledX, scaledY, 8f, pointPaint)
    }

    private fun drawLine(canvas: Canvas, start: PoseLandmark?, end: PoseLandmark?) {
        if (start != null && end != null) {
            val (startX, startY) = scale(start.position.x, start.position.y)
            val (endX, endY) = scale(end.position.x, end.position.y)
            canvas.drawLine(startX, startY, endX, endY, linePaint)
        }
    }

    // Fungsi untuk menskalakan koordinat dari gambar sumber ke ukuran view
    private fun scale(x: Float, y: Float): Pair<Float, Float> {
        val scaleFactorX = width.toFloat() / imageWidth.toFloat()
        val scaleFactorY = height.toFloat() / imageHeight.toFloat()

        // pakai max untuk keperluan scalefactor
        val scaleFactor = max(scaleFactorX, scaleFactorY)

        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor
        val offsetX = (width - scaledWidth) / 2
        val offsetY = (height - scaledHeight) / 2

        // Untuk membalik koordinat untuk keperluan mirroring kamera
        val mirroredX = imageWidth - x

        val scaledX = mirroredX * scaleFactor + offsetX
        val scaledY = y * scaleFactor + offsetY

        return Pair(scaledX, scaledY)
    }
}