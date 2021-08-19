package si.fri.matevzfa.approxhpvmdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.room.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.concurrent.Executors

class HARClassificationLogger(val startedAt: Instant) :
    BroadcastReceiver() {

    val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault())

    override fun onReceive(context: Context?, intent: Intent?) {

        val argMaxBaseline = intent?.getIntExtra(ARGMAX_BASELINE, -1)
        val argMax = intent?.getIntExtra(ARGMAX, -1)
        val usedConf = intent?.getIntExtra(USED_CONF, -1)

        val db = Room.databaseBuilder(
            context!!.applicationContext,
            AppDatabase::class.java, "classification-log"
        ).allowMainThreadQueries().build()

        val classification =
            Classification(
                uid = 0,
                timestamp = dateTimeFormatter.format(Instant.now()),
                runStart = dateTimeFormatter.format(startedAt),
                usedConfig = usedConf,
                argMax = argMax,
                argMaxBaseline = argMaxBaseline
            )

        Log.i(TAG, "Adding $classification")

        db.classificationDao().insertAll(classification)
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
        val TAG = "HARClassificationLogger"

        val ACTION = "HARClassificationLogger.BROADCAST"
        val USED_CONF = "USED_CONF"
        val ARGMAX = "ARGMAX"
        val ARGMAX_BASELINE = "ARGMAX_BASELINE"
    }
}
