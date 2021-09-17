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

        val collection = mutableListOf<Reading>()

        for (line in iter) {
            val next = Reading.fromRecord(line)

            if (collection.isEmpty()) {
                collection.add(next)
                continue
            }

            val current = collection.first()

            if (current.user == next.user && (next.time.toEpochMilli() - current.time.toEpochMilli()) < 1000) {
                collection.add(next)
            } else {
                // Save collected readings
                store(collection)

                // Reset collectio
                collection.clear()

                // Reinitialize a new series
                collection.add(next)
            }
        }

        return@withContext Result.success()
    }

    private fun store(readings: List<Reading>) {
        signalProcessor.reset()

        val sortedReadings = readings.sortedBy { it.time }
        val runStart = sortedReadings.first().time

        for (reading in sortedReadings) {
            val accData = HARSignalProcessor.SensorData(reading.accX, reading.accY, reading.accZ)
            val gyrData = HARSignalProcessor.SensorData(reading.gyrX, reading.gyrY, reading.gyrZ)

            signalProcessor.addAccData(accData)
            signalProcessor.addGyrData(gyrData)

            if (signalProcessor.isFull()) {

                val signalImage = signalProcessor.signalImage()

                val classification = Classification(
                    uid = 0,
                    timestamp = dateTimeFormatter.format(reading.time)!!,
                    runStart = dateTimeFormatter.format(runStart)!!,
                    usedConfig = null,
                    argMax = null,
                    argMaxBaseline = null,
                    confidenceConcat = null,
                    confidenceBaselineConcat = null,
                    signalImage = signalImage.joinToString(","),
                    usedEngine = null,
                )

                classificationDao.insertAll(classification)

                signalProcessor.reset()
            }
        }
    }

    private data class Reading(
        val time: Instant,
        val accX: Float, val accY: Float, val accZ: Float,
        val gyrX: Float, val gyrY: Float, val gyrZ: Float,
        val user: Int,
    ) {
        companion object {
            fun fromRecord(record: String): Reading {
                //    0     1     2     3     4     5     6            7            8       9
                // time acc_x acc_y acc_z ang_x ang_y ang_z activity_ind activity_des user_id
                val values = record.split(" ")

                val timestamp = Instant.ofEpochMilli((values[0].toFloat() * 1000).toLong())!!
                val userId = values[9].toInt()

                return Reading(
                    timestamp,
                    values[1].toFloat(), values[2].toFloat(), values[3].toFloat(),
                    values[4].toFloat(), values[5].toFloat(), values[6].toFloat(),
                    userId,
                )
            }
        }
    }

    companion object {
        const val TAG = "DataImportWork"
    }
}
