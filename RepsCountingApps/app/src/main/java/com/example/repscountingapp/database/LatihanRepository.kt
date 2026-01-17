package com.example.repscountingapp.database

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.concurrent.TimeUnit

class LatihanRepository(private val latihanDao: LatihanDao, private val userProfileDao: UserProfileDao) {

    // fungsi buat nyimpen data
    suspend fun insert(history: LatihanHistory) {
        latihanDao.insert(history)
    }

    // fungsi buat ngambil semua data (buat halaman history nanti)
    val allHistory: Flow<List<LatihanHistory>> = latihanDao.getAllHistory()

    // fungsi untuk mengambil data yang sudah difilter
    fun getHistoryBetweenDates(startTimestamp: Long, endTimestamp: Long): Flow<List<LatihanHistory>> {
        return latihanDao.getHistoryBetweenDates(startTimestamp, endTimestamp)
    }

    // fungsi untuk menghapus data lama (lebih dari 30 hari)
    suspend fun deleteOldHistory() {
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        latihanDao.deleteOlderThan(thirtyDaysAgo)
    }

    suspend fun delete(history: LatihanHistory) {
        // sebelum hapus data, hapus fotonya dulu dari memori
        val file = File(history.fotoPath)
        if (file.exists()) {
            file.delete()
        }
        // baru hapus datanya dari database
        latihanDao.delete(history)
    }

    fun getWeeklyHistory(): Flow<List<LatihanHistory>> {
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        return latihanDao.getHistorySince(sevenDaysAgo)
    }

    val userProfile: Flow<UserProfile?> = userProfileDao.getUserProfile()

    suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.insertOrUpdate(profile)
    }
}