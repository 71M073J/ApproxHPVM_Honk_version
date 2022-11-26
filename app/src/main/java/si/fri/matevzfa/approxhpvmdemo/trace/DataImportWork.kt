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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassificationDao
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.har.HARSignalProcessor
import java.util.stream.Collectors


@HiltWorker
class DataImportWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    val classificationDao: ClassificationDao,
    val traceClassificationDao: TraceClassificationDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : CoroutineWorker(context, workerParams) {

    private val signalProcessor = HARSignalProcessor(HARSignalProcessor.AxisOrder.DEFAULT)
    private val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val binfilePath = "tune_input.bin"
        val result = kotlin.runCatching {
            applicationContext.assets.open(binfilePath)//.bufferedReader().lines()
            //.collect(Collectors.toList())!!
        }

        if (result.isFailure) {
            Log.e(TAG, "An error occurred", result.exceptionOrNull())
            return@withContext Result.failure()
        }
        //val file = applicationContext.assets.open(binfilePath)
        val file = result.getOrNull()!!
        var bytes : ByteArray = file.readBytes()

        var vals = IntArray(bytes.size / 4)
        for(i in bytes.indices){
            vals[i/4] = vals[i/4] or ((bytes[i].toInt() and 0x000000FF) shl ((i % 4)) * 8)
        }
        bytes = ByteArray(0)
        val binfilePathLabels = "tune_labels.bin"
        val resultLabels = kotlin.runCatching {
            applicationContext.assets.open(binfilePathLabels)//.bufferedReader().lines()
            //.collect(Collectors.toList())!!
        }
        if (resultLabels.isFailure) {
            Log.e(TAG, "An error occurred", result.exceptionOrNull())
            return@withContext Result.failure()
        }
        //val file = applicationContext.assets.open(binfilePath)
        val fileLabels = resultLabels.getOrNull()!!
        var bytesLabels : ByteArray = fileLabels.readBytes()
        var valsLabels = IntArray(bytesLabels.size / 4)
        for(i in bytesLabels.indices){
            valsLabels[i/4] = valsLabels[i/4] or ((bytesLabels[i].toInt() and 0x000000FF) shl ((i % 4)) * 8)
        }
        bytesLabels = ByteArray(0)

        var vals2 = FloatArray(vals.size)
        for(i in vals.indices){
            vals2[i] = java.lang.Float.intBitsToFloat(vals[i])
        }
        vals = IntArray(0)
        //VALS2 PRAVILNO PARSA AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        Log.i("asda", vals2[0].toString() + ", " + vals2[1].toString() +", "+ vals2[2].toString()  + ", "+ vals2.size)
        var signalImage : FloatArray
        val runStart = Clock.System.now()
        val numImages = (vals2.size / (101 * 40)).toInt()
        for (i in 0 until 1){
            signalImage = FloatArray(101 * 40)
            for (j in 0 until (101 * 40)){//TODO REDO the reverse indexing for image
                //signalImage[4039 - ((j % 40) * 101 + (j / 40))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((j % 101) * 40 + (j / 101))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((39 - (j % 40)) * 101 + (j / 40))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((100 - (j % 101)) * 40 + (j / 101))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((j % 40) * 101 + (100 - (j / 40)))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((j % 101) * 40 + (39 - (j / 101)))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((39 - (j % 40)) * 101 + (100 - (j / 40)))] = vals2[i * (40 * 101) + j]
                //signalImage[4039 - ((100 - (j % 101)) * 40 + (39 - (j / 101)))] = vals2[i * (40 * 101) + j]
                //signalImage[((j % 40) * 101 + (j / 40))] = vals2[i * (40 * 101) + j]
                //signalImage[((j % 101) * 40 + (j / 101))] = vals2[i * (40 * 101) + j]
                //signalImage[((39 - (j % 40)) * 101 + (j / 40))] = vals2[i * (40 * 101) + j]
                //signalImage[((100 - (j % 101)) * 40 + (j / 101))] = vals2[i * (40 * 101) + j]
                //signalImage[((j % 40) * 101 + (100 - (j / 40)))] = vals2[i * (40 * 101) + j]
                //signalImage[((j % 101) * 40 + (39 - (j / 101)))] = vals2[i * (40 * 101) + j]
                //signalImage[((39 - (j % 40)) * 101 + (100 - (j / 40)))] = vals2[i * (40 * 101) + j]
                signalImage[((100 - (j % 101)) * 40 + (39 - (j / 101)))] = vals2[i * (40 * 101) + j]
                //TODO AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
            }
            val classification = Classification(
                uid = 0,
                timestamp = Clock.System.now().toString(),
                runStart = runStart.toString(),
                usedConfig = null,
                argMax = null,
                argMaxBaseline = null,
                confidenceConcat = null,
                confidenceBaselineConcat = null,
                signalImage = signalImage.joinToString(","),
                usedEngine = null,
                info = "baseline: ${valsLabels[i]} (${labelNames[valsLabels[i]]})"
            )
            Log.i(
                    TAG,
            "Inserting next command $i, with label ${valsLabels[i]}(${labelNames[valsLabels[i]]})"
            )
            classificationDao.insertAll(classification)
        }
        vals2 = FloatArray(0)
/*
        val filePath = "raw_labels.txt"

        val result = kotlin.runCatching {
            applicationContext.assets.open(filePath)//.bufferedReader().lines()
                //.collect(Collectors.toList())!!
        }

        if (result.isFailure) {
            Log.e(TAG, "An error occurred", result.exceptionOrNull())
            return@withContext Result.failure()
        }

        val readingsByUser = result.getOrNull()!!
            .drop(1)
            .map { line -> Reading.fromRecord(line) }
            .groupBy { it.user }
            .mapValues { it.value.sortedBy { it.time } }

        for ((user, readings) in readingsByUser) {

            val runStart = readings.first().time

            for (chunk in readings.chunked(128)) {
                if (chunk.size < 128) {
                    continue
                }

                if (!chunk.all { it.groudTruth == chunk.first().groudTruth }) {
                    continue
                }

                signalProcessor.reset()

                for (reading in chunk) {
                    signalProcessor.addAccData(
                        HARSignalProcessor.SensorData(
                            reading.accX,
                            reading.accY,
                            reading.accZ
                        )
                    )
                    signalProcessor.addGyrData(
                        HARSignalProcessor.SensorData(
                            reading.gyrX,
                            reading.gyrY,
                            reading.gyrZ
                        )
                    )
                }

                val timestamp = chunk.last().time
                val baseline = chunk.last().groudTruth

                if (!signalProcessor.isFull()) {
                    throw RuntimeException("signalProcessor was not filled")
                }

                val signalImage = signalProcessor.signalImage()
                val classification = Classification(
                    uid = 0,
                    timestamp = timestamp.toString(),
                    runStart = runStart.toString(),
                    usedConfig = null,
                    argMax = null,
                    argMaxBaseline = null,
                    confidenceConcat = null,
                    confidenceBaselineConcat = null,
                    signalImage = signalImage.joinToString(","),
                    usedEngine = null,
                    info = "user=$user baseline=$baseline"
                )

                Log.i(
                    TAG,
                    "Inserting user $user timestamp $timestamp ${chunk.last().time.toEpochMilliseconds() - chunk.first().time.toEpochMilliseconds()}"
                )

                classificationDao.insertAll(classification)
            }
        }
*/

        val a: Instant = Clock.System.now()

        return@withContext Result.success()
    }

    private data class Reading(
        val time: Instant,
        val accX: Float, val accY: Float, val accZ: Float,
        val gyrX: Float, val gyrY: Float, val gyrZ: Float,
        val user: Int,
        val groudTruth: Int,
    ) {
        companion object {
            fun fromRecord(record: String): Reading {
                //    0     1     2     3     4     5     6            7            8       9
                // time acc_x acc_y acc_z ang_x ang_y ang_z activity_ind activity_des user_id
                val values = record.split(" ")

                val timestamp =
                    Instant.fromEpochMilliseconds((values[0].toDouble() * 1000).toLong())
                val userId = values[9].toInt()

                return Reading(
                    timestamp,
                    values[1].toFloat(), values[2].toFloat(), values[3].toFloat(),
                    values[4].toFloat(), values[5].toFloat(), values[6].toFloat(),
                    userId,
                    values[7].toInt()
                )
            }
        }
    }

    companion object {
        const val TAG = "DataImportWork"
    }
}
