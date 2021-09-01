package si.fri.matevzfa.approxhpvmdemo.trace

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.paging.PagedList
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper

@HiltWorker
class TraceClassificationWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    val classificationDao: ClassificationDao,
    val approxHPVMWrapper: ApproxHPVMWrapper,
) : Worker(context, workerParams) {

    private val forRunStart = workerParams.inputData.getString("RUN_START")

    override fun doWork(): Result {
        val ds = classificationDao.loadAllByRunStart(forRunStart)
        val pl = PagedList.Builder(ds.create(), 10).build()

        for (c: Classification in pl) {
            Log.i(TAG, "${c.timestamp}")
        }

        return Result.success()
    }

    companion object {
        const val TAG = "TraceClassificationWork"

        const val RUN_START = "RUN_START"
    }
}
