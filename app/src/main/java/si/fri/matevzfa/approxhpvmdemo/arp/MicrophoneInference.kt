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
        val signalImageSplit = signalImageDao.getLast().split("-")
        val signalImage = signalImageSplit[0].split(",").map { it.toFloat() }.toFloatArray()
        val approxLevel = signalImageSplit[1].toInt()
        val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")
        val traceRunStart = dateTimeFormatter.format(Instant.now())

        // This is where we set the approximation levels
        val arpEngine = ArpAdaptation(approxHPVMWrapper, approxLevel)

        Log.w(TAG, "WE GUCCI")
        Log.w(TAG, signalImage.toString())
        Log.i(TAG, "Using approximation level: ${arpEngine.currentApproxLevel}")
        val (softm, argm) = arpEngine.useFor { classify(signalImage) }

        Log.e(TAG, argm.toString() + "(${labelNames[argm]})")
        Log.e(TAG, softm.joinToString(","))

        arpEngine.actUpon(softm, argm)
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
            usedEngine = arpEngine.name(),
            info = "no info",
        )

        traceClassificationDao.insertAll(tc)

        return Result.success()
    }

    private fun classify(signalImage: FloatArray) : Pair<FloatArray, Int> {
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
