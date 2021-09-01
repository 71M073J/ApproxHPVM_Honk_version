package si.fri.matevzfa.approxhpvmdemo

import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun activityName(index: Int) =
    when (index) {
        0 -> "Walking"
        1 -> "Upstairs"
        2 -> "Downstairs"
        3 -> "Sitting"
        4 -> "Standing"
        5 -> "Lying"
        else -> "UNKNOWN_ACTIVITY"
    }

val dateTimeFormatter =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())!!
