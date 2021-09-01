package si.fri.matevzfa.approxhpvmdemo.trace

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.paging.PagedList
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import si.fri.matevzfa.approxhpvmdemo.adaptation.KalmanAdaptation
import si.fri.matevzfa.approxhpvmdemo.adaptation.NoAdaptation
import si.fri.matevzfa.approxhpvmdemo.adaptation.StateAdaptation
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassification
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassificationDao
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.har.HARClassificationLogger
import java.time.Instant

@HiltWorker
class TraceClassificationWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    val classificationDao: ClassificationDao,
    val traceClassificationDao: TraceClassificationDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : Worker(context, workerParams) {

    private val forRunStart = workerParams.inputData.getString("RUN_START")

    override fun doWork(): Result {
        val ds = classificationDao.loadAllByRunStart(forRunStart)
        val pl = PagedList.Builder(ds.create(), 10).build()

        val traceRunStart = Instant.now()

        val noEngine = NoAdaptation(approxHPVMWrapper)
        val engines = mutableListOf(
            StateAdaptation(approxHPVMWrapper),
            KalmanAdaptation(approxHPVMWrapper),
        )

        val traceClassifications = mutableListOf<TraceClassification>()

        for ((i, c: Classification) in pl.withIndex()) {
            c.signalImage ?: continue

            val signalImage = c.signalImage.split(",").map { it.toFloat() }.toFloatArray()

            val (softMaxBaseline, argMaxBaseline) = noEngine.useFor { classify(signalImage) }

            for (engine in engines) {
                Log.i(TAG, "Classifying input $i with ${engine.name()}")

                val usedConfig = engine.configuration()
                val (softMax, argMax) = engine.useFor { classify(signalImage) }
                engine.actUpon(softMax, argMax)

                val tc = TraceClassification(
                    uid = 0,
                    timestamp = c.timestamp,
                    runStart = c.runStart,
                    traceRunStart = HARClassificationLogger.dateTimeFormatter.format(traceRunStart),
                    usedConfig = usedConfig,
                    argMax = argMax,
                    confidenceConcat = softMax.joinToString(","),
                    argMaxBaseline = argMaxBaseline,
                    confidenceBaselineConcat = softMaxBaseline.joinToString(","),
                    usedEngine = engine.name(),
                )

                traceClassifications.add(tc)
            }
        }

        traceClassificationDao.insertAll(*traceClassifications.toTypedArray())

        return Result.success()
    }

    private fun classify(signalImage: FloatArray): Pair<FloatArray, Int> {
        val softMax = FloatArray(6)
        approxHPVMWrapper.hpvmInference(signalImage, softMax)
        val argMax = softMax.indices.maxByOrNull { softMax[it] } ?: -1
        return Pair(softMax, argMax)
    }

    companion object {
        const val TAG = "TraceClassificationWork"

        const val RUN_START = "RUN_START"
    }
}
