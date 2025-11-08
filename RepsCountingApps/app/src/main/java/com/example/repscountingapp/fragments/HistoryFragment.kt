package com.example.repscountingapp.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.repscountingapp.adapters.HistoryListAdapter
import com.example.repscountingapp.database.LatihanHistory
import com.example.repscountingapp.databinding.FragmentHistoryBinding
import com.example.repscountingapp.viewmodels.HistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // ini ambil pakai hilt
    private val viewModel: HistoryViewModel by viewModels()

    private var adapter: HistoryListAdapter? = null
    private val calendar = Calendar.getInstance()
    private var startTimestamp: Long = 0
    private var endTimestamp: Long = 0

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // siapin tombol-tombol filter
        setupFilterButtons()

        // amati data dari viewmodel.
        // kalau datanya berubah (misal karena difilter atau dihapus), ui-nya otomatis update
        viewModel.historyData.observe(viewLifecycleOwner) { historyList ->
            if (historyList.isEmpty()) {
                binding.historyListView.visibility = View.GONE
                binding.emptyText.visibility = View.VISIBLE
            } else {
                binding.historyListView.visibility = View.VISIBLE
                binding.emptyText.visibility = View.GONE
            }

            // kita 'suntikkan' fungsi hapus ke dalam adapter
            adapter = HistoryListAdapter(requireContext(), historyList) { historyItem ->
                // ini yang akan dijalankan kalau tombol hapus di adapter diklik
                showDeleteConfirmation(historyItem)
            }
            binding.historyListView.adapter = adapter
        }
    }

    private fun setupFilterButtons() {
        binding.btnStartDate.setOnClickListener {
            showDatePicker(true)
        }

        binding.btnEndDate.setOnClickListener {
            showDatePicker(false)
        }

        binding.btnFilter.setOnClickListener {
            applyFilter()
        }

        binding.btnResetFilter.setOnClickListener {
            resetFilter()
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                // set ke awal hari
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)

                if (isStartDate) {
                    startTimestamp = calendar.timeInMillis
                    binding.btnStartDate.text = dateFormatter.format(calendar.time)
                } else {
                    // set ke akhir hari
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    endTimestamp = calendar.timeInMillis
                    binding.btnEndDate.text = dateFormatter.format(calendar.time)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun applyFilter() {
        if (startTimestamp == 0L || endTimestamp == 0L) {
            Toast.makeText(context, "Pilih tanggal mulai dan akhir", Toast.LENGTH_SHORT).show()
            return
        }

        if (startTimestamp > endTimestamp) {
            Toast.makeText(context, "Tanggal mulai tidak boleh setelah tanggal akhir", Toast.LENGTH_SHORT).show()
            return
        }

        // cek rentang maksimal 30 hari
        val diff = endTimestamp - startTimestamp
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days > 30) {
            Toast.makeText(context, "Rentang filter maksimal 30 hari", Toast.LENGTH_SHORT).show()
            return
        }

        // suruh viewmodel buat nge-filter data
        viewModel.setDateFilter(startTimestamp, endTimestamp)
    }

    private fun resetFilter() {
        binding.btnStartDate.text = "Tgl Mulai"
        binding.btnEndDate.text = "Tgl Akhir"
        startTimestamp = 0L
        endTimestamp = 0L
        viewModel.resetFilter()
    }

    private fun showDeleteConfirmation(history: LatihanHistory) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Riwayat")
            .setMessage("Yakin mau hapus latihan ${history.namaLatihan} ini?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteHistory(history)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}