package si.fri.matevzfa.approxhpvmdemo.adaptation

import android.util.Log
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import kotlin.math.max
import kotlin.math.min

class StateAdaptation(approxHPVMWrapper: ApproxHPVMWrapper, val factor: Int = 1) :
    AdaptationEngine(approxHPVMWrapper) {

    private var approximationIncreaseStep = 1

    val nTracked: Int = 3

    private enum class ApproximationChange {
        MORE, LESS, LESS_2, NONE
    }

    var lastNStates = mutableListOf<Int>()
    var approximationVotes = 0

    override fun actUponImpl(softMax: FloatArray, argMax: Int) {
        Log.i(TAG, "--")
        Log.i(TAG, "BEGIN")
        Log.i(TAG, "approximationVotes $approximationVotes")

        addState(argMax)

        if (lastNStates.size < nTracked) {
            return
        }

        val reliable = lastNStates.all { it == lastNStates.last() }

        Log.i(TAG, "lastTwoEq ${areLastTwoEqual()}")

        approximationVotes = if (reliable) {
            max(0, approximationVotes) + 1
        } else {
            min(0, approximationVotes) - 1
        }.coerceIn(-2, 2)


        Log.i(TAG, "approximationVotes $approximationVotes")

        if (approximationVotes <= -2) {
            // NN misbehaving. Approximate less and reset last N states
            approximateLess()
            approximationVotes = 0
        } else if (approximationVotes >= 2) {
            // Results stable. Approximate more
            approximateMore()
            approximationVotes = 0
        }
    }

    private fun addState(state: Int) {
        while (lastNStates.size >= nTracked) {
            lastNStates.removeAt(0)
        }

        lastNStates.add(state)
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

    private fun areLastTwoEqual(): Boolean {
        val (a: Int, b: Int) = lastNStates.takeLast(2)
        return a == b
    }

    override fun name(): String = "StateAdaptation_x$factor"

    companion object {
        private val TAG: String = "StateAdaptation"
    }

}
