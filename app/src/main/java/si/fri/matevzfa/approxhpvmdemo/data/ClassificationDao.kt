package si.fri.matevzfa.approxhpvmdemo.data

import androidx.paging.DataSource
import androidx.paging.PagedList
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClassificationDao {
    @Query("SELECT * FROM classification")
    fun getAll(): List<Classification>

    @Query("SELECT * FROM classification WHERE uid IN (:userIds)")
    fun loadAllByIds(userIds: IntArray): List<Classification>

    @Query("SELECT * FROM classification WHERE run_start = :runStart ORDER BY timestamp ASC")
    fun loadAllByRunStart(runStart: String?): DataSource.Factory<Int, Classification>

    @Query("SELECT DISTINCT run_start FROM classification ORDER BY timestamp ASC")
    fun getRunStarts(): List<String>

    @Insert
    fun insertAll(vararg users: Classification)

    @Delete
    fun delete(user: Classification)
}
