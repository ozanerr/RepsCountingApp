package com.example.repscountingapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0,
    val name: String = "",
    val age: Int = 0,
    val gender: String = "Male",
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val neck: Double = 0.0,
    val waist: Double = 0.0,
    val hip: Double = 0.0,
    val profilePicturePath: String = ""
)