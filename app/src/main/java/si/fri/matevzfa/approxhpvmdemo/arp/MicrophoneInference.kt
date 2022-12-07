package si.fri.matevzfa.approxhpvmdemo.arp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.activityName
import si.fri.matevzfa.approxhpvmdemo.adaptation.*
import si.fri.matevzfa.approxhpvmdemo.data.*
import si.fri.matevzfa.approxhpvmdemo.dateTimeFormatter
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.har.Configuration
import java.time.Instant

@HiltWorker
class MicrophoneInference @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    //val classificationDao: ClassificationDao,
    val traceClassificationDao: TraceClassificationDao,
    val signalImageDao: SignalImageDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val signalImage = signalImageDao.getLast().split(",").map { it.toFloat() }.toFloatArray()
        val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")
        //showNotification("Trace classification started", "You will be notified when it completes.")
        //Nočemo tega tukaj, če že dej to v onclicklistener za gumb (ta funkcija se kliče vsako sekundo)
        val traceRunStart = dateTimeFormatter.format(Instant.now())
        val noEngine = NoAdaptation(approxHPVMWrapper)

        Log.w(TAG, "WE GUCCI")
        Log.w(TAG, signalImage.toString())
        //noEngine.useFor{}
        val (softm, argm) = classify(signalImage)
        Log.e(TAG, argm.toString() + "(${labelNames[argm]})")
        Log.e(TAG, softm.joinToString(","))
        //TODO make a new DB for this, so i can also clear it on work start, so there is always just
        // one entry, unless we want history
        val tc = TraceClassification(
            uid = 0,
            timestamp = null,
            runStart = null,
            traceRunStart = traceRunStart,
            usedConfig = 0,
            argMax = argm,
            confidenceConcat = softm.joinToString(","),
            argMaxBaseline = 0,
            confidenceBaselineConcat = "",
            usedEngine = "no engine",
            info = "no info",
        )

        traceClassificationDao.insertAll(tc)
        /*val engines = listOf(
            NaiveAdaptation(approxHPVMWrapper, 1),
            NaiveAdaptation(approxHPVMWrapper, 2),
            StateAdaptation(approxHPVMWrapper, 1),
            StateAdaptation(approxHPVMWrapper, 2),
            KalmanAdaptation(approxHPVMWrapper, 1),
            KalmanAdaptation(approxHPVMWrapper, 2),
            /*HARConfidenceAdaptation(
                approxHPVMWrapper,
                Configuration.loadConfigurations(applicationContext),
                1,
            ),
            HARConfidenceAdaptation(
                approxHPVMWrapper,
                Configuration.loadConfigurations(applicationContext),
                2,
            )*/
        )*/

        /*
        for ((i, c: Classification) in classifications.withIndex()) {
            c.signalImage ?: continue

            val signalImage = c.signalImage.split(",").map { it.toFloat() }.toFloatArray()

            val (softMaxBaseline, argMaxBaseline) = noEngine.useFor { classify(signalImage) }

            for (engine in engines) {
                Log.i(
                    TAG,
                    "Classifying input ${i + 1}/${classifications.size} with ${engine.name()}"
                )
                if (isStopped) {
                    traceClassificationDao.deleteAllByTraceRunStart(traceRunStart!!)
                    showNotification(
                        "Trace classification cancelled",
                        "Trace classification for $forRunStart was cancelled."
                    )
                    return Result.success()
                }

                val usedConfig = engine.configuration()
                val (softMax, argMax) = engine.useFor { classify(signalImage) }
                engine.actUpon(softMax, argMax)

                val tc = TraceClassification(
                    uid = 0,
                    timestamp = c.timestamp,
                    runStart = c.runStart,
                    traceRunStart = traceRunStart,
                    usedConfig = usedConfig,
                    argMax = argMax,
                    confidenceConcat = softMax.joinToString(","),
                    argMaxBaseline = argMaxBaseline,
                    confidenceBaselineConcat = softMaxBaseline.joinToString(","),
                    usedEngine = engine.name(),
                    info = c.info,
                )

                traceClassificationDao.insertAll(tc)
            }
        }

        showNotification(
            "Trace classification completed",
            "Classification for $forRunStart completed."
        )*/

        return Result.success()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun showNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Trace Classification Completion"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            // Register the channel with the system
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_info_24)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(COMPLETED_ID, builder.build())
        }
    }

    private fun classify(signalImage: FloatArray): Pair<FloatArray, Int> {
        val softMax = FloatArray(12)
        Log.e(TAG, "Am i here")
        approxHPVMWrapper.hpvmInference(signalImage, softMax)
        val argMax = softMax.indices.maxByOrNull { softMax[it] } ?: -1
        return Pair(softMax, argMax)
    }

    companion object {
        const val TAG = "microphoneInference"
        const val CHANNEL_ID = "microphoneInference"
        const val COMPLETED_ID = 5050124

        const val RUN_START = "RUN_START"
    }
}
