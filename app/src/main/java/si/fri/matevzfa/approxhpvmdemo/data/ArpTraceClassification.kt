package si.fri.matevzfa.approxhpvmdemo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "arp_trace_classification")
data class ArpTraceClassification(
    @PrimaryKey(autoGenerate = true) val uid: Int,
    @ColumnInfo(name = "timestamp") val timestamp: String?,
    @ColumnInfo(name = "run_start") val runStart: String?,
    @ColumnInfo(name = "trace_run_start") val traceRunStart: String?,
    @ColumnInfo(name = "used_config") val usedConfig: Int?,
    @ColumnInfo(name = "argmax") val argMax: Int?,
    @ColumnInfo(name = "argmax_baseline") val argMaxBaseline: Int?,
    @ColumnInfo(name = "confidence_concat") val confidenceConcat: String?,
    @ColumnInfo(name = "confidence_baseline_concat") val confidenceBaselineConcat: String?,
    @ColumnInfo(name = "used_engine") val usedEngine: String?,
    @ColumnInfo(name = "info") val info: String = "",
    @ColumnInfo(name = "word_to_say") val word_to_say : String = ""
) {
    override fun toString(): String =
        "TraceClassification($timestamp, $usedEngine, config=$usedConfig, base=$argMaxBaseline, approx=$argMax, info=$info)"
}
