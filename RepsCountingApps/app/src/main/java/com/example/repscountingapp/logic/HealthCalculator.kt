package com.example.repscountingapp.logic

import kotlin.math.log10

object HealthCalculator {

    fun calculateBMI(weightKg: Double, heightCm: Double): Double {
        if (heightCm == 0.0) return 0.0
        val heightM = heightCm / 100
        return weightKg / (heightM * heightM)
    }

    // rumus us navy method
    fun calculateBodyFat(
        gender: String,
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double
    ): Double? {
        if (heightCm == 0.0 || neckCm == 0.0 || waistCm == 0.0) return null

        // kalau wanita butuh hip, kalau hip 0 dianggap data belum lengkap
        if (gender == "Female" && hipCm == 0.0) return null

        return try {
            if (gender == "Male") {
                // rumus pria
                495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
            } else {
                // rumus wanita
                495.0 / (1.29579 - 0.35004 * log10(waistCm + hipCm - neckCm) + 0.22100 * log10(heightCm)) - 450.0
            }
        } catch (e: Exception) {
            null // kalau perhitungan error (misal log minus), return null
        }
    }

    fun getBMICategory(bmi: Double): String {
        return when {
            bmi < 18.5 -> "Kurus"
            bmi < 24.9 -> "Normal"
            bmi < 29.9 -> "Gemuk"
            else -> "Obesitas"
        }
    }
}