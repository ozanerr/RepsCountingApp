package com.example.repscountingapp.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import com.example.repscountingapp.ExerciseActivity

object InstructionDialogBuilder {

    // ini fungsi utama yang dipanggil dari LatihanFragment
    fun showInstructions(
        context: Context, // butuh context buat nampilin dialog
        title: String,
        exerciseType: String,
        instructionPoints: List<String>
    ) {
        // format poin-poin instruksi jadi satu teks
        val message = "pastikan:\n\n" + instructionPoints.joinToString("\n") {
            "\u2022 $it" // \u2022 itu simbol poin
        }

        // buat dan tampilkan popup-nya
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Mulai") { dialog, _ ->
                // kalau 'mulai' diklik, buka ExerciseActivity
                val intent = Intent(context, ExerciseActivity::class.java).apply {
                    putExtra("EXERCISE_TYPE", exerciseType)
                }
                context.startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}