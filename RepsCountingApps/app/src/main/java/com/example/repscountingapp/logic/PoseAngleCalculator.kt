package com.example.repscountingapp.logic

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2
import kotlin.math.absoluteValue

object PoseAngleCalculator {
    fun calculateAngle(p1: PoseLandmark, p2: PoseLandmark, p3: PoseLandmark): Double {
        val radians = atan2(p3.position.y - p2.position.y, p3.position.x - p2.position.x) -
                atan2(p1.position.y - p2.position.y, p1.position.x - p2.position.x)
        var angle = Math.toDegrees(radians.toDouble())
        angle = angle.absoluteValue
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle
    }
}
