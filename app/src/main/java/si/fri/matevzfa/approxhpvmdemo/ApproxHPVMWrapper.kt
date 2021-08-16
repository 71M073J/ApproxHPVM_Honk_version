package si.fri.matevzfa.approxhpvmdemo

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent


class ApproxHPVMWrapper(context: Context) : LifecycleObserver {
    companion object {
        const val TAG = "ApproxHPVMWrapper"

        init {
            System.loadLibrary("native-lib")
        }
    }

    private val assetManager = context.assets

    /**
     * [hpvmRootInPtr] is used by the native part of HPVM backend. It holds a reference to the
     * memory allocation for HPVM environment (such as weight tensors).
     */
    @Suppress("unused")
    protected var hpvmRootInPtr: Long = 0

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun init() {
        Log.i(TAG, "Initializing ApproxHPVMWrapper")

        hpvmInit(assetManager)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun cleanup() {
        Log.i(TAG, "Cleaning up ApproxHPVMWrapper")

        hpvmCleanup()
    }

    /**
     * Initializes the HPVM runtime.
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     *
     * At the end of application lifetime, [hpvmCleanup] must be called.
     */
    @Suppress("KotlinJniMissingFunction")
    private external fun hpvmInit(mgr: AssetManager);

    /**
     * Cleans up the HPVM runtime initialized with [hpvmInit].
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     */
    @Suppress("KotlinJniMissingFunction")
    private external fun hpvmCleanup();

    /**
     *
     */
    @Suppress("KotlinJniMissingFunction")
    external fun hpvmInference(input: FloatArray, output: FloatArray);

    @Suppress("KotlinJniMissingFunction")
    external fun hpvmAdaptiveApproximateMore();

    @Suppress("KotlinJniMissingFunction")
    external fun hpvmAdaptiveApproximateLess();

    @Suppress("KotlinJniMissingFunction")
    external fun hpvmAdaptiveGetConfigIndex(): Int;

    @Suppress("KotlinJniMissingFunction")
    external fun hpvmAdaptiveSetConfigIndex(idx: Int): Int;

}
