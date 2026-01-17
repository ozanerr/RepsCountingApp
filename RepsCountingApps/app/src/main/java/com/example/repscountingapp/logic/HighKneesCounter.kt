package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.min

private enum class HighKneeState {
    WAITING_FOR_LEFT, // nunggu kaki kiri naik
    WAITING_FOR_RIGHT // nunggu kaki kanan naik
}

class HighKneesCounter {

    companion object {
        // ambang batas lutut dianggap lurus (kalibrasi)
        private const val STANDING_KNEE_THRESHOLD = 145.0

        // ambang batas rasio bahu-y vs pinggul-y
        private const val BACK_STRAIGHT_THRESHOLD = 0.9f

        // target angkatan lutut (sudut harus lebih kecil dari ini)
        private const val KNEE_LIFT_THRESHOLD = 130.0

        private const val KNEE_MOVEMENT_THRESHOLD = 140.0

        // nunggu 2 frame biar stabil (lebih cepat)
        private const val CONFIRMATION_FRAMES = 2
    }

    private var repCount = 0
    // state internal, mulai dengan nunggu kaki kiri
    private var currentState = HighKneeState.WAITING_FOR_LEFT
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    private var wasBackStraightInCycle: Boolean = true
    // kita lacak sudut terdalam
    private var minLeftKneeAngleInRep: Double = 180.0
    private var minRightKneeAngleInRep: Double = 180.0

    // reset hitungan kalau orangnya keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = HighKneeState.WAITING_FOR_LEFT
        resetRepMetrics()
    }

    private fun resetRepMetrics() {
        wasBackStraightInCycle = true
        minLeftKneeAngleInRep = 180.0
        minRightKneeAngleInRep = 180.0
    }

    fun analyzePose(pose: Pose, isPhoneStable: Boolean): RepResult {

        if (!isPhoneStable) {
            framesInTargetState = 0
            stableUpFrames = 0
            // kirim status ponsel goyang
            return RepResult(repCount, "Goyang", "Ponsel Goyang", null)
        }

        // ambil semua sendi yang dibutuhkan
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
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgShoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2

        // syarat postur: bahu tidak lebih rendah dari (pinggul * 0.9)
        val isBackStraight = avgShoulderY <= (avgHipY * BACK_STRAIGHT_THRESHOLD)

        val isLeftKneeMoving = leftKneeAngle < KNEE_MOVEMENT_THRESHOLD
        val isRightKneeMoving = rightKneeAngle < KNEE_MOVEMENT_THRESHOLD

        // cek kaki lurus (untuk kembali ke state standby)
        val areLegsStanding = averageKneeAngle > STANDING_KNEE_THRESHOLD

        // proses kalibrasi, minta pengguna berdiri tegak
        if (!isCalibrated) {
            if (areLegsStanding) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                }
            } else {
                stableUpFrames = 0
            }
            // kirim status kalibrasi
            return RepResult(repCount, "Kalibrasi", "Berdiri tegak", null)
        }

        var feedbackReport: Set<String>? = null

        // catat postur terburuk
        if (!isBackStraight) {
            wasBackStraightInCycle = false
        }

        if (currentState == HighKneeState.WAITING_FOR_LEFT) {
            // catat sudut lutut kiri (puncak angkatan)
            minLeftKneeAngleInRep = min(minLeftKneeAngleInRep, leftKneeAngle)

            if (isLeftKneeMoving) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    // kaki kiri terdeteksi naik, sekarang tunggu kaki kanan
                    currentState = HighKneeState.WAITING_FOR_RIGHT
                    framesInTargetState = 0
                }
            } else {
                framesInTargetState = 0
            }
        } else { // currentState == HighKneeState.WAITING_FOR_RIGHT
            // catat sudut lutut kanan (puncak angkatan)
            minRightKneeAngleInRep = min(minRightKneeAngleInRep, rightKneeAngle)

            if (isRightKneeMoving) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    // kaki kanan terdeteksi naik, siklus selesai
                    currentState = HighKneeState.WAITING_FOR_LEFT
                    framesInTargetState = 0

                    var repIsValid = true
                    val feedbackMessages = mutableSetOf<String>()

                    if (!wasBackStraightInCycle) {
                        feedbackMessages.add("Jaga dada tetap tegak")
                        repIsValid = false // punggung bungkuk = rep tidak sah
                    }

                    if (minLeftKneeAngleInRep > KNEE_LIFT_THRESHOLD || minRightKneeAngleInRep > KNEE_LIFT_THRESHOLD) {
                        feedbackMessages.add("Angkat lutut lebih tinggi")
                        repIsValid = false
                    }

                    if (repIsValid) {
                        repCount++
                    }

                    feedbackReport = feedbackMessages.ifEmpty { null }
                    // bersihkan catatan untuk siklus berikutnya
                    resetRepMetrics()
                }
            } else {
                framesInTargetState = 0
            }
        }

        // tentukan status instruksi
        val status = if (currentState == HighKneeState.WAITING_FOR_LEFT) "ANGKAT KIRI" else "ANGKAT KANAN"

        // kirim laporan
        return RepResult(repCount, status, null, feedbackReport)
    }
}