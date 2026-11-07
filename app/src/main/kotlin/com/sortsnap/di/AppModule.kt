package com.sortsnap.di

import android.content.Context
import androidx.room.Room
import com.sortsnap.data.db.CorrectionDao
import com.sortsnap.data.db.FolderPriorDao
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.db.ReviewDao
import com.sortsnap.data.db.ScanStateDao
import com.sortsnap.data.db.SortSnapDatabase
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.data.delete.MediaStoreDeleter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SortSnapDatabase =
        // No destructive fallback: user corrections live here. Add a Migration for every schema bump.
        Room.databaseBuilder(context, SortSnapDatabase::class.java, SortSnapDatabase.NAME)
            .build()

    @Provides fun imageDao(db: SortSnapDatabase): ImageDao = db.images()
    @Provides fun correctionDao(db: SortSnapDatabase): CorrectionDao = db.corrections()
    @Provides fun priorDao(db: SortSnapDatabase): FolderPriorDao = db.priors()
    @Provides fun scanStateDao(db: SortSnapDatabase): ScanStateDao = db.scanState()
    @Provides fun reviewDao(db: SortSnapDatabase): ReviewDao = db.reviews()

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun deleter(impl: MediaStoreDeleter): MediaDeleter
}
