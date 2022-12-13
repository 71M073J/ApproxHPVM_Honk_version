package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    version = 8,
    entities = [Classification::class, TraceClassification::class,
        ArpTraceClassification::class, SignalImage::class],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classificationDao(): ClassificationDao
    abstract fun traceClassificationDao(): TraceClassificationDao
    abstract fun arpTraceClassificationDao(): ArpTraceClassificationDao
    abstract fun signalImageDao(): SignalImageDao
}
