package si.fri.matevzfa.approxhpvmdemo.adaptation

import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper

class NoAdaptation(approxHPVMWrapper: ApproxHPVMWrapper) : AdaptationEngine(approxHPVMWrapper) {

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {
        // Do nothing
    }

    override fun name(): String = "NoAdaptation"

}
