package si.fri.matevzfa.approxhpvmdemo.trace

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassificationDao
import si.fri.matevzfa.approxhpvmdemo.dateTimeFormatter
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.har.HARSignalProcessor
import java.time.Instant

@HiltWorker
class DataImportWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    val classificationDao: ClassificationDao,
    val traceClassificationDao: TraceClassificationDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : CoroutineWorker(context, workerParams) {

    private val signalProcessor = HARSignalProcessor(HARSignalProcessor.AxisOrder.DEFAULT)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        signalProcessor.reset()

        val filePath = "raw_labels.txt"

        val result = kotlin.runCatching {
            applicationContext.assets.open(filePath).bufferedReader().lines()
        }

        if (result.isFailure) {
            Log.e(TAG, "An error occurred", result.exceptionOrNull())
            return@withContext Result.failure()
        }

        val iter = result.getOrNull()!!.iterator()

        // Skip header
        iter.next()

        val userIdRunStartMap = mutableMapOf<Int, Instant>()

        for (line in iter) {
            //    0     1     2     3     4     5     6            7            8       9
            // time acc_x acc_y acc_z ang_x ang_y ang_z activity_ind activity_des user_id
            val values = line.split(" ")

            val userId = values[9].toInt()

            val timestamp = Instant.ofEpochMilli((values[0].toFloat() * 1000).toLong())
            val runStart = userIdRunStartMap.getOrPut(userId, { timestamp })

            val accData = HARSignalProcessor.SensorData(
                values[1].toFloat(),
                values[2].toFloat(),
                values[3].toFloat()
            )
            signalProcessor.addAccData(accData)

            val gyrData = HARSignalProcessor.SensorData(
                values[4].toFloat(),
                values[5].toFloat(),
                values[6].toFloat()
            )
            signalProcessor.addGyrData(gyrData)

            if (signalProcessor.isFull()) {
                processSignalImage(runStart, timestamp)
                signalProcessor.reset()
            }
        }

        return@withContext Result.success()
    }

    private fun processSignalImage(runStart: Instant, timestamp: Instant) {
        val signalImage = signalProcessor.signalImage()

        Classification(
            uid = 0,
            timestamp = dateTimeFormatter.format(timestamp)!!,
            runStart = dateTimeFormatter.format(runStart)!!,
            usedConfig = null,
            argMax = null,
            argMaxBaseline = null,
            confidenceConcat = null,
            confidenceBaselineConcat = null,
            signalImage = signalImage.joinToString(","),
            usedEngine = null,
        ).also {
            classificationDao.insertAll(it)
        }

    }

    companion object {
        const val TAG = "DataImportWork"
    }
}
