package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min

class DipsCounter {

    companion object {
        // ambang batas siku lurus (posisi atas)
        private const val UP_ELBOW_ANGLE_THRESHOLD = 150.0
        // ambang batas siku menekuk (posisi bawah)
        private const val DOWN_ELBOW_ANGLE_THRESHOLD = 120.0

        // sudut badan (bahu-pinggul-lutut)
        // 90 derajat adalah l-sit sempurna. toleransi diberikan untuk kursi tinggi.
        private const val CALIBRATION_BODY_ANGLE_MIN = 80.0
        private const val CALIBRATION_BODY_ANGLE_MAX = 140.0

        // sudut kaki (pinggul-lutut-ankle)
        // 180 lurus, 100 ditekuk. di bawah 100 dianggap terlalu menekuk.
        private const val CALIBRATION_KNEE_ANGLE_MIN = 100.0

        // toleransi jarak tangan-pinggul (tetap)
        private const val CALIBRATION_WRIST_HIP_RATIO_MAX = 0.3f

        private const val VERTICAL_MOVEMENT_THRESHOLD_RATIO = 0.1f

        // nunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    // patokan posisi y bahu saat kalibrasi
    private var calibratedUpShoulderY: Float = 0f

    // ini 'catatan' data selama satu repetisi
    private var minElbowAngleInRep: Double = 180.0

    // reset hitungan kalau orangnya keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ExerciseState.UP
        calibratedUpShoulderY = 0f
        resetRepMetrics()
    }

    private fun resetRepMetrics() {
        minElbowAngleInRep = 180.0
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


        val landmarks = listOf(
            leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist,
            leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle
        )

        // reset jika ada sendi tidak terlihat
        if (landmarks.any { it == null }) {
            resetState()
            // kirim status tubuh tidak terlihat
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        // sudut utama: bahu-siku-pergelangan tangan
        val leftElbowAngle = PoseAngleCalculator.calculateAngle(leftShoulder!!, leftElbow!!, leftWrist!!)
        val rightElbowAngle = PoseAngleCalculator.calculateAngle(rightShoulder!!, rightElbow!!, rightWrist!!)
        val averageElbowAngle = (leftElbowAngle + rightElbowAngle) / 2

        // sudut cek 'posisi duduk': bahu-pinggul-lutut
        val leftBodyAngle = PoseAngleCalculator.calculateAngle(leftShoulder, leftHip!!, leftKnee!!)
        val rightBodyAngle = PoseAngleCalculator.calculateAngle(rightShoulder, rightHip!!, rightKnee!!)
        val averageBodyAngle = (leftBodyAngle + rightBodyAngle) / 2

        // sudut cek 'kaki': pinggul-lutut-pergelangan kaki
        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip, leftKnee, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip, rightKnee, rightAnkle!!)
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        // data posisi y (vertikal)
        val avgWristY = (leftWrist.position.y + rightWrist.position.y) / 2
        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2


        if (!isCalibrated) {

            val torsoHeight = abs(avgShoulderY - avgHipY)

            // 1. cek badan (harus dalam rentang 80-140 derajat)
            val isBodyShapeCorrect = averageBodyAngle in CALIBRATION_BODY_ANGLE_MIN..CALIBRATION_BODY_ANGLE_MAX

            // 2. cek kaki (boleh lurus atau ditekuk sedikit, > 100 derajat)
            val isLegShapeCorrect = averageKneeAngle > CALIBRATION_KNEE_ANGLE_MIN

            // 3. cek tangan dekat pinggul
            val areHandsPlacedCorrectly = if (torsoHeight > 0) {
                abs(avgWristY - avgHipY) < (torsoHeight * CALIBRATION_WRIST_HIP_RATIO_MAX)
            } else false

            // kirim feedback spesifik
            if (!isBodyShapeCorrect) {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Posisikan badan di kursi (tegak)", null)
            }
            if (!isLegShapeCorrect) {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Luruskan kaki ke depan (min 100°)", null)
            }
            if (!areHandsPlacedCorrectly) {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Posisikan tangan di tepi kursi", null)
            }

            // jika form benar, cek lengan lurus untuk mulai
            if (averageElbowAngle > UP_ELBOW_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true // kalibrasi berhasil
                    calibratedUpShoulderY = avgShoulderY // simpan patokan y
                }
            } else {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Luruskan lengan", null)
            }

            return RepResult(repCount, "Kalibrasi", "Tahan posisi...", null)
        }

        // hitung batas gerakan vertikal
        val torsoHeight = abs(avgShoulderY - avgHipY)
        val movementThreshold = torsoHeight * VERTICAL_MOVEMENT_THRESHOLD_RATIO

        // logika utama penghitungan repetisi
        if (currentState == ExerciseState.UP) {
            // cek transisi dari atas (lurus) ke bawah (menekuk)
            val isMovingDown = avgShoulderY > calibratedUpShoulderY + movementThreshold

            if (averageElbowAngle < 140.0 && isMovingDown) { // mulai gerakan turun
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
            minElbowAngleInRep = min(minElbowAngleInRep, averageElbowAngle)

            // cek transisi ke atas (kembali lurus)
            if (averageElbowAngle > UP_ELBOW_ANGLE_THRESHOLD) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ExerciseState.UP
                    framesInTargetState = 0
                    calibratedUpShoulderY = avgShoulderY // update patokan y

                    // repetisi selesai, mulai analisis catatan
                    val feedbackReport = mutableSetOf<String>()
                    var repIsValid = true

                    // 1. cek kedalaman siku
                    if (minElbowAngleInRep > DOWN_ELBOW_ANGLE_THRESHOLD) {
                        feedbackReport.add("Turun lebih rendah")
                        repIsValid = false // gerakan tidak cukup dalam
                    }

                    // hitung repetisi hanya jika valid
                    if (repIsValid) {
                        repCount++
                    }

                    // status baru sekarang up (lengan lurus), jadi instruksinya turun
                    return RepResult(repCount, "TURUN", null, feedbackReport.ifEmpty { null })
                }
            } else {
                framesInTargetState = 0
            }
        }

        val status = if (currentState == ExerciseState.UP) "NAIK" else "TURUN"
        // jangan kirim feedback di antara repetisi
        return RepResult(repCount, status, null, null)
    }
}