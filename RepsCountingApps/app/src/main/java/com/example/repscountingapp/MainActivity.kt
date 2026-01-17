package com.example.repscountingapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.repscountingapp.databinding.ActivityMainBinding
import com.example.repscountingapp.fragments.HistoryFragment
import com.example.repscountingapp.fragments.LatihanFragment
import com.example.repscountingapp.fragments.ProfileFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Langsung ke menu latihan saat aplikasi pertama kali nyala
        if (savedInstanceState == null) {
            loadFragment(LatihanFragment())
        }

        // Ini bagian yang ngurusin kalau kamu klik menu di bottom navigation bar.
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_latihan -> {
                    // Kalau menu 'Latihan' diklik, ya kita tampilin halaman Latihan.
                    loadFragment(LatihanFragment())
                    true
                }
                R.id.nav_history -> {
                    // Kalau 'History' diklik, kita ganti ke halaman History.
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    // Fungsi ganti fragment yang lagi tampil di layar.
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

