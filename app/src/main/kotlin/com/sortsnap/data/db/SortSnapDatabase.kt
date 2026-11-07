package com.sortsnap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ImageRecord::class,
        CorrectionEntity::class,
        FolderPriorEntity::class,
        ScanStateEntity::class,
        ReviewAnswerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SortSnapDatabase : RoomDatabase() {
    abstract fun images(): ImageDao
    abstract fun corrections(): CorrectionDao
    abstract fun priors(): FolderPriorDao
    abstract fun scanState(): ScanStateDao
    abstract fun reviews(): ReviewDao

    companion object {
        const val NAME = "sortsnap.db"
    }
}
