package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_image")
data class SignalImage(
    @PrimaryKey(autoGenerate = true) val uid: Int,
    @ColumnInfo(name = "img") val img: String?,
    @ColumnInfo(name = "wordToSay") val wordToSay: String = ""
) {
    override fun toString(): String =
        "SignalImage(ID:$uid, Image: $img)"
}
