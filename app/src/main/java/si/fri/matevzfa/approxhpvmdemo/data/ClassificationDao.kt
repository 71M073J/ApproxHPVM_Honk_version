package si.fri.matevzfa.approxhpvmdemo.data

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

    @Query("SELECT * FROM classification WHERE run_start = :runStart")
    fun loadAllByRunStart(runStart: String?): List<Classification>

    @Query("SELECT DISTINCT run_start FROM classification")
    fun getRunStarts(): List<String>

    @Insert
    fun insertAll(vararg users: Classification)

    @Delete
    fun delete(user: Classification)
}
