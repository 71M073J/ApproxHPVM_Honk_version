package si.fri.matevzfa.approxhpvmdemo.adaptation

import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper

class NaiveAdaptation(approxHPVMWrapper: ApproxHPVMWrapper, private val factor: Int = 1) :
    AdaptationEngine(approxHPVMWrapper) {

    private var lastArgMax = -1
    private var approximationIncreaseStep = 1

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {

        if (lastArgMax == -1) {
            lastArgMax = argMax
            return
        }

        if (argMax != lastArgMax) {
            approximateLess()
        } else {
            approximateMore()
        }
    }

    private fun approximateLess() {
        val current = mApproxHPVMWrapper.hpvmAdaptiveGetConfigIndex()
        mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(current / 2)

        approximationIncreaseStep = 1
    }

    private fun approximateMore() {
        val current = mApproxHPVMWrapper.hpvmAdaptiveGetConfigIndex()
        mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(current + approximationIncreaseStep)

        approximationIncreaseStep *= factor
    }

    override fun name(): String = "NaiveAdaptation_x$factor"
}
