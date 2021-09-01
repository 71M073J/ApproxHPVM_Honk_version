package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    version = 6,
    entities = [Classification::class, TraceClassification::class],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classificationDao(): ClassificationDao
    abstract fun traceClassificationDao(): TraceClassificationDao
}
