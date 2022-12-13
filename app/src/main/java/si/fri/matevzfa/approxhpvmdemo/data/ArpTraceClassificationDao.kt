package si.fri.matevzfa.approxhpvmdemo.data

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ArpTraceClassificationDao {
    @Query("SELECT * FROM arp_trace_classification")
    fun getAll(): List<ArpTraceClassification>

    @Query("SELECT * FROM arp_trace_classification WHERE run_start = :runStart ORDER BY timestamp ASC")
    fun loadAllByRunStart(runStart: String?): DataSource.Factory<Int, ArpTraceClassification>

    @Query("DELETE FROM arp_trace_classification WHERE run_start = :runStart")
    fun deleteAllByRunStart(runStart: String)

    @Query("DELETE FROM arp_trace_classification WHERE trace_run_start = :traceRunStart")
    fun deleteAllByTraceRunStart(traceRunStart: String)

    @Query("SELECT * FROM arp_trace_classification ORDER BY uid DESC LIMIT 1")
    fun getLast(): ArpTraceClassification?

    @Query("SELECT DISTINCT run_start FROM arp_trace_classification ORDER BY timestamp ASC")
    fun getRunStarts(): List<String>

    @Insert
    fun insertAll(vararg users: ArpTraceClassification)

    @Delete
    fun delete(user: ArpTraceClassification)
}
