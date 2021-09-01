package si.fri.matevzfa.approxhpvmdemo.adaptation

import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper

abstract class AdaptationEngine(protected val mApproxHPVMWrapper: ApproxHPVMWrapper) {

    private var configuration = 0

    fun useFor(block: () -> Unit) {
        mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(configuration)
        block()
        configuration = mApproxHPVMWrapper.hpvmAdaptiveGetConfigIndex()
    }

    fun actUpon(softMax: FloatArray, argMax: Int) {
        // Set to this adaptation engine's configuration index
        mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(configuration)

        // Run adaptation
        actUponImpl(softMax, argMax)

        // Save new configuration index
        configuration = mApproxHPVMWrapper.hpvmAdaptiveGetConfigIndex()
    }

    fun configuration() = configuration

    /**
     * This method must implement adaptation, given a softMax confidence array and the index
     * of predicted class.
     */
    abstract fun actUponImpl(softMax: FloatArray, argMax: Int)

    /**
     * Returns engine name
     */
    abstract fun name(): String
}
