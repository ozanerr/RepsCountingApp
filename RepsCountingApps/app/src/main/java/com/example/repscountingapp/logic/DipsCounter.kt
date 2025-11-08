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
        private const val DOWN_ELBOW_ANGLE_THRESHOLD = 120.0 // sebelumnya 100.0

        // 1. badan harus < 110 (posisi 'l')
        private const val CALIBRATION_BODY_ANGLE_MAX = 110.0
        // 2. kaki harus > 130 (relatif lurus)
        private const val CALIBRATION_KNEE_ANGLE_MIN = 130.0
        // 3. toleransi jarak tangan-pinggul (30% dari tinggi torso)
        private const val CALIBRATION_WRIST_HIP_RATIO_MAX = 0.3f

        private const val VERTICAL_MOVEMENT_THRESHOLD_RATIO = 0.1f // butuh 10% gerakan

        // nunggu 3 frame biar stabil
        private const val CONFIRMATION_FRAMES = 3
    }

    private var repCount = 0
    private var currentState = ExerciseState.UP // mulai dari atas (lengan lurus)
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    // patokan posisi y bahu saat kalibrasi
    private var calibratedUpShoulderY: Float = 0f

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

        // sudut cek 'berdiri': bahu-pinggul-lutut
        val leftBodyAngle = PoseAngleCalculator.calculateAngle(leftShoulder, leftHip!!, leftKnee!!)
        val rightBodyAngle = PoseAngleCalculator.calculateAngle(rightShoulder, rightHip!!, rightKnee!!)
        val averageBodyAngle = (leftBodyAngle + rightBodyAngle) / 2

        // sudut cek 'kaki lurus': pinggul-lutut-pergelangan kaki
        val leftKneeAngle = PoseAngleCalculator.calculateAngle(leftHip, leftKnee, leftAnkle!!)
        val rightKneeAngle = PoseAngleCalculator.calculateAngle(rightHip, rightKnee, rightAnkle!!)
        val averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        // data posisi y (vertikal)
        val avgWristY = (leftWrist.position.y + rightWrist.position.y) / 2
        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2


        if (!isCalibrated) {

            // hitung 3 syarat form awal
            val torsoHeight = abs(avgShoulderY - avgHipY)
            val isBodyShapeCorrect = averageBodyAngle < CALIBRATION_BODY_ANGLE_MAX
            val isLegShapeCorrect = averageKneeAngle > CALIBRATION_KNEE_ANGLE_MIN

            // pastikan torsoHeight tidak nol
            val areHandsPlacedCorrectly = if (torsoHeight > 0) {
                abs(avgWristY - avgHipY) < (torsoHeight * CALIBRATION_WRIST_HIP_RATIO_MAX)
            } else false

            // kirim feedback spesifik berdasarkan apa yang salah
            if (!isBodyShapeCorrect) {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Duduk di tepi kursi", null)
            }
            if (!isLegShapeCorrect) {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Luruskan kaki ke depan", null)
            }
            if (!areHandsPlacedCorrectly) {
                stableUpFrames = 0
                return RepResult(repCount, "Kalibrasi", "Posisikan tangan di tepi kursi", null)
            }

            // jika semua form benar, baru cek lengan lurus
            if (averageElbowAngle > UP_ELBOW_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true // kalibrasi berhasil!
                    calibratedUpShoulderY = avgShoulderY // simpan patokan y
                }
            } else {
                stableUpFrames = 0
                // form sudah benar, tinggal luruskan lengan
                return RepResult(repCount, "Kalibrasi", "Luruskan lengan", null)
            }

            // default jika masih proses
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
            // minKneeAngleInRep = min(minKneeAngleInRep, averageKneeAngle) // (dihapus)

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

                    // if (minKneeAngleInRep < LEG_BENT_THRESHOLD) {
                    //     feedbackReport.add("Jaga kaki tetap lurus")
                    //     repIsValid = false
                    // }

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

        // jika 'up' (lengan lurus), instruksinya 'turun'
        // jika 'down' (lengan nekuk), instruksinya 'naik'
        val status = if (currentState == ExerciseState.UP) "TURUN" else "NAIK"
        // jangan kirim feedback di antara repetisi
        return RepResult(repCount, status, null, null)
    }
}