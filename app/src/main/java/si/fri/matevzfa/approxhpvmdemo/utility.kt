package si.fri.matevzfa.approxhpvmdemo


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
