package com.example.repscountingapp.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.repscountingapp.R
import com.example.repscountingapp.databinding.FragmentProfileBinding
import com.example.repscountingapp.logic.HealthCalculator
import com.example.repscountingapp.viewmodels.ProfileViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    // Variabel sementara untuk path foto profil
    private var currentPhotoPath: String = ""

    // Launcher untuk memilih gambar dari galeri
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uri: Uri? = data?.data
            if (uri != null) {
                // Tampilkan gambar sementara
                Glide.with(this).load(uri).circleCrop().into(binding.ivProfilePicture)
                // Simpan path string (sebaiknya di copy ke internal storage di real app, tapi ini cukup utk demo)
                currentPhotoPath = uri.toString()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChart()
        setupInputs()
        observeData()

        binding.radioGroupGender.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.radioFemale.id) {
                binding.layoutHip.visibility = View.VISIBLE
            } else {
                binding.layoutHip.visibility = View.GONE
            }
        }

        binding.ivProfilePicture.setOnClickListener {
            openImagePicker()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfileData()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun setupChart() {
        val chart = binding.weeklyChart
        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.textColor = Color.WHITE
        chart.axisLeft.textColor = Color.WHITE
        // Format sumbu kiri jadi bilangan bulat (0, 1, 2...)
        chart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
        chart.axisLeft.granularity = 1f // Jarak minimal antar garis 1

        chart.legend.textColor = Color.WHITE
        chart.setNoDataText("Belum ada data latihan minggu ini")
        chart.setNoDataTextColor(Color.WHITE)
    }

    private fun setupInputs() {
        binding.radioMale.isChecked = true
    }

    private fun observeData() {
        // Ambil data profil
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            if (profile != null) {
                binding.etName.setText(profile.name)
                if (profile.age > 0) binding.etAge.setText(profile.age.toString())

                if (profile.gender == "Female") binding.radioFemale.isChecked = true else binding.radioMale.isChecked = true
                if (profile.height > 0) binding.etHeight.setText(profile.height.toString())
                if (profile.weight > 0) binding.etWeight.setText(profile.weight.toString())
                if (profile.neck > 0) binding.etNeck.setText(profile.neck.toString())
                if (profile.waist > 0) binding.etWaist.setText(profile.waist.toString())
                if (profile.hip > 0) binding.etHip.setText(profile.hip.toString())

                // Load foto profil jika ada
                if (profile.profilePicturePath.isNotEmpty()) {
                    currentPhotoPath = profile.profilePicturePath
                    try {
                        Glide.with(this)
                            .load(Uri.parse(profile.profilePicturePath))
                            .circleCrop()
                            .placeholder(android.R.drawable.ic_menu_camera)
                            .into(binding.ivProfilePicture)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                calculateAndDisplayStats(profile.gender, profile.height, profile.weight, profile.neck, profile.waist, profile.hip)
            }
        }

        // Ambil data grafik
        viewModel.weeklyHistory.observe(viewLifecycleOwner) { historyList ->
            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            val today = calendar.timeInMillis
            val dayFormat = SimpleDateFormat("EE", Locale.getDefault())

            for (i in 6 downTo 0) {
                val dayStart = today - (i * 24 * 60 * 60 * 1000L)
                val dayEnd = dayStart + (24 * 60 * 60 * 1000L)

                val totalReps = historyList
                    .filter { it.tanggal in dayStart until dayEnd }
                    .sumOf { it.jumlahRepetisi }

                entries.add(BarEntry((6-i).toFloat(), totalReps.toFloat()))
                labels.add(dayFormat.format(Date(dayStart)))
            }

            val dataSet = BarDataSet(entries, "Total Repetisi")
            dataSet.color = Color.CYAN
            dataSet.valueTextColor = Color.WHITE
            dataSet.valueTextSize = 12f

            // Format angka di atas batang jadi bilangan bulat
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }

            val data = BarData(dataSet)
            binding.weeklyChart.data = data
            binding.weeklyChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            binding.weeklyChart.invalidate()
        }
    }

    private fun saveProfileData() {
        val name = binding.etName.text.toString()
        val age = binding.etAge.text.toString().toIntOrNull() ?: 0

        val gender = if (binding.radioMale.isChecked) "Male" else "Female"
        val height = binding.etHeight.text.toString().toDoubleOrNull() ?: 0.0
        val weight = binding.etWeight.text.toString().toDoubleOrNull() ?: 0.0
        val neck = binding.etNeck.text.toString().toDoubleOrNull() ?: 0.0
        val waist = binding.etWaist.text.toString().toDoubleOrNull() ?: 0.0
        val hip = binding.etHip.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            Toast.makeText(context, "Nama wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.saveProfile(name, age, gender, height, weight, neck, waist, hip, currentPhotoPath)
        calculateAndDisplayStats(gender, height, weight, neck, waist, hip)
        Toast.makeText(context, "Profil Tersimpan", Toast.LENGTH_SHORT).show()
    }

    private fun calculateAndDisplayStats(gender: String, h: Double, w: Double, n: Double, wa: Double, hi: Double) {
        val bmi = HealthCalculator.calculateBMI(w, h)
        val bmiCat = HealthCalculator.getBMICategory(bmi)
        binding.tvBmiResult.text = String.format("BMI: %.1f (%s)", bmi, bmiCat)

        val bf = HealthCalculator.calculateBodyFat(gender, h, n, wa, hi)
        if (bf != null) {
            binding.tvBodyfatResult.text = String.format("Body Fat: %.1f%%", bf)
            binding.tvBodyfatHint.visibility = View.GONE
        } else {
            binding.tvBodyfatResult.text = "Body Fat: -"
            binding.tvBodyfatHint.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}