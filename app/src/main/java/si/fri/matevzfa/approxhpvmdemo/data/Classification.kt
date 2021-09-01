package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classification")
data class Classification(
    @PrimaryKey(autoGenerate = true) val uid: Int,
    @ColumnInfo(name = "timestamp") val timestamp: String?,
    @ColumnInfo(name = "run_start") val runStart: String?,
    @ColumnInfo(name = "used_config") val usedConfig: Int?,
    @ColumnInfo(name = "argmax") val argMax: Int?,
    @ColumnInfo(name = "argmax_baseline") val argMaxBaseline: Int?,
    @ColumnInfo(name = "confidence_concat") val confidenceConcat: String?,
    @ColumnInfo(name = "confidence_baseline_concat") val confidenceBaselineConcat: String?,
    @ColumnInfo(name = "signal_image") val signalImage: String?,
    @ColumnInfo(name = "used_engine") val usedEngine: String?,
) {
    override fun toString(): String =
        "Classification($timestamp, $usedEngine, config=$usedConfig, base=$argMaxBaseline, approx=$argMax)"
}