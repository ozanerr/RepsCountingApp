package com.example.repscountingapp.di

import android.content.Context
import com.example.repscountingapp.database.AppDatabase
import com.example.repscountingapp.database.LatihanDao
import com.example.repscountingapp.database.LatihanRepository
import com.example.repscountingapp.database.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideLatihanDao(database: AppDatabase): LatihanDao {
        return database.latihanDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideLatihanRepository(
        latihanDao: LatihanDao,
        userProfileDao: UserProfileDao // inject dao baru ke repo
    ): LatihanRepository {
        return LatihanRepository(latihanDao, userProfileDao)
    }
}