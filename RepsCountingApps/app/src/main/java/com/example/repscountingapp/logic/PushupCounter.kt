package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min

class PushupCounter {

    companion object {
        private const val UP_ANGLE_THRESHOLD = 150.0
        private const val DOWN_ANGLE_THRESHOLD = 95.0

        private const val BODY_STRAIGHT_THRESHOLD_LOW = 155.0
        private const val BODY_STRAIGHT_THRESHOLD_HIGH = 190.0
        private const val KNEE_STRAIGHT_THRESHOLD = 140.0

        private const val CONFIRMATION_FRAMES = 3
        private const val VERTICAL_MOVEMENT_THRESHOLD_RATIO = 0.15f

        private const val PLANK_RATIO_THRESHOLD = 0.5f
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    private var lastShoulderY: Float = 0f
    private var calibratedUpShoulderY: Float = 0f

    private var minElbowAngleInRep: Double = 180.0
    private var wasBodyStraightInRep: Boolean = true
    private var didKneeTouchInRep: Boolean = false

    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ExerciseState.UP
        lastShoulderY = 0f
        calibratedUpShoulderY = 0f // Reset patokan
        resetRepMetrics()
    }

    private fun resetRepMetrics() {
        minElbowAngleInRep = 180.0
        wasBodyStraightInRep = true
        didKneeTouchInRep = false
    }

    fun analyzePose(pose: Pose, isPhoneStable: Boolean): RepResult {

        if (!isPhoneStable) {
            framesInTargetState = 0
            stableUpFrames = 0
            return RepResult(repCount, "Goyang", "Ponsel Goyang", null)
        }

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val landmarks = listOf(
            leftShoulder, leftElbow, leftWrist, rightShoulder, rightElbow, rightWrist,
            leftHip, rightHip, leftKnee, rightKnee,
            leftAnkle, rightAnkle
        )

        if (landmarks.any { it == null }) {
            resetState()
            // Kirim ini sebagai feedback real-time
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        val leftElbowAngle = PoseAngleCalculator.calculateAngle(leftShoulder!!, leftElbow!!, leftWrist!!)
        val rightElbowAngle = PoseAngleCalculator.calculateAngle(rightShoulder!!, rightElbow!!, rightWrist!!)
        val averageElbowAngle = (leftElbowAngle + rightElbowAngle) / 2

        val leftBodyAngle = PoseAngleCalculator.calculateAngle(leftShoulder, leftHip!!, leftKnee!!)
        val rightBodyAngle = PoseAngleCalculator.calculateAngle(rightShoulder, rightHip!!, rightKnee!!)
        val averageBodyAngle = (leftBodyAngle + rightBodyAngle) / 2

        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip, leftKnee, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip, rightKnee, rightAnkle!!)
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2

        val avgShoulderX = (leftShoulder.position.x + rightShoulder.position.x) / 2
        val avgHipX = (leftHip.position.x + rightHip.position.x) / 2

        val verticalDist = abs(avgHipY - avgShoulderY)
        val horizontalDist = abs(avgHipX - avgShoulderX) // Pakai X, bukan landmark absolut

        var isPlank = false
        if (horizontalDist > 0) { // Hindari pembagian nol
            val ratio = verticalDist / horizontalDist
            if (ratio < PLANK_RATIO_THRESHOLD) {
                isPlank = true
            }
        }

        if (!isPlank) {
            resetState() // Bukan plank, reset semuanya
            return RepResult(repCount, "--", "Posisikan tubuh di lantai", null)
        }

        // Cek kalibrasi (hanya jika sudah di posisi plank)
        if (!isCalibrated) {
            if (averageElbowAngle > UP_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                    // Siapin patokan awal buat 'posisi atas'
                    lastShoulderY = avgShoulderY
                    calibratedUpShoulderY = avgShoulderY
                }
            } else {
                stableUpFrames = 0
            }
            return RepResult(repCount, "Kalibrasi", "Luruskan lengan", null)
        }

        val isMovingDown = avgShoulderY > lastShoulderY
        val isMovingUp = avgShoulderY < lastShoulderY

        val torsoHeight = abs(avgHipY - avgShoulderY)
        val movementThreshold = torsoHeight * VERTICAL_MOVEMENT_THRESHOLD_RATIO

        // Logika utama buat hitung repetisi
        if (currentState == ExerciseState.UP) {
            if (averageElbowAngle < 140.0 && isMovingDown && avgShoulderY > calibratedUpShoulderY + movementThreshold) {
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
            minElbowAngleInRep = min(minElbowAngleInRep, averageElbowAngle)

            if (averageBodyAngle < BODY_STRAIGHT_THRESHOLD_LOW) {
                wasBodyStraightInRep = false // Pinggang turun
            } else if (averageBodyAngle > BODY_STRAIGHT_THRESHOLD_HIGH) {
                wasBodyStraightInRep = false // Pinggang naik
            }

            if (averageKneeAngle < KNEE_STRAIGHT_THRESHOLD) {
                didKneeTouchInRep = true
            }

            // Cek transisi dari bawah ke atas
            if (averageElbowAngle > UP_ANGLE_THRESHOLD && isMovingUp) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0

                    // Repetisi selesai! Saatnya analisis catatannya.
                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. Cek kedalaman siku
                    if (minElbowAngleInRep > DOWN_ANGLE_THRESHOLD) {
                        feedbackReport.add("Siku kurang ditekuk")
                        repIsValid = false // Gerakan tidak cukup dalam
                    }

                    // 2. Cek lutut
                    if (didKneeTouchInRep) {
                        feedbackReport.add("Lutut tidak boleh menyentuh lantai")
                        repIsValid = false // Lutut menyentuh = rep tidak sah
                    }

                    // 3. Cek punggung
                    if (!wasBodyStraightInRep) {
                        feedbackReport.add("Jaga pinggang tetap lurus")
                        repIsValid = false
                    }

                    // 4. Cek siku saat naik
                    if (averageElbowAngle < UP_ANGLE_THRESHOLD) {
                        feedbackReport.add("Siku kurang lurus saat naik")
                    }

                    // Hitung rep hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    // Kirim laporan: rep, status, dan feedback pasca-repetisi
                    return RepResult(repCount, "TURUN", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        // Selalu update posisi terakhir
        lastShoulderY = avgShoulderY

        val status = if (currentState == ExerciseState.UP) "NAIK" else "TURUN"
        // Di antara repetisi, jangan kirim feedback apa-apa
        return RepResult(repCount, status, null, null)
    }
}