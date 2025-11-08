package com.example.repscountingapp.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.repscountingapp.ExerciseActivity
import com.example.repscountingapp.R
import com.example.repscountingapp.databinding.FragmentLatihanBinding
import com.example.repscountingapp.databinding.ItemExerciseCardBinding
import android.app.AlertDialog

class LatihanFragment : Fragment() {

    private var _binding: FragmentLatihanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLatihanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // atur semua info di kartu latihan
        setupExerciseCard(
            binding.pushupLayout,
            "PUSH UP",
            "Latihan Tubuh Atas",
            R.drawable.pushup
        )
        setupExerciseCard(
            binding.squatLayout,
            "SQUAT",
            "Latihan Tubuh Bawah",
            R.drawable.squat
        )
        setupExerciseCard(
            binding.lungesLayout,
            "LUNGES",
            "Latihan Keseimbangan",
            R.drawable.lunges
        )
        setupExerciseCard(
            binding.situpLayout,
            "SIT UP",
            "Latihan Perut",
            R.drawable.situp
        )
        setupExerciseCard(
            binding.highKneesLayout,
            "HIGH KNEES",
            "Latihan Kardio Intensitas Tinggi",
            R.drawable.high_knees
        )
        setupExerciseCard(
            binding.gluteBridgesLayout,
            "GLUTE BRIDGES",
            "Latihan Panggul & Punggung",
            R.drawable.glute_bridges
        )

        setupExerciseCard(
            binding.dipsLayout,
            "DIPS",
            "Latihan Dada",
            R.drawable.dips
        )

        // setupExerciseCard(
        //     binding.backExtentionLayout,
        //     "BACK EXTENTION",
        //     "Latihan Punggung",
        //     R.drawable.back_extention
        // )

        // setupExerciseCard(
        //     binding.pullupLayout,
        //     "PULLUP",
        //     "Latihan Punggung",
        //     R.drawable.pullup
        // )

        setupExerciseCard(
            binding.shoulderTapLayout,
            "SHOULDER TAP",
            "Latihan Bahu",
            R.drawable.shoulder_tap
        )

        // bikin semua kartu jadi interaktif
        setupCardInteractivity(binding.pushupCard) { showPushupInstructions() }
        setupCardInteractivity(binding.squatCard) { showSquatInstructions() }
        setupCardInteractivity(binding.lungesCard) { showLungesInstructions() }
        setupCardInteractivity(binding.situpCard) { showSitupInstructions() }
        setupCardInteractivity(binding.highKneesCard) { showHighKneesInstructions() }
        setupCardInteractivity(binding.gluteBridgesCard) { showGluteBridgesInstructions() }
        setupCardInteractivity(binding.dipsCard) { showDipsInstructions() }

        // setupCardInteractivity(binding.backExtentionCard) { showBackExtensionInstructions() }
        setupCardInteractivity(binding.shoulderTapCard) { showShoulderTapInstructions() }

        // ini untuk latihan yang belum siap
        // setupCardInteractivity(binding.pullupCard) { Toast.makeText(context, "Pull Up belum tersedia", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // fungsi ini buat ngisi data ke tiap kartu
    private fun setupExerciseCard(view: ItemExerciseCardBinding, title: String, subtitle: String, gifResource: Int) {
        val cardTitle = view.exerciseTitle
        val cardSubtitle = view.exerciseSubtitle
        val cardBackground = view.exerciseBackground

        cardTitle.text = title
        cardSubtitle.text = subtitle

        Glide.with(this)
            .asGif()
            .load(gifResource)
            .into(cardBackground)
    }

    // fungsi ini yang ngurusin animasi pegas di setiap kartu
    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardInteractivity(card: CardView, onClickAction: () -> Unit) {
        // siapin dulu animasi pegasnya buat skala x dan y
        val scaleX = SpringAnimation(card, SpringAnimation.SCALE_X)
        val scaleY = SpringAnimation(card, SpringAnimation.SCALE_Y)

        // atur rasa pegasnya, biar mantulnya pas dan enak dilihat
        val springForce = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        scaleX.spring = springForce
        scaleY.spring = springForce

        card.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // pas kartu ditekan, kecilin dikit biar ada feedback
                    scaleX.animateToFinalPosition(0.95f)
                    scaleY.animateToFinalPosition(0.95f)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // pas jari diangkat, balikin lagi ukurannya ke semula
                    scaleX.animateToFinalPosition(1f)
                    scaleY.animateToFinalPosition(1f)
                    // baru deh, jalanin aksinya
                    onClickAction()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // kalau jari digeser keluar sebelum diangkat, batalin juga animasinya
                    scaleX.animateToFinalPosition(1f)
                    scaleY.animateToFinalPosition(1f)
                    true
                }
                else -> false
            }
        }
    }

    private fun showPushupInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Push Up",
            "PUSHUP",
            listOf(
                "smartphone diletakkan secara vertikal",
                "sikap badan menyamping dari kamera depan (90 derajat)",
                "sikap awal plank dengan tangan lurus, pastikan seluruh badan terlihat",
                "lakukan pushup dengan gerakan yang benar dan tidak terlalu cepat"
            )
        )
    }

    private fun showSquatInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Squat",
            "SQUAT",
            listOf(
                "smartphone diletakkan secara vertikal",
                "sikap badan menghadap kearah kamera depan atau menyamping kamera depan (90 derajat)",
                "sikap awal berdiri tegak dengan kedua tangan didepan dada, pastikan seluruh tubuh terlihat",
                "lakukan squat dengan gerakan yang benar dan tidak terlalu cepat"
            )
        )
    }

    private fun showLungesInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Lunges",
            "LUNGES",
            listOf(
                "smartphone diletakkan secara vertikal",
                "sikap badan menyamping arah kamera (90 derajat)",
                "sikap awal berdiri tegak, tangan disamping pinggang, pastikan seluruh tubuh terlihat",
                "lakukan lunges dengan benar dan tidak terlalu cepat"
            )
        )
    }

    private fun showSitupInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Sit Up",
            "SITUP",
            listOf(
                "smartphone diletakkan secara vertikal",
                "sikap badan menyamping arah kamera depan (90 derajat)",
                "sikap awal badan terlentang dengan lutut ditekuk, pastikan seluruh tubuh terlihat",
                "lakukan situp dengan benar dan tidak terlalu cepat"
            )
        )
    }

    private fun showHighKneesInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi High Knees",
            "HIGH_KNEES",
            listOf(
                "smartphone diletakkan vertikal",
                "sikap badan menyamping arah kamera (45 derajat)",
                "sikap awal berdiri tegak dengan tangan dipinggang, pastikan seluruh tubuh terlihat",
                "lakukan high knees dengan benar, dimulai dari kaki kiri, dan tidak terlalu cepat"
            )
        )
    }

    private fun showGluteBridgesInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Glute Bridges",
            "GLUTE_BRIDGES",
            listOf(
                "smartphone diletakkan secara vertikal",
                "sikap badan menyamping (90 derajat) dari kamera",
                "sikap awal berbaring, lutut ditekuk, pastikan seluruh badan terlihat",
                "angkat panggul hingga badan lurus dari bahu ke lutut"
            )
        )
    }

    private fun showBackExtensionInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Back Extension",
            "BACK_EXTENTION",
            listOf(
                "smartphone diletakkan secara vertikal",
                "sikap badan menyamping (90 derajat) dari kamera",
                "sikap awal berbaring telungkup, tangan di belakang kepala",
                "angkat dada dari lantai, jangan terlalu cepat"
            )
        )
    }

    private fun showDipsInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Dips",
            "DIPS",
            listOf(
                "smartphone diletakkan vertikal",
                "sikap badan menyamping kamera (90 derajat)",
                "sikap awal duduk di tepi kursi, tangan memegang tepi kursi",
                "luruskan kaki ke depan (sedikit ditekuk boleh)",
                "lakukan dips dengan benar dan tidak terlalu cepat"
            )
        )
    }

    private fun showShoulderTapInstructions() {
        InstructionDialogBuilder.showInstructions(
            requireContext(),
            "Instruksi Shoulder Tap",
            "SHOULDER_TAP",
            listOf(
                "smartphone diletakkan vertikal",
                "sikap badan menyamping kamera (45 derajat)",
                "sikap awal plank dengan tangan lurus",
                "sentuh bahu bergantian (kiri dulu) & jaga pinggul tetap stabil"
            )
        )
    }
}