package si.fri.matevzfa.approxhpvmdemo.data


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SignalImageDao {
    @Query("SELECT * FROM signal_image")
    fun getAll(): List<SignalImage>

    @Query("SELECT img FROM signal_image WHERE uid = :ID")
    fun getByID(ID: Int?): String

    @Query("DELETE FROM signal_image WHERE uid = :ID")
    fun deleteByID(ID: Int?)

    @Query("SELECT img FROM signal_image ORDER BY uid DESC LIMIT 1")
    fun getLast(): String

    @Query("DELETE FROM signal_image")
    fun deleteAll()

    @Insert
    fun insertAll(vararg users: SignalImage)

    @Delete
    fun delete(user: SignalImage)
}
