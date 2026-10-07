package com.rentnest.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.repository.*
import com.rentnest.app.domain.SmsGateway
import com.rentnest.app.domain.repository.*
import com.rentnest.app.domain.time.SystemTimeProvider
import com.rentnest.app.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "rentnest.db")
            // Demo data only: a schema change wipes the database and the app reseeds it on launch.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides @Singleton
    fun sessionStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds @Singleton abstract fun catalog(impl: RoomCatalogRepository): CatalogRepository
    @Binds @Singleton abstract fun inventory(impl: RoomInventoryRepository): InventoryRepository
    @Binds @Singleton abstract fun bookings(impl: RoomBookingRepository): BookingRepository
    @Binds @Singleton abstract fun notifications(impl: RoomNotificationRepository): NotificationRepository
    @Binds @Singleton abstract fun session(impl: DataStoreSessionRepository): SessionRepository
    @Binds @Singleton abstract fun time(impl: SystemTimeProvider): TimeProvider
    @Binds @Singleton abstract fun sms(impl: AndroidSmsGateway): SmsGateway
}
