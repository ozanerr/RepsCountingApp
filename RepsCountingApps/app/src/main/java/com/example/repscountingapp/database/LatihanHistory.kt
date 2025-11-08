package com.example.repscountingapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "latihan_history")
data class LatihanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val namaLatihan: String,
    val jumlahRepetisi: Int,
    val tanggal: Long, // Paling gampang simpan waktu sebagai angka (timestamp)
    val fotoPath: String // simpan 'alamat' fotonya di HP, bukan fotonya
)
