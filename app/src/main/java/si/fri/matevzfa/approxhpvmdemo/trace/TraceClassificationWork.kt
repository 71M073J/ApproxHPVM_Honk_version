package si.fri.matevzfa.approxhpvmdemo.trace

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import si.fri.matevzfa.approxhpvmdemo.adaptation.*
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassification
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassificationDao
import si.fri.matevzfa.approxhpvmdemo.dateTimeFormatter
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.har.Configuration
import java.time.Instant

@HiltWorker
class TraceClassificationWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    val classificationDao: ClassificationDao,
    val traceClassificationDao: TraceClassificationDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : CoroutineWorker(context, workerParams) {

    private val forRunStart = workerParams.inputData.getString("RUN_START")

    override suspend fun doWork(): Result {
        val classifications = classificationDao.loadAllByRunStart(forRunStart)

        val traceRunStart = Instant.now()

        val noEngine = NoAdaptation(approxHPVMWrapper)
        val engines = listOf(
            NaiveAdaptation(approxHPVMWrapper, 1),
            NaiveAdaptation(approxHPVMWrapper, 2),
            StateAdaptation(approxHPVMWrapper, 1),
            StateAdaptation(approxHPVMWrapper, 2),
            KalmanAdaptation(approxHPVMWrapper, 1),
            KalmanAdaptation(approxHPVMWrapper, 2),
            HARConfidenceAdaptation(
                approxHPVMWrapper,
                Configuration.loadConfigurations(applicationContext),
                1,
            ),
            HARConfidenceAdaptation(
                approxHPVMWrapper,
                Configuration.loadConfigurations(applicationContext),
                2,
            )
        )

        val traceClassifications = mutableListOf<TraceClassification>()

        for ((i, c: Classification) in classifications.withIndex()) {
            c.signalImage ?: continue

            val signalImage = c.signalImage.split(",").map { it.toFloat() }.toFloatArray()

            val (softMaxBaseline, argMaxBaseline) = noEngine.useFor { classify(signalImage) }

            for (engine in engines) {
                Log.i(
                    TAG,
                    "Classifying input ${i + 1}/${classifications.size} with ${engine.name()}"
                )

                val usedConfig = engine.configuration()
                val (softMax, argMax) = engine.useFor { classify(signalImage) }
                engine.actUpon(softMax, argMax)

                val tc = TraceClassification(
                    uid = 0,
                    timestamp = c.timestamp,
                    runStart = c.runStart,
                    traceRunStart = dateTimeFormatter.format(traceRunStart),
                    usedConfig = usedConfig,
                    argMax = argMax,
                    confidenceConcat = softMax.joinToString(","),
                    argMaxBaseline = argMaxBaseline,
                    confidenceBaselineConcat = softMaxBaseline.joinToString(","),
                    usedEngine = engine.name(),
                    info = c.info,
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
