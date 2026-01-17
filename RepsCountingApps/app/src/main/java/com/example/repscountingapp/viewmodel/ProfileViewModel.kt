package com.example.repscountingapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.repscountingapp.database.LatihanHistory
import com.example.repscountingapp.database.LatihanRepository
import com.example.repscountingapp.database.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: LatihanRepository
) : ViewModel() {

    val userProfile: LiveData<UserProfile?> = repository.userProfile.asLiveData()
    val weeklyHistory: LiveData<List<LatihanHistory>> = repository.getWeeklyHistory().asLiveData()

    fun saveProfile(
        name: String,
        age: Int,
        gender: String,
        height: Double,
        weight: Double,
        neck: Double,
        waist: Double,
        hip: Double,
        profilePicturePath: String
    ) {
        viewModelScope.launch {
            val profile = UserProfile(
                id = 0,
                name = name,
                age = age,
                gender = gender,
                height = height,
                weight = weight,
                neck = neck,
                waist = waist,
                hip = hip,
                profilePicturePath = profilePicturePath
            )
            repository.saveUserProfile(profile)
        }
    }
}