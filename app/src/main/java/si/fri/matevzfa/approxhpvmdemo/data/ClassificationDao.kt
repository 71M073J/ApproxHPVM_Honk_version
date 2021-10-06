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

    @Query("SELECT * FROM classification WHERE run_start = :runStart ORDER BY timestamp ASC")
    fun loadAllByRunStart(runStart: String?): List<Classification>

    @Query("SELECT DISTINCT run_start FROM classification ORDER BY timestamp ASC")
    fun getRunStarts(): List<String>

    @Query(
        """
        SELECT 
            c.run_start as 'runStart', 
            CASE WHEN tc.run_start IS NULL THEN 0 ELSE 1 END  as 'wasClassified' 
        FROM (SELECT DISTINCT run_start FROM classification) as c
        LEFT JOIN (SELECT DISTINCT run_start FROM trace_classification) as tc ON (c.run_start = tc.run_start)
        """
    )
    fun getRunStartsWithClassifiedFlag(): List<ClassificationInfo>

    @Insert
    fun insertAll(vararg users: Classification)

    @Delete
    fun delete(user: Classification)
}
