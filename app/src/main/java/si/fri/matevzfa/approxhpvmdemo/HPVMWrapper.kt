package si.fri.matevzfa.approxhpvmdemo

import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

abstract class HPVMWrapper : LifecycleObserver {



    /**
     * Initializes the HPVM runtime.
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     *
     * At the end of application lifetime, [hpvmCleanup] must be called.
     */
    abstract fun hpvmInit(mgr: AssetManager);

    /**
     * Cleans up the HPVM runtime initialized with [hpvmInit].
     * This method must be implemented by the ApproxHPVM compiler, not by the app programmer.
     */
    abstract fun hpvmCleanup();

    /**
     * Takes an input tensor represented by [input] and returns the result of the final layer of the
     * neural network in [output].
     */
    abstract fun hpvmInference(input: FloatArray, output: FloatArray);
}
