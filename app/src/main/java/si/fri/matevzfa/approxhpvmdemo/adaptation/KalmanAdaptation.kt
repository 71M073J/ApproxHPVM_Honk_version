package si.fri.matevzfa.approxhpvmdemo.adaptation

import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper

class KalmanAdaptation(approxHPVMWrapper: ApproxHPVMWrapper, val factor: Int = 2) :
    AdaptationEngine(approxHPVMWrapper) {

    var currentState: Float = 1F
    var currentUncertainty: Float = 100F
    var lastArgMax: Int = -1

    val measurementUncertainty: Float = 1F
    val processNoise: Float = 0.1F

    var approximationIncreaseStep = 1

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {
        val kalmanGain = currentUncertainty / (currentUncertainty + measurementUncertainty)

        val equalAsLast = if (lastArgMax == -1 || argMax == lastArgMax) 1 else 0

        val newState = currentState + kalmanGain * (equalAsLast - currentState)
        val newUncertainty = currentUncertainty + processNoise

        if (newState < 0.5) {
            approximateLess()
        } else if (newState > 0.95) {
            approximateMore()
        }

        lastArgMax = argMax

        currentState = newState
        currentUncertainty = newUncertainty
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

    override fun name(): String = "KalmanAdaptation_x$factor"
}
