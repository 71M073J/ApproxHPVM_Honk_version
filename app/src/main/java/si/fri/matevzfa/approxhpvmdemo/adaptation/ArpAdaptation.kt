package si.fri.matevzfa.approxhpvmdemo.adaptation

import android.util.Log
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper

class ArpAdaptation(approxHPVMWrapper: ApproxHPVMWrapper, var currentApproxLevel: Int = 0) :
    AdaptationEngine(approxHPVMWrapper)  {

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {
        Log.i(TAG, "ActUpon in ArpAdaptation")
        mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(currentApproxLevel)
    }

    fun changeApproxLevel(newApproxLevel: Int) {
        Log.i(TAG, "Change approximate level to: $newApproxLevel")
        currentApproxLevel = newApproxLevel
    }

    override fun name(): String = "ArpAdaptation_$currentApproxLevel"

    companion object {
        private const val TAG: String = "ArpAdaptation"
    }
}