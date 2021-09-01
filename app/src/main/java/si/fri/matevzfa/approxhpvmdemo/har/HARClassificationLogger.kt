package si.fri.matevzfa.approxhpvmdemo.har

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class HARClassificationLogger(private val startedAt: Instant) :
    BroadcastReceiver() {

    @Inject
    lateinit var dao: ClassificationDao

    override fun onReceive(context: Context?, intent: Intent?) {

        val argMaxBaseline = intent?.getIntExtra(ARGMAX_BASELINE, -1)
        val argMax = intent?.getIntExtra(ARGMAX, -1)
        val usedConf = intent?.getIntExtra(USED_CONF, -1)
        val confidenceConcat = intent?.getFloatArrayExtra(CONFIDENCE)?.joinToString(",")
        val confidenceBaselineConcat =
            intent?.getFloatArrayExtra(CONFIDENCE_BASELINE)?.joinToString(",")
        val signalImage =
            intent?.getFloatArrayExtra(SIGNAL_IMAGE)?.joinToString(",")
        val usedEngine = intent?.getStringExtra(USED_ENGINE)

        val classification =
            Classification(
                uid = 0,
                timestamp = dateTimeFormatter.format(Instant.now())!!,
                runStart = dateTimeFormatter.format(startedAt)!!,
                usedConfig = usedConf!!,
                argMax = argMax!!,
                argMaxBaseline = argMaxBaseline!!,
                confidenceConcat = confidenceConcat ?: "",
                confidenceBaselineConcat = confidenceBaselineConcat ?: "",
                signalImage = signalImage ?: "",
                usedEngine = usedEngine!!,
            )

        Log.i(TAG, "Adding $classification")

        dao.insertAll(classification)
    }

    companion object {
        const val TAG = "HARClassificationLogger"

        const val ACTION = "HARClassificationLogger.BROADCAST"
        const val USED_CONF = "USED_CONF"
        const val ARGMAX = "ARGMAX"
        const val ARGMAX_BASELINE = "ARGMAX_BASELINE"
        const val CONFIDENCE = "CONFIDENCE"
        const val CONFIDENCE_BASELINE = "CONFIDENCE_BASELINE"
        const val SIGNAL_IMAGE = "SIGNAL_IMAGE"
        const val USED_ENGINE = "USED_ENGINE"

        val dateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())!!
    }
}
