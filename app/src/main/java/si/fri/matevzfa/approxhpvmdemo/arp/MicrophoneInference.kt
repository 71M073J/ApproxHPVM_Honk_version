package si.fri.matevzfa.approxhpvmdemo.arp

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
    //val classificationDao: ArpClassificationDao,
    val traceClassificationDao: ArpTraceClassificationDao,
    val signalImageDao: SignalImageDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val lastData = signalImageDao.getLast()
        if (lastData.img == null){
            return Result.failure()
        }
        Log.e(TAG, lastData.toString())
        val signalImage = lastData.img.split(",").map { it.toFloat() }.toFloatArray()
        val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")

        // This is where we set the approximation levels
        val noEngine = NoAdaptation(approxHPVMWrapper)
        val confidenceEngine = HARConfidenceAdaptation(approxHPVMWrapper, loadConfigurations(applicationContext))
        Log.w(TAG, "WE GUCCI")
        Log.w(TAG, signalImage.toString())

        doInference(noEngine, signalImage, labelNames, lastData)
        doInference(confidenceEngine, signalImage, labelNames, lastData)

        return Result.success()
    }

    private fun doInference (engine : AdaptationEngine , micInput: FloatArray,
                             labelNames: List<String>, lastData: SignalImage) {
        val traceRunStart = dateTimeFormatter.format(Instant.now())
        val (softm, argm) = engine.useFor { classify(micInput) }

        Log.e(TAG, argm.toString() + "(${labelNames[argm]})")
        Log.e(TAG, softm.joinToString(","))

        engine.actUpon(softm, argm)
        val timestamp = dateTimeFormatter.format(Instant.now())

        addToTraceClassification(traceRunStart, argm, softm, engine, lastData, timestamp)
    }

    private fun addToTraceClassification(traceRunStart : String?, argm : Int?,
                                         softm: FloatArray, adaptEngine : AdaptationEngine,
                                         signalImage: SignalImage, timestamp: String) {
        val tc = ArpTraceClassification(
            uid = 0,
            timestamp = timestamp,
            runStart = null,
            traceRunStart = traceRunStart,
            usedConfig = adaptEngine.configuration(),
            argMax = argm,
            confidenceConcat = softm.joinToString(","),
            argMaxBaseline = 0,
            confidenceBaselineConcat = "",
            usedEngine = adaptEngine.name(),
            word_to_say = signalImage.wordToSay,
            info = "no info",
        )

        traceClassificationDao.insertAll(tc)
    }

    private fun classify(signalImage: FloatArray) : Pair<FloatArray, Int> {
        val softMax = FloatArray(12)
        Log.e(TAG, "Classify in microphone inference")
        approxHPVMWrapper.hpvmInference(signalImage, softMax)
        val argMax = softMax.indices.maxByOrNull { softMax[it] } ?: -1
        return Pair(softMax, argMax)
    }

    companion object {
        const val TAG = "microphoneInference"
        const val CHANNEL_ID = "microphoneInference"
        const val COMPLETED_ID = 5050124

        const val RUN_START = "RUN_START"

        fun loadConfigurations(ctx: Context): List<Configuration> =
            ctx.assets.open("models/honk/conf_honk_best.txt").reader()
                .readLines()
                .filter { it.startsWith("conf") }
                .map { it.split(" ") }
                .map { s ->
                    Log.i(TAG, "$s")
                    var idx = 0
                    val id = s[idx++].replace("conf", "").toInt()

                    // speedup, energy, accuracy
                    //idx += 3

                    Configuration(
                        id = id,
                        qosLoss = s[idx++].toFloat(),
                        overallConfidence = Configuration.ConfidenceInfo(
                            s[idx++].toFloat(),
                            s[idx++].toFloat(),
                            s[idx++].toFloat()
                        ),
                        perClassConfidence = (idx..(s.size - 3) step 3).map { i ->
                            Configuration.ConfidenceInfo(s[i].toFloat(), s[i + 1].toFloat(), s[i + 2].toFloat())
                        },
                    )
                }
    }
}
