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
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.repscountingapp.logic.ShoulderTapCounter // <-- TAMBAHKAN IMPORT INI
import com.example.repscountingapp.logic.SitupCounter
import com.example.repscountingapp.logic.SquatCounter
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class ExerciseActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityExerciseBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector

    // ini semua logika yang lagi jalan
    private var pushupCounter: PushupCounter? = null
    private var squatCounter: SquatCounter? = null
    private var lungesCounter: LungesCounter? = null
    private var situpCounter: SitupCounter? = null
    private var highKneesCounter: HighKneesCounter? = null
    private var gluteBridgesCounter: GluteBridgesCounter? = null
    private var backExtensionCounter: BackExtensionCounter? = null
    private var dipsCounter: DipsCounter? = null
    private var shoulderTapCounter: ShoulderTapCounter? = null // <-- TAMBAHKAN INI
    private var activeExerciseType: String? = null

    // ini buat ngurusin sensor goyang
    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    @Volatile private var isPhoneStable = true
    private var lastShakeTimestamp: Long = 0
    // hitungan frame goyang berturut-turut
    private var unstableFrameCount = 0

    @Inject
    lateinit var repository: LatihanRepository
    private var isSaving = false

    private var lastRepCount = 0
    private var firstRepBitmap: Bitmap? = null

    companion object {
        // ambang batas disesuaikan
        private const val MOVEMENT_THRESHOLD = 0.5f
        // harus goyang 3 frame berturut-turut
        private const val UNSTABLE_FRAME_THRESHOLD = 3
        // waktu tenang setelah goyangan terdeteksi
        private const val STABLE_TIME_MS = 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            "SHOULDER_TAP" -> shoulderTapCounter = ShoulderTapCounter() // <-- TAMBAHKAN INI
        }

        requestCameraPermission()
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializePoseDetector()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // minta sensor yang spesifik (linear acceleration)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        binding.finishButton.isEnabled = false

        binding.finishButton.setOnClickListener {
            saveLatihanAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        // nyalain sensor pas aplikasi dibuka
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        // matiin sensor pas aplikasi ditutup, biar hemat baterai
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // nggak perlu diapa-apain
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // fungsi ini ngecek terus-terusan apakah hp-nya lagi goyang
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // hitung total gerakan di semua sumbu
            val movementMagnitude = sqrt(x * x + y * y + z * z)

            // cek apakah gerakannya ngelewatin batas toleransi
            if (movementMagnitude > MOVEMENT_THRESHOLD) {
                // gerakan terdeteksi, tambah hitungan frame goyang
                unstableFrameCount++

                // kalau sudah 3 frame goyang terus, baru kita flag
                if (unstableFrameCount >= UNSTABLE_FRAME_THRESHOLD) {
                    isPhoneStable = false
                    lastShakeTimestamp = System.currentTimeMillis()
                }
            } else {
                // gerakan di bawah threshold (dianggap diam)
                unstableFrameCount = 0 // reset hitungan frame goyang

                // cek udah diem berapa lama
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

                                    // minta hasil analisis dari logika yang aktif
                                    val result: RepResult = when (activeExerciseType) {
                                        "PUSHUP" -> pushupCounter!!.analyzePose(pose, isPhoneStable)
                                        "SQUAT" -> squatCounter!!.analyzePose(pose, isPhoneStable)
                                        "LUNGES" -> lungesCounter!!.analyzePose(pose, isPhoneStable)
                                        "SITUP" -> situpCounter!!.analyzePose(pose, isPhoneStable)
                                        "HIGH_KNEES" -> highKneesCounter!!.analyzePose(pose, isPhoneStable)
                                        "GLUTE_BRIDGES" -> gluteBridgesCounter!!.analyzePose(pose, isPhoneStable)
                                        "BACK_EXTENTION" -> backExtensionCounter!!.analyzePose(pose, isPhoneStable)
                                        "DIPS" -> dipsCounter!!.analyzePose(pose, isPhoneStable)
                                        "SHOULDER_TAP" -> shoulderTapCounter!!.analyzePose(pose, isPhoneStable) // <-- TAMBAHKAN INI
                                        else -> RepResult(0, "Error", null, null)
                                    }

                                    runOnUiThread {
                                        binding.statusText.text = result.status

                                        val repJustIncreased = result.count > lastRepCount
                                        if (repJustIncreased) {
                                            lastRepCount = result.count
                                            binding.repCountText.text = result.count.toString()

                                            if (result.count == 1) {
                                                binding.finishButton.isEnabled = true
                                                firstRepBitmap = binding.viewFinder.bitmap
                                            }

                                            binding.feedbackCard.visibility = View.GONE
                                        }

                                        var feedbackToShow: String? = null
                                        if (result.postRepFeedback != null && result.postRepFeedback.isNotEmpty()) {
                                            feedbackToShow = result.postRepFeedback.joinToString("\n")

                                        } else if (result.realTimeFeedback != null) {
                                            if (lastRepCount == 0) {
                                                // izinkan semua pesan kalibrasi
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
                                                }
                                            } else {
                                                feedbackToShow = result.realTimeFeedback
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

    private fun saveLatihanAndFinish() {
        if (isSaving) return

        val repCount = binding.repCountText.text.toString().toIntOrNull() ?: 0

        if (repCount == 0) {
            Toast.makeText(this, "Repetisi masih 0, tidak ada yang disimpan.", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        Toast.makeText(this, "Menyimpan...", Toast.LENGTH_SHORT).show()

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
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}