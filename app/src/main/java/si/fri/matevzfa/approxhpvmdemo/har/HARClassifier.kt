package si.fri.matevzfa.approxhpvmdemo.har

import android.os.Handler
import android.os.HandlerThread

class HARClassifier(
    val mApproxHVPMWrapper: ApproxHPVMWrapper,
    handlerThread: HandlerThread,
    val callback: (FloatArray) -> Unit = {}
) {
    private val numClasses = 12
    private val handler = Handler(handlerThread.looper)

    fun classify(signalImage: FloatArray) {
        handler.post {
            val output = FloatArray(numClasses)
            mApproxHVPMWrapper.hpvmInference(signalImage, output)
            callback(output)
        }
    }
}

class HARClassifierWithBaseline(
    val mApproxHVPMWrapper: ApproxHPVMWrapper,
    handlerThread: HandlerThread,
    val callback: (FloatArray, FloatArray, FloatArray) -> Unit = { softMax, softMaxBasline, signalImage -> }
) {
    private val numClasses = 12
    private val handler = Handler(handlerThread.looper)

    fun classify(signalImage: FloatArray) {
        handler.post {
            val output = FloatArray(numClasses)
            val outputBaseline = FloatArray(numClasses)
            mApproxHVPMWrapper.hpvmInference(signalImage, output)
            mApproxHVPMWrapper.hpvmInferenceWithConfig(signalImage, outputBaseline, 0)
            callback(output, outputBaseline, signalImage)
        }
    }
}
