package com.example.repscountingapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech // Import TTS
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog // Import Dialog untuk statistik
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.repscountingapp.database.LatihanHistory
import com.example.repscountingapp.database.LatihanRepository
import com.example.repscountingapp.databinding.ActivityExerciseBinding
import com.example.repscountingapp.logic.BackExtensionCounter
import com.example.repscountingapp.logic.DipsCounter
import com.example.repscountingapp.logic.GluteBridgesCounter
import com.example.repscountingapp.logic.HighKneesCounter
import com.example.repscountingapp.logic.LungesCounter
import com.example.repscountingapp.logic.PushupCounter
import com.example.repscountingapp.logic.RepResult
import com.example.repscountingapp.logic.SitupCounter
import com.example.repscountingapp.logic.SquatCounter
import com.example.repscountingapp.logic.ShoulderTapCounter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class ExerciseActivity : AppCompatActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityExerciseBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector

    private var pushupCounter: PushupCounter? = null
    private var squatCounter: SquatCounter? = null
    private var lungesCounter: LungesCounter? = null
    private var situpCounter: SitupCounter? = null
    private var highKneesCounter: HighKneesCounter? = null
    private var gluteBridgesCounter: GluteBridgesCounter? = null
    private var backExtensionCounter: BackExtensionCounter? = null
    private var dipsCounter: DipsCounter? = null
    private var shoulderTapCounter: ShoulderTapCounter? = null
    private var activeExerciseType: String? = null

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    @Volatile private var isPhoneStable = true
    private var lastShakeTimestamp: Long = 0
    private var unstableFrameCount = 0

    @Inject
    lateinit var repository: LatihanRepository
    private var isSaving = false

    private var lastRepCount = 0
    private var firstRepBitmap: Bitmap? = null

    // --- FITUR BARU: TTS & STATISTIK ---
    private lateinit var tts: TextToSpeech
    private var lastSpokenFeedback: String = "" // Biar nggak ngulang feedback yang sama terus menerus
    private var lastSpokenTime: Long = 0

    // Variabel untuk menyimpan statistik error: "Nama Error" -> Jumlah
    private val errorStatistics = mutableMapOf<String, Int>()
    // -----------------------------------

    companion object {
        private const val MOVEMENT_THRESHOLD = 0.5f
        private const val UNSTABLE_FRAME_THRESHOLD = 3
        private const val STABLE_TIME_MS = 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi TTS
        tts = TextToSpeech(this, this)

        activeExerciseType = intent.getStringExtra("EXERCISE_TYPE")

        when (activeExerciseType) {
            "PUSHUP" -> pushupCounter = PushupCounter()
            "SQUAT" -> squatCounter = SquatCounter()
            "LUNGES" -> lungesCounter = LungesCounter()
            "SITUP" -> situpCounter = SitupCounter()
            "HIGH_KNEES" -> highKneesCounter = HighKneesCounter()
            "GLUTE_BRIDGES" -> gluteBridgesCounter = GluteBridgesCounter()
            "BACK_EXTENTION" -> backExtensionCounter = BackExtensionCounter()
            "DIPS" -> dipsCounter = DipsCounter()
            "SHOULDER_TAP" -> shoulderTapCounter = ShoulderTapCounter()
        }

        requestCameraPermission()
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializePoseDetector()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        binding.finishButton.isEnabled = false

        // Ubah logika tombol selesai untuk menampilkan statistik dulu
        binding.finishButton.setOnClickListener {
            showCompletionDialog()
        }
    }

    // --- SETUP TTS ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Coba Bahasa Indonesia, kalau gak ada fallback ke Inggris
            val result = tts.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
    }

    // Fungsi pintar untuk bicara
    private fun speak(text: String, isPriority: Boolean = false) {
        if (isPriority) {
            // Kalau prioritas (hitungan repetisi), potong suara lain (FLUSH)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            // Kalau feedback, antrikan di belakang (ADD)
            // Cek biar nggak spamming feedback yang sama dalam waktu singkat (misal 3 detik)
            val currentTime = System.currentTimeMillis()
            if (text != lastSpokenFeedback || (currentTime - lastSpokenTime) > 3000) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, null)
                lastSpokenFeedback = text
                lastSpokenTime = currentTime
            }
        }
    }
    // ----------------

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Stop bicara kalau aplikasi dipause
        if (::tts.isInitialized) tts.stop()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val movementMagnitude = sqrt(x * x + y * y + z * z)

            if (movementMagnitude > MOVEMENT_THRESHOLD) {
                unstableFrameCount++
                if (unstableFrameCount >= UNSTABLE_FRAME_THRESHOLD) {
                    isPhoneStable = false
                    lastShakeTimestamp = System.currentTimeMillis()
                }
            } else {
                unstableFrameCount = 0
                if (System.currentTimeMillis() - lastShakeTimestamp > STABLE_TIME_MS) {
                    isPhoneStable = true
                }
            }
        }
    }

    private fun initializePoseDetector() {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isSaving) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                            val sourceWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
                            val sourceHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height
                            poseDetector.process(image)
                                .addOnSuccessListener { pose ->
                                    binding.overlay.drawPose(pose, sourceWidth, sourceHeight)

                                    val result: RepResult = when (activeExerciseType) {
                                        "PUSHUP" -> pushupCounter!!.analyzePose(pose, isPhoneStable)
                                        "SQUAT" -> squatCounter!!.analyzePose(pose, isPhoneStable)
                                        "LUNGES" -> lungesCounter!!.analyzePose(pose, isPhoneStable)
                                        "SITUP" -> situpCounter!!.analyzePose(pose, isPhoneStable)
                                        "HIGH_KNEES" -> highKneesCounter!!.analyzePose(pose, isPhoneStable)
                                        "GLUTE_BRIDGES" -> gluteBridgesCounter!!.analyzePose(pose, isPhoneStable)
                                        "BACK_EXTENTION" -> backExtensionCounter!!.analyzePose(pose, isPhoneStable)
                                        "DIPS" -> dipsCounter!!.analyzePose(pose, isPhoneStable)
                                        "SHOULDER_TAP" -> shoulderTapCounter!!.analyzePose(pose, isPhoneStable)
                                        else -> RepResult(0, "Error", null, null)
                                    }

                                    runOnUiThread {
                                        binding.statusText.text = result.status

                                        val repJustIncreased = result.count > lastRepCount
                                        if (repJustIncreased) {
                                            lastRepCount = result.count
                                            binding.repCountText.text = result.count.toString()

                                            // 1. Suara Hitungan (Prioritas Tinggi)
                                            speak(result.count.toString(), isPriority = true)

                                            if (result.count == 1) {
                                                binding.finishButton.isEnabled = true
                                                firstRepBitmap = binding.viewFinder.bitmap
                                            }

                                            binding.feedbackCard.visibility = View.GONE
                                        }

                                        var feedbackToShow: String? = null
                                        if (result.postRepFeedback != null && result.postRepFeedback.isNotEmpty()) {

                                            // --- LOGIKA FEEDBACK & STATISTIK ---
                                            // Simpan error ke statistik
                                            for (errorMsg in result.postRepFeedback) {
                                                val currentCount = errorStatistics.getOrDefault(errorMsg, 0)
                                                errorStatistics[errorMsg] = currentCount + 1

                                                // Ucapkan error (Antrian)
                                                speak(errorMsg, isPriority = false)
                                            }

                                            feedbackToShow = result.postRepFeedback.joinToString("\n")

                                        } else if (result.realTimeFeedback != null) {
                                            // Feedback realtime (kalibrasi)
                                            if (lastRepCount == 0) {
                                                if (result.realTimeFeedback == "Pastikan tubuh terlihat" ||
                                                    result.realTimeFeedback == "Ponsel Goyang" ||
                                                    result.realTimeFeedback == "Luruskan lengan" ||
                                                    result.realTimeFeedback == "Berdiri tegak" ||
                                                    result.realTimeFeedback == "Berbaring telentang" ||
                                                    result.realTimeFeedback == "Berbaring, lutut ditekuk" ||
                                                    result.realTimeFeedback == "Berbaring telungkup" ||
                                                    result.realTimeFeedback == "Posisikan badan di kursi" ||
                                                    result.realTimeFeedback == "Tahan posisi plank" ||
                                                    result.realTimeFeedback == "Ambil posisi plank" ||
                                                    result.realTimeFeedback == "ANGKAT KIRI" ||
                                                    result.realTimeFeedback == "ANGKAT KANAN" ||
                                                    result.realTimeFeedback == "TAP KIRI" ||
                                                    result.realTimeFeedback == "TAP KANAN") {

                                                    feedbackToShow = result.realTimeFeedback
                                                    // Ucapkan feedback realtime (tapi jangan spam)
                                                    speak(result.realTimeFeedback, isPriority = false)
                                                }
                                            } else {
                                                feedbackToShow = result.realTimeFeedback
                                                speak(result.realTimeFeedback, isPriority = false)
                                            }
                                        }

                                        if (feedbackToShow != null) {
                                            binding.formFeedbackText.text = feedbackToShow
                                            binding.feedbackCard.visibility = View.VISIBLE
                                        } else if (repJustIncreased) {
                                            binding.feedbackCard.visibility = View.GONE
                                        }
                                    }
                                }
                                .addOnFailureListener { e -> Log.e("PoseDetection", "Gagal mendeteksi pose.", e) }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("CameraX", "Gagal memulai kamera.", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Izin kamera dibutuhkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> activityResultLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // --- DIALOG RINGKASAN & STATISTIK ---
    private fun showCompletionDialog() {
        // Stop proses kamera & suara sementara
        isSaving = true
        if (::tts.isInitialized) tts.stop()

        val repCount = binding.repCountText.text.toString().toIntOrNull() ?: 0

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Latihan Selesai")

        var message = "Total Repetisi: $repCount\n\n"

        if (errorStatistics.isNotEmpty()) {
            message += "Catatan Koreksi Form:\n"
            for ((error, count) in errorStatistics) {
                message += "- $error: $count kali\n"
            }
        } else {
            message += "Kerja bagus! Form gerakan sempurna."
        }

        builder.setMessage(message)
        builder.setCancelable(false) // User harus pilih tombol

        builder.setPositiveButton("Simpan") { _, _ ->
            saveLatihanAndFinish()
        }

        builder.setNegativeButton("Lanjut Latihan") { dialog, _ ->
            // Lanjutkan latihan
            isSaving = false
            dialog.dismiss()
        }

        builder.show()
    }
    // ------------------------------------

    private fun saveLatihanAndFinish() {
        // (IsSaving sudah true dari showCompletionDialog)
        Toast.makeText(this, "Menyimpan...", Toast.LENGTH_SHORT).show()

        val repCount = binding.repCountText.text.toString().toIntOrNull() ?: 0
        val exerciseName = activeExerciseType ?: "Unknown"
        val timestamp = System.currentTimeMillis()

        val bitmapToSave = firstRepBitmap

        lifecycleScope.launch(Dispatchers.IO) {
            var fotoPath = ""
            if (bitmapToSave != null) {
                fotoPath = saveImageToInternalStorage(bitmapToSave) ?: ""
            }

            val history = LatihanHistory(
                namaLatihan = exerciseName,
                jumlahRepetisi = repCount,
                tanggal = timestamp,
                fotoPath = fotoPath
            )

            repository.insert(history)

            launch(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String? {
        val fileName = "rep_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)

        return try {
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.close()
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}