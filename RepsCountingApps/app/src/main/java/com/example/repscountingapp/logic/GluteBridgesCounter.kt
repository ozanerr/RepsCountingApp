package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

class GluteBridgesCounter {

    companion object {
        // ambang batas sudut badan (bahu-pinggul-lutut)
        private const val UP_BODY_ANGLE_THRESHOLD = 135.0
        private const val DOWN_BODY_ANGLE_THRESHOLD = 160.0

        // Lutut harus selalu ditekuk saat glute bridges.
        private const val KNEE_BENT_MAX_THRESHOLD = 150.0

        // nunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP // mulai dari bawah (di lantai)
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    // sudut badan terbesar (puncak angkatan panggul)
    private var maxBodyAngleInRep: Double = 0.0

    // reset hitungan kalau orangnya keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ExerciseState.UP
        resetRepMetrics()
    }

    private fun resetRepMetrics() {
        maxBodyAngleInRep = 0.0
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

        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip, leftKnee!!, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip!!, rightKnee!!, rightAnkle!!)
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        // Cek apakah lutut lurus (indikasi salah gerakan)
        val isKneeStraight = averageKneeAngle > KNEE_BENT_MAX_THRESHOLD


        // proses kalibrasi, minta pengguna berbaring
        if (!isCalibrated) {
            // Cek 1: Badan menekuk (< 135)
            // Cek 2: Lutut HARUS ditekuk (!isKneeStraight)
            if (averageBodyAngle < UP_BODY_ANGLE_THRESHOLD && !isKneeStraight) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                }
            } else {
                stableUpFrames = 0
            }

            // Berikan feedback spesifik jika kaki lurus
            if (isKneeStraight) {
                return RepResult(repCount, "Kalibrasi", "Tekuk lutut Anda", null)
            }

            // kirim status kalibrasi
            return RepResult(repCount, "Kalibrasi", "Berbaring, lutut ditekuk", null)
        }

        // Safety Check: Jika di tengah jalan kaki lurus, reset (bukan glute bridge)
        if (isKneeStraight) {
            resetState()
            return RepResult(repCount, "--", "Tekuk lutut Anda", null)
        }

        // logika utama penghitungan repetisi
        if (currentState == ExerciseState.UP) {
            // cek transisi dari bawah (up) ke atas (down)
            if (averageBodyAngle > 145.0) { // mulai gerakan naik
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

            // catat metrik terburuk (sudut terbesar) selama posisi naik
            maxBodyAngleInRep = max(maxBodyAngleInRep, averageBodyAngle)

            // cek transisi ke atas (kembali berbaring)
            if (averageBodyAngle < UP_BODY_ANGLE_THRESHOLD) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0

                    // repetisi selesai, mulai analisis catatan
                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. cek puncak gerakan (badan lurus)
                    if (maxBodyAngleInRep < DOWN_BODY_ANGLE_THRESHOLD) {
                        feedbackReport.add("Angkat panggul lebih tinggi")
                        repIsValid = false // gerakan tidak cukup tinggi
                    }

                    // hitung repetisi hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    return RepResult(repCount, "NAIK", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        val status = if (currentState == ExerciseState.UP) "TURUN" else "NAIK"
        // jangan kirim feedback di antara repetisi
        return RepResult(repCount, status, null, null)
    }
}