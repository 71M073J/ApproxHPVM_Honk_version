package si.fri.matevzfa.approxhpvmdemo

abstract class AdaptationEngine(protected val mApproxHPVMWrapper: ApproxHPVMWrapper) {
    abstract fun actUpon(softMax: FloatArray, argMax: Int);
}
