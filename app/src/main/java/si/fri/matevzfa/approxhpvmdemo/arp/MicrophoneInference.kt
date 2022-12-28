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
import kotlin.math.sign

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
        if (lastData == null || lastData.img == null || lastData.approxnum == null){
            return Result.failure()
        }
        Log.e(TAG, lastData.toString())
        val signalImage = lastData.img.split(",").map { it.toFloat() }.toFloatArray()
        val approxLevel = lastData.approxnum
        val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")
        val traceRunStart = dateTimeFormatter.format(Instant.now())

        // This is where we set the approximation levels
        val arpEngine = ArpAdaptation(approxHPVMWrapper, approxLevel)
        val noEngine = NoAdaptation(approxHPVMWrapper)
        // TODO this probably doesn't work
        //  make it work :D
        val confidenceEngine = HARConfidenceAdaptation(approxHPVMWrapper, loadConfigurations(applicationContext))
        Log.w(TAG, "WE GUCCI")
        Log.w(TAG, signalImage.toString())
        Log.i(TAG, "Using approximation level: ${arpEngine.currentApproxLevel}")

        // Approximated classification
        val (softm, argm) = arpEngine.useFor { classify(signalImage) }
        // Non-approximated classification for evaluation
        val (softmBaseline, argmBaseline) = noEngine.useFor { classify(signalImage) }

        val (softmConf, argmConf) = confidenceEngine.useFor { classify(signalImage) }
        
        Log.e(TAG, argm.toString() + "(${labelNames[argm]})")
        Log.e(TAG, softm.joinToString(","))

        // Recalculate configuration indexes
        arpEngine.actUpon(softm, argm)
        confidenceEngine.actUpon(softmConf, argmConf)
        noEngine.actUpon(softmBaseline, argmBaseline)

        // Insert results into database
        addToTraceClassification(traceRunStart, argm, softm, arpEngine, lastData)
        addToTraceClassification(traceRunStart, argmBaseline, softmBaseline, noEngine, lastData)
        addToTraceClassification(traceRunStart, argmConf, softmConf, confidenceEngine, lastData)

        return Result.success()
    }

    private fun addToTraceClassification(traceRunStart : String?, argm : Int?,
                                         softm: FloatArray, adaptEngine : AdaptationEngine,
                                         signalImage: SignalImage) {
        val tc = ArpTraceClassification(
            uid = 0,
            timestamp = null,
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
            ctx.assets.open("models/honk/confs.txt").reader()
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
