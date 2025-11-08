package com.example.repscountingapp.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LatihanDao {

    @Insert
    suspend fun insert(history: LatihanHistory)

    @Query("SELECT * FROM latihan_history ORDER BY tanggal DESC")
    fun getAllHistory(): Flow<List<LatihanHistory>>

    @Query("SELECT * FROM latihan_history WHERE tanggal BETWEEN :startTimestamp AND :endTimestamp ORDER BY tanggal DESC")
    fun getHistoryBetweenDates(startTimestamp: Long, endTimestamp: Long): Flow<List<LatihanHistory>>

    @Query("DELETE FROM latihan_history WHERE tanggal < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Delete
    suspend fun delete(history: LatihanHistory)
}