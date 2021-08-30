package si.fri.matevzfa.approxhpvmdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HARClassificationLogger(val startedAt: Instant) :
    BroadcastReceiver() {

    val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    override fun onReceive(context: Context?, intent: Intent?) {

        val argMaxBaseline = intent?.getIntExtra(ARGMAX_BASELINE, -1)
        val argMax = intent?.getIntExtra(ARGMAX, -1)
        val usedConf = intent?.getIntExtra(USED_CONF, -1)
        val confidenceConcat = intent?.getFloatArrayExtra(CONFIDENCE)?.joinToString(",")
        val confidenceBaselineConcat =
            intent?.getFloatArrayExtra(CONFIDENCE_BASELINE)?.joinToString(",")

        val db = Room.databaseBuilder(
            context!!.applicationContext,
            AppDatabase::class.java, "classification-log"
        ).allowMainThreadQueries().build()

        val classification =
            Classification(
                uid = 0,
                timestamp = dateTimeFormatter.format(Instant.now())!!,
                runStart = dateTimeFormatter.format(startedAt)!!,
                usedConfig = usedConf!!,
                argMax = argMax!!,
                argMaxBaseline = argMaxBaseline!!,
                confidenceConcat = confidenceConcat ?: "",
                confidenceBaselineConcat = confidenceBaselineConcat ?: "",
            )

        Log.i(TAG, "Adding $classification")

        db.classificationDao().insertAll(classification)

        db.close()
    }


    @Database(entities = arrayOf(Classification::class), version = 1)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun classificationDao(): UserDao
    }


    @Entity
    data class Classification(
        @PrimaryKey(autoGenerate = true) val uid: Int,
        @ColumnInfo(name = "timestamp") val timestamp: String?,
        @ColumnInfo(name = "run_start") val runStart: String?,
        @ColumnInfo(name = "used_config") val usedConfig: Int?,
        @ColumnInfo(name = "argmax") val argMax: Int?,
        @ColumnInfo(name = "argmax_baseline") val argMaxBaseline: Int?,
        @ColumnInfo(name = "confidence_concat") val confidenceConcat: String?,
        @ColumnInfo(name = "confidence_baseline_concat") val confidenceBaselineConcat: String?,
    )

    @Dao
    interface UserDao {
        @Query("SELECT * FROM classification")
        fun getAll(): List<Classification>

        @Query("SELECT * FROM classification WHERE uid IN (:userIds)")
        fun loadAllByIds(userIds: IntArray): List<Classification>

        @Insert
        fun insertAll(vararg users: Classification)

        @Delete
        fun delete(user: Classification)
    }


    companion object {
        const val TAG = "HARClassificationLogger"

        const val ACTION = "HARClassificationLogger.BROADCAST"
        const val USED_CONF = "USED_CONF"
        const val ARGMAX = "ARGMAX"
        const val ARGMAX_BASELINE = "ARGMAX_BASELINE"
        const val CONFIDENCE = "CONFIDENCE"
        const val CONFIDENCE_BASELINE = "CONFIDENCE_BASELINE"
    }
}
