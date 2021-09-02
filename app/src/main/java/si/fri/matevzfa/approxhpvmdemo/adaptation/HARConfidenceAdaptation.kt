package si.fri.matevzfa.approxhpvmdemo.adaptation

import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.har.Configuration

/**
 * The [HARConfidenceAdaptation] adaptation engine only works for mobilenet_uci-har.
 *
 * Changing the neural network in any way would require using adaptive AMC pipeline to obtain new
 * confidence thresholds.
 */
class HARConfidenceAdaptation(
    approxHPVMWrapper: ApproxHPVMWrapper,
    private val configurations: List<Configuration>,
    private val factor: Int = 1,
) :
    AdaptationEngine(approxHPVMWrapper) {

    private var approximationIncreaseStep = 1

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {

        val confidence = softMax[argMax]

        val confidenceInfo = configurations[configuration()].perClassConfidence[argMax]

        if (confidence < midPoint(confidenceInfo.wrong, confidenceInfo.correct, 0.5F)) {
            approximateLess()
        } else if (confidence > midPoint(confidenceInfo.wrong, confidenceInfo.correct, 0.75F)) {
            approximateMore()
        }
    }

    private fun midPoint(a: Float, b: Float, k: Float) =
        a + k * (b - a)

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

    override fun name(): String = "HARConfidenceAdaptation_x$factor"
}
