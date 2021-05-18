package si.fri.matevzfa.approxhpvmdemo

import android.content.Context
import android.os.Handler
import android.os.HandlerThread

class HARClassifier(
    val context: Context, handlerThread: HandlerThread,
    private val callback: (FloatArray) -> Unit = {}
) {
    val handler = Handler(handlerThread.looper)

    val numClasses = 6

    /**
     * Classify signal image given by [signalImage] and return SoftMax
     */
    fun classify(signalImage: FloatArray) {
        handler.post {
            // callback(hpvmClassify(signalImage))
            callback(FloatArray(numClasses))
        }
    }

    @Suppress("KotlinJniMissingFunction")
    private external fun hpvmClassify(values: FloatArray): FloatArray
}
