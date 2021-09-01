package si.fri.matevzfa.approxhpvmdemo.data

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TraceClassificationDao {
    @Query("SELECT * FROM trace_classification")
    fun getAll(): List<TraceClassification>

    @Query("SELECT * FROM trace_classification WHERE run_start = :runStart ORDER BY timestamp ASC")
    fun loadAllByRunStart(runStart: String?): DataSource.Factory<Int, TraceClassification>

    @Query("SELECT DISTINCT run_start FROM trace_classification ORDER BY timestamp ASC")
    fun getRunStarts(): List<String>

    @Insert
    fun insertAll(vararg users: TraceClassification)

    @Delete
    fun delete(user: TraceClassification)
}
