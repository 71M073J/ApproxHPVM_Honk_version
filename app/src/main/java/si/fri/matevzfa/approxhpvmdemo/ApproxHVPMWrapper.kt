package si.fri.matevzfa.approxhpvmdemo

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class ApproxHVPMWrapper : LifecycleObserver {
    companion object {
        const val TAG = "ApproxHPVMWrapper"
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun init() {
        Log.i(TAG, "Initializing ApproxHPVMWrapper")
        Log.w(TAG, "TODO hpvmInit");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun cleanup() {
        Log.i(TAG, "Cleaning up ApproxHPVMWrapper")
        Log.w(TAG, "TODO hpvmCleanup");
    }

    /**
     * Initializes the HPVM runtime.
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     *
     * At the end of application lifetime, [hpvmCleanup] must be called.
     */
    @Suppress("KotlinJniMissingFunction")
    private external fun hpvmInit();

    /**
     * Cleans up the HPVM runtime.
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     */
    @Suppress("KotlinJniMissingFunction")
    private external fun hpvmCleanup();

    /**
     * Passes data for inference to the ApproxHPVM runtime.
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     *
     * @return SoftMax layer output
     */
    @Suppress("KotlinJniMissingFunction")
    external fun hpvmInference(input: FloatArray): FloatArray;
}
