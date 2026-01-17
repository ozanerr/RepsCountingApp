package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min

class LungesCounter {

    companion object {
        // ambang batas berdiri dilonggarkan sedikit
        private const val UP_KNEE_THRESHOLD = 150.0

        // target kedua lutut di bawah ini
        private const val DOWN_KNEE_THRESHOLD = 100.0
        // tunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3
        // cek jarak gerakan minimal
        private const val VERTICAL_MOVEMENT_THRESHOLD_RATIO = 0.15f
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0
    private var lastShoulderY: Float = 0f

    // posisi atas sebagai patokan
    private var calibratedUpShoulderY: Float = 0f

    // catatan data selama satu repetisi
    private var minLeftKneeAngleInRep: Double = 180.0
    private var minRightKneeAngleInRep: Double = 180.0
    private var wasBackStraightInRep: Boolean = true

    // reset hitungan jika pose keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ExerciseState.UP
        lastShoulderY = 0f
        calibratedUpShoulderY = 0f // reset patokan
        resetRepMetrics()
    }

    // reset catatan metrik repetisi
    private fun resetRepMetrics() {
        minLeftKneeAngleInRep = 180.0
        minRightKneeAngleInRep = 180.0
        wasBackStraightInRep = true
    }

    fun analyzePose(pose: Pose, isPhoneStable: Boolean): RepResult {

        if (!isPhoneStable) {
            framesInTargetState = 0
            stableUpFrames = 0
            // kirim status ponsel goyang
            return RepResult(repCount, "Goyang", "Ponsel Goyang", null)
        }

        // ambil data semua sendi yang dibutuhkan
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

        // reset jika ada sendi tidak terlihat
        if (landmarks.any { it == null }) {
            resetState()
            // kirim status tubuh tidak terlihat
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip!!, leftKnee!!, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip!!, rightKnee!!, rightAnkle!!)
        // hitung rata-rata sudut lutut
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgShoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2

        // syarat postur, bahu tidak lebih rendah dari pinggul
        val isBackStraight = avgShoulderY <= avgHipY

        // proses kalibrasi, minta berdiri tegak
        if (!isCalibrated) {
            // cek kelurusan berdasarkan rata-rata lutut, bukan satu per satu
            if (averageKneeAngle > UP_KNEE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                    lastShoulderY = avgShoulderY
                    calibratedUpShoulderY = avgShoulderY // simpan posisi atas sebagai patokan
                }
            } else {
                stableUpFrames = 0
            }
            // kirim status kalibrasi
            return RepResult(repCount, "Kalibrasi", "Berdiri tegak", null)
        }

        val isMovingDown = avgShoulderY > lastShoulderY
        val isMovingUp = avgShoulderY < lastShoulderY

        val torsoHeight = abs(avgHipY - avgShoulderY)
        val movementThreshold = torsoHeight * VERTICAL_MOVEMENT_THRESHOLD_RATIO

        // logika utama penghitungan repetisi
        if (currentState == ExerciseState.UP) {
            // syarat ganda: lutut menekuk dan badan turun
            if ((leftKneeAngle < 150.0 || rightKneeAngle < 150.0) && isMovingDown && avgShoulderY > calibratedUpShoulderY + movementThreshold) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.DOWN
                    framesInTargetState = 0
                    // gerakan turun dimulai, reset catatan metrik
                    resetRepMetrics()
                }
            } else {
                framesInTargetState = 0
            }
        } else { // currentState == ExerciseState.DOWN

            // catat metrik terburuk selama posisi turun
            minLeftKneeAngleInRep = min(minLeftKneeAngleInRep, leftKneeAngle)
            minRightKneeAngleInRep = min(minRightKneeAngleInRep, rightKneeAngle)
            if (!isBackStraight) {
                wasBackStraightInRep = false
            }

            // cek transisi ke atas, kedua lutut lurus
            if (averageKneeAngle > UP_KNEE_THRESHOLD && isMovingUp) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0

                    // repetisi selesai, mulai analisis catatan
                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. cek kedalaman
                    //    kedua lutut harus menekuk
                    if (minLeftKneeAngleInRep > DOWN_KNEE_THRESHOLD || minRightKneeAngleInRep > DOWN_KNEE_THRESHOLD) {
                        feedbackReport.add("Turun lebih rendah")
                        repIsValid = false
                    }

                    // 2. cek punggung
                    if (!wasBackStraightInRep) {
                        feedbackReport.add("Jaga dada tetap tegak")
                        repIsValid = false // punggung bungkuk repetisi tidak sah
                    }

                    // hitung repetisi hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    // kirim laporan hasil repetisi
                    return RepResult(repCount, "TURUN", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        lastShoulderY = avgShoulderY
        val status = if (currentState == ExerciseState.UP) "NAIK" else "TURUN"
        // jangan kirim feedback di antara repetisi
        return RepResult(repCount, status, null, null)
    }
}