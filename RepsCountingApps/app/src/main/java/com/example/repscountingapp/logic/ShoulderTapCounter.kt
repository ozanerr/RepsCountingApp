package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

private enum class ShoulderTapState {
    WAITING_FOR_LEFT_TAP, // nunggu tangan kiri nyentuh bahu kanan
    WAITING_FOR_RIGHT_TAP // nunggu tangan kanan nyentuh bahu kiri
}

class ShoulderTapCounter {

    companion object {
        private const val PLANK_ELBOW_ANGLE_THRESHOLD = 130.0


        // toleransi goyangan pinggang (ini dicek pas repetisi)
        private const val BODY_SWAY_THRESHOLD_LOW = 150.0
        private const val BODY_SWAY_THRESHOLD_HIGH = 190.0
        // toleransi lengan nekuk
        private const val ELBOW_BEND_THRESHOLD = 140.0

        // nunggu 2 frame biar stabil
        private const val CONFIRMATION_FRAMES = 2
    }

    private var repCount = 0
    private var currentState = ShoulderTapState.WAITING_FOR_LEFT_TAP
    private var framesInTargetState = 0
    private var isCalibrated = false
    private var stableUpFrames = 0

    private var wasBodyStable: Boolean = true
    private var didElbowsBend: Boolean = false
    private var didLeftHandTap: Boolean = false
    private var didRightHandTap: Boolean = false

    // reset hitungan kalau orangnya keluar frame
    private fun resetState() {
        framesInTargetState = 0
        stableUpFrames = 0
        isCalibrated = false
        currentState = ShoulderTapState.WAITING_FOR_LEFT_TAP
        resetRepMetrics()
    }

    private fun resetRepMetrics() {
        wasBodyStable = true
        didElbowsBend = false
        didLeftHandTap = false
        didRightHandTap = false
    }

    // fungsi bantu hitung jarak 2d
    private fun getDistance(p1: PoseLandmark, p2: PoseLandmark): Double {
        // kita ubah hasil akhirnya (float) jadi double
        return sqrt((p1.position.x - p2.position.x).pow(2) + (p1.position.y - p2.position.y).pow(2)).toDouble()
    }

    fun analyzePose(pose: Pose, isPhoneStable: Boolean): RepResult {

        if (!isPhoneStable) {
            framesInTargetState = 0
            stableUpFrames = 0
            // kirim ponsel goyang sebagai feedback real-time
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
            leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist,
            leftHip, rightHip, leftKnee, rightKnee,
            leftAnkle, rightAnkle
        )

        // reset jika ada sendi tidak terlihat
        if (landmarks.any { it == null }) {
            resetState()
            // kirim ini sebagai feedback real-time
            return RepResult(repCount, "--", "Pastikan tubuh terlihat", null)
        }

        val leftElbowAngle = PoseAngleCalculator.calculateAngle(leftShoulder!!, leftElbow!!, leftWrist!!)
        val rightElbowAngle = PoseAngleCalculator.calculateAngle(rightShoulder!!, rightElbow!!, rightWrist!!)
        val averageElbowAngle = (leftElbowAngle + rightElbowAngle) / 2

        val leftBodyAngle = PoseAngleCalculator.calculateAngle(leftShoulder, leftHip!!, leftKnee!!)
        val rightBodyAngle = PoseAngleCalculator.calculateAngle(rightShoulder, rightHip!!, rightKnee!!)
        val averageBodyAngle = (leftBodyAngle + rightBodyAngle) / 2



        val shoulderWidth = getDistance(leftShoulder, rightShoulder)
        // tap dianggap sah jika jarak tangan ke bahu lawan < 30% lebar bahu
        val tapThreshold = shoulderWidth * 0.3
        val isLeftTapping = getDistance(leftWrist, rightShoulder) < tapThreshold
        val isRightTapping = getDistance(rightWrist, leftShoulder) < tapThreshold

        if (!isCalibrated) {
            // cek apakah lengan sudah lurus
            if (averageElbowAngle > PLANK_ELBOW_ANGLE_THRESHOLD) {
                stableUpFrames++
                if (stableUpFrames >= CONFIRMATION_FRAMES + 2) {
                    isCalibrated = true
                }
            } else {
                stableUpFrames = 0
            }
            // kirim status kalibrasi
            return RepResult(repCount, "Kalibrasi", "Tahan posisi plank", null)
        }

        var feedbackReport: Set<String>? = null

        // catat form terburuk
        // cek kelurusan badan (pinggul-bahu-lutut)
        if (averageBodyAngle < BODY_SWAY_THRESHOLD_LOW || averageBodyAngle > BODY_SWAY_THRESHOLD_HIGH) {
            wasBodyStable = false
        }

        // cek lengan tumpuan (yang tidak gerak)
        if (currentState == ShoulderTapState.WAITING_FOR_LEFT_TAP) {
            // lengan kanan harus lurus
            if (rightElbowAngle < ELBOW_BEND_THRESHOLD) didElbowsBend = true
        } else {
            // lengan kiri harus lurus
            if (leftElbowAngle < ELBOW_BEND_THRESHOLD) didElbowsBend = true
        }


        if (currentState == ShoulderTapState.WAITING_FOR_LEFT_TAP) {
            // nunggu tangan kiri nyentuh bahu kanan
            if (isLeftTapping) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ShoulderTapState.WAITING_FOR_RIGHT_TAP
                    framesInTargetState = 0
                    didLeftHandTap = true // tandai tap kiri berhasil
                }
            } else {
                framesInTargetState = 0
            }
        } else { // currentState == ShoulderTapState.WAITING_FOR_RIGHT_TAP
            // nunggu tangan kanan nyentuh bahu kiri
            if (isRightTapping) {
                framesInTargetState++
                if (framesInTargetState >= CONFIRMATION_FRAMES) {
                    currentState = ShoulderTapState.WAITING_FOR_LEFT_TAP
                    framesInTargetState = 0
                    didRightHandTap = true // tandai tap kanan berhasil

                    var repIsValid = true
                    val feedbackMessages = mutableSetOf<String>()

                    // 1. cek stabilitas pinggang
                    if (!wasBodyStable) {
                        feedbackMessages.add("Jaga pinggang tetap lurus")
                        repIsValid = false
                    }

                    // 2. cek lengan lurus
                    if (didElbowsBend) {
                        feedbackMessages.add("Jaga lengan tumpuan tetap lurus")
                        repIsValid = false
                    }

                    // 3. cek kedua tangan nyentuh
                    if (!didLeftHandTap || !didRightHandTap) {
                        feedbackMessages.add("Sentuh bahu lebih tinggi")
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
        val status = if (currentState == ShoulderTapState.WAITING_FOR_LEFT_TAP) "TAP KIRI" else "TAP KANAN"

        // kirim laporan
        return RepResult(repCount, status, null, feedbackReport)
    }
}