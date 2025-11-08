package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max
import kotlin.math.min

class BackExtensionCounter {

    companion object {
        // ambang batas untuk posisi berbaring (lurus)
        private const val UP_BODY_ANGLE_THRESHOLD = 170.0
        // ambang batas untuk posisi puncak (melengkung)
        // (sudut telinga-bahu-pinggul)
        private const val DOWN_BODY_ANGLE_THRESHOLD = 155.0

        // nunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP // mulai dari berbaring lurus
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    // sudut badan terkecil (puncak angkatan)
    private var minBodyAngleInRep: Double = 180.0

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
        // kita butuh telinga untuk latihan ini
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)

        val landmarks = listOf(
            leftShoulder, rightShoulder, leftHip, rightHip,
            leftEar, rightEar
        )

        // reset jika ada sendi tidak terlihat
        if (landmarks.any { it == null }) {
            resetState()
            // kirim status tubuh tidak terlihat
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        // sudut utama: telinga-bahu-pinggul
        val leftBodyAngle = PoseAngleCalculator.calculateAngle(leftEar!!, leftShoulder!!, leftHip!!)
        val rightBodyAngle = PoseAngleCalculator.calculateAngle(rightEar!!, rightShoulder!!, rightHip!!)
        val averageBodyAngle = (leftBodyAngle + rightBodyAngle) / 2


        // proses kalibrasi, minta pengguna berbaring telungkup
        if (!isCalibrated) {
            // cek apakah sudah di posisi berbaring (lurus)
            if (averageBodyAngle > UP_BODY_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                }
            } else {
                stableUpFrames = 0
            }
            // kirim status kalibrasi
            return RepResult(repCount, "Kalibrasi", "Berbaring telungkup", null)
        }

        // logika utama penghitungan repetisi
        if (currentState == ExerciseState.UP) {
            // cek transisi dari atas (lurus) ke bawah (melengkung)
            if (averageBodyAngle < 165.0) { // mulai gerakan naik
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

            // catat metrik terburuk (sudut terkecil) selama posisi naik
            minBodyAngleInRep = min(minBodyAngleInRep, averageBodyAngle)

            // cek transisi ke atas (kembali lurus/berbaring)
            if (averageBodyAngle > UP_BODY_ANGLE_THRESHOLD) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0

                    // repetisi selesai, mulai analisis catatan
                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. cek puncak gerakan (melengkung)
                    if (minBodyAngleInRep > DOWN_BODY_ANGLE_THRESHOLD) {
                        feedbackReport.add("Angkat dada lebih tinggi")
                        repIsValid = false // gerakan tidak cukup tinggi
                    }

                    // hitung repetisi hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    // status baru sekarang up (berbaring), jadi instruksinya naik
                    return RepResult(repCount, "NAIK", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        // jika 'up' (berbaring), instruksinya 'naik'
        // jika 'down' (puncak), instruksinya 'turun'
        val status = if (currentState == ExerciseState.UP) "NAIK" else "TURUN"
        // jangan kirim feedback di antara repetisi
        return RepResult(repCount, status, null, null)
    }
}