package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    version = 7,
    entities = [Classification::class, TraceClassification::class, SignalImage::class],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classificationDao(): ClassificationDao
    abstract fun traceClassificationDao(): TraceClassificationDao
    abstract fun signalImageDao(): SignalImageDao
}
