package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Classification::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classificationDao(): ClassificationDao
}
