package si.fri.matevzfa.approxhpvmdemo

import android.os.Handler
import android.os.HandlerThread

class HARClassifier(
    val mApproxHVPMWrapper: ApproxHPVMWrapper,
    handlerThread: HandlerThread,
    val callback: (FloatArray) -> Unit = {}
) {
    private val numClasses = 6
    private val handler = Handler(handlerThread.looper)

    fun classify(signalImage: FloatArray) {
        handler.post {
            val output = FloatArray(numClasses)
            mApproxHVPMWrapper.hpvmInference(signalImage, output)
            callback(output)
        }
    }
}
