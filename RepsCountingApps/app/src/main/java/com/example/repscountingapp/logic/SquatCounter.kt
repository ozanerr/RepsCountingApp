package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min

class SquatCounter {

    companion object {
        // Ambang batas sudut lutut untuk nentuin posisi
        private const val UP_KNEE_ANGLE_THRESHOLD = 160.0
        private const val DOWN_KNEE_ANGLE_THRESHOLD = 90.0
        // Nunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3

        // Badan harus turun minimal 15% dari tinggi torso agar dianggap squat valid
        private const val VERTICAL_MOVEMENT_THRESHOLD_RATIO = 0.15f
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    // Variabel baru untuk 'posisi atas' yang jadi patokan
    private var calibratedUpShoulderY: Float = 0f

    private var minKneeAngleInRep: Double = 180.0
    private var wasBackStraightInRep: Boolean = true

    // Reset hitungan kalau orangnya keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ExerciseState.UP
        calibratedUpShoulderY = 0f // Reset patokan
        resetRepMetrics()
    }

    // Fungsi baru untuk mereset 'catatan' repetisi
    private fun resetRepMetrics() {
        minKneeAngleInRep = 180.0
        wasBackStraightInRep = true
    }

    fun analyzePose(pose: Pose, isPhoneStable: Boolean): RepResult {

        if (!isPhoneStable) {
            framesInTargetState = 0
            stableUpFrames = 0
            return RepResult(repCount, "Goyang", "Ponsel Goyang", null)
        }

        // Ambil semua sendi yang dibutuhkan
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        val landmarks = listOf(
            leftHip, leftKnee, leftAnkle, rightHip, rightKnee, rightAnkle,
            leftShoulder, rightShoulder
        )

        // Kalau ada sendi yang nggak kelihatan, reset saja
        if (landmarks.any { it == null }) {
            resetState()
            // Kirim ini sebagai feedback real-time
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip!!, leftKnee!!, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip!!, rightKnee!!, rightAnkle!!)
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgShoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2

        // Syarat postur: bahu tidak lebih rendah dari pinggul
        val isBackStraight = avgShoulderY <= avgHipY

        // Kalau belum kalibrasi, minta pengguna berdiri tegak
        if (!isCalibrated) {
            if (averageKneeAngle > UP_KNEE_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                    // Siapin patokan awal buat 'posisi atas' (tinggi bahu saat berdiri)
                    calibratedUpShoulderY = avgShoulderY
                }
            } else {
                stableUpFrames = 0
            }
            return RepResult(repCount, "Kalibrasi", "Berdiri tegak", null)
        }

        // Hitung ambang batas gerakan berdasarkan ukuran tubuh user
        val torsoHeight = abs(avgHipY - avgShoulderY)
        val movementThreshold = torsoHeight * VERTICAL_MOVEMENT_THRESHOLD_RATIO

        // Logika utama buat hitung repetisi
        if (currentState == ExerciseState.UP) {
            val isBodyMovingDown = avgShoulderY > (calibratedUpShoulderY + movementThreshold)

            if (averageKneeAngle < 150.0 && isBodyMovingDown) { // Mulai gerakan turun
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.DOWN
                    framesInTargetState = 0
                    // Gerakan turun dimulai, bersihkan catatan rep sebelumnya
                    resetRepMetrics()
                }
            } else {
                framesInTargetState = 0
            }
        } else { // currentState == ExerciseState.DOWN

            // Selama di posisi bawah, catat terus data terburuknya
            minKneeAngleInRep = min(minKneeAngleInRep, averageKneeAngle)
            if (!isBackStraight) {
                wasBackStraightInRep = false
            }

            // Cek transisi dari bawah ke atas
            if (averageKneeAngle > UP_KNEE_ANGLE_THRESHOLD) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0

                    // Update patokan tinggi bahu saat berdiri lagi
                    calibratedUpShoulderY = avgShoulderY

                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. Cek kedalaman
                    if (minKneeAngleInRep > DOWN_KNEE_ANGLE_THRESHOLD) {
                        feedbackReport.add("Kaki kurang ditekuk")
                        repIsValid = false
                    }

                    // 2. Cek Punggung
                    if (!wasBackStraightInRep) {
                        feedbackReport.add("Jaga dada tetap tegak")
                        repIsValid = false // Punggung bungkuk = rep tidak sah
                    }

                    // Hitung rep hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    return RepResult(repCount, "TURUN", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        val status = if (currentState == ExerciseState.UP) "NAIK" else "TURUN"

        // Di antara repetisi, jangan kirim feedback apa-apa
        return RepResult(repCount, status, null, null)
    }
}