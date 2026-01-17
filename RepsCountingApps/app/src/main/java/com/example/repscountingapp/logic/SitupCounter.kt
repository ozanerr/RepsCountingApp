package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.min

class SitupCounter {

    companion object {
        // ambang batas berbaring (kaki ditekuk)
        private const val UP_BODY_ANGLE_THRESHOLD = 130.0

        private const val DOWN_BODY_ANGLE_THRESHOLD = 110.0

        // ambang batas kaki dianggap lurus (gagal)
        private const val KNEE_STRAIGHT_THRESHOLD = 140.0

        // nunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP // mulai dari berbaring
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    // sudut badan terkecil (puncak sit-up)
    private var minBodyAngleInRep: Double = 180.0
    // penanda apakah kaki sempat lurus
    private var didLegsStraightenInRep: Boolean = false

    // reset hitungan kalau orangnya keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ExerciseState.UP
        resetRepMetrics()
    }

    private fun resetRepMetrics() {
        minBodyAngleInRep = 180.0
        didLegsStraightenInRep = false
    }

    fun analyzePose(pose: Pose, isPhoneStable: Boolean): RepResult {

        if (!isPhoneStable) {
            framesInTargetState = 0
            stableUpFrames = 0
            // kirim status ponsel goyang
            return RepResult(repCount, "Goyang", "Ponsel Goyang", null)
        }

        // ambil semua sendi yang dibutuhkan
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val landmarks = listOf(
            leftShoulder, rightShoulder, leftHip, rightHip,
            leftKnee, rightKnee, leftAnkle, rightAnkle
        )

        // reset jika ada sendi tidak terlihat
        if (landmarks.any { it == null }) {
            resetState()
            // kirim status tubuh tidak terlihat
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        // sudut utama: bahu-pinggul-lutut
        val leftBodyAngle = PoseAngleCalculator.calculateAngle(leftShoulder!!, leftHip!!, leftKnee!!)
        val rightBodyAngle = PoseAngleCalculator.calculateAngle(rightShoulder!!, rightHip!!, rightKnee!!)
        val averageBodyAngle = (leftBodyAngle + rightBodyAngle) / 2

        // sudut sekunder: pinggul-lutut-pergelangan kaki (buat cek kaki lurus)
        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip, leftKnee, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip, rightKnee, rightAnkle!!)
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        // proses kalibrasi, minta pengguna berbaring
        if (!isCalibrated) {
            // cek apakah sudah di posisi berbaring (dengan ambang batas baru)
            if (averageBodyAngle > UP_BODY_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                }
            } else {
                stableUpFrames = 0
            }
            // kirim status kalibrasi (pesan diubah)
            return RepResult(repCount, "Kalibrasi", "Berbaring, lutut ditekuk", null)
        }

        // logika utama penghitungan repetisi
        if (currentState == ExerciseState.UP) {
            // cek transisi dari atas (berbaring) ke bawah (duduk)
            if (averageBodyAngle < UP_BODY_ANGLE_THRESHOLD - 5) { // mulai gerakan naik (125)
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.DOWN
                    framesInTargetState = 0
                    // gerakan naik dimulai, reset catatan metrik
                    resetRepMetrics()
                }
            } else {
                framesInTargetState = 0
            }
        } else { // currentState == ExerciseState.DOWN

            // catat metrik terburuk selama posisi naik
            minBodyAngleInRep = min(minBodyAngleInRep, averageBodyAngle)

            // cek apakah kaki jadi lurus
            if (averageKneeAngle > KNEE_STRAIGHT_THRESHOLD) {
                didLegsStraightenInRep = true
            }

            // cek transisi ke atas (kembali berbaring)
            if (averageBodyAngle > UP_BODY_ANGLE_THRESHOLD) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0

                    // repetisi selesai, mulai analisis catatan
                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. cek puncak gerakan (duduk)
                    if (minBodyAngleInRep > DOWN_BODY_ANGLE_THRESHOLD) {
                        feedbackReport.add("Naik lebih tinggi")
                        repIsValid = false // gerakan tidak cukup tinggi
                    }

                    // 2. cek kaki
                    if (didLegsStraightenInRep) {
                        feedbackReport.add("Jaga lutut tetap tertekuk")
                        repIsValid = false // kaki lurus = rep tidak sah
                    }

                    // hitung repetisi hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    return RepResult(repCount, "TURUN", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        // jika 'UP' (berbaring), instruksinya 'NAIK'
        // jika 'DOWN' (duduk), instruksinya 'TURUN'
        val status = if (currentState == ExerciseState.UP) "TURUN" else "NAIK"
        // jangan kirim feedback di antara repetisi
        return RepResult(repCount, status, null, null)
    }
}