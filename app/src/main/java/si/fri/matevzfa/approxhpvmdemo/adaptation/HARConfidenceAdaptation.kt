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
    private val configurations: List<Configuration>
) :
    AdaptationEngine(approxHPVMWrapper) {

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {

    }

    override fun name(): String = "HARConfidenceAdaptation"
}
