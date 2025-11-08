package com.example.repscountingapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.repscountingapp.database.LatihanHistory
import com.example.repscountingapp.database.LatihanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: LatihanRepository
) : ViewModel() {

    private val filterDates = MutableLiveData<Pair<Long, Long>?>(null)

    val historyData: LiveData<List<LatihanHistory>> = filterDates.switchMap { filter ->
        if (filter == null) {
            repository.allHistory.asLiveData()
        } else {
            repository.getHistoryBetweenDates(filter.first, filter.second).asLiveData()
        }
    }

    init {
        // tetap jalankan pembersihan data lama
        viewModelScope.launch {
            repository.deleteOldHistory()
        }
    }

    fun setDateFilter(startTimestamp: Long, endTimestamp: Long) {
        filterDates.value = Pair(startTimestamp, endTimestamp)
    }

    fun resetFilter() {
        filterDates.value = null
    }

    fun deleteHistory(history: LatihanHistory) {
        viewModelScope.launch {
            repository.delete(history)
        }
    }
}