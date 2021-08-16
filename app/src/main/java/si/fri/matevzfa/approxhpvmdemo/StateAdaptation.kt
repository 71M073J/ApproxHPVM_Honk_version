package si.fri.matevzfa.approxhpvmdemo

import android.util.Log
import kotlin.math.max
import kotlin.math.min


class StateAdaptation(approxHPVMWrapper: ApproxHPVMWrapper) :
    AdaptationEngine(approxHPVMWrapper) {

    val nTracked: Int = 3

    private enum class ApproximationChange {
        MORE, LESS, LESS_2, NONE
    }

    var lastNStates = mutableListOf<Int>()
    var lastMajorityClass: Int? = null
    var approximationVotes = 0


    override fun actUpon(softMax: FloatArray, argMax: Int) {
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

        val action = if (approximationVotes <= -2) {
            // NN misbehaving. Approximate less and reset last N states
            val currentIdx = mApproxHPVMWrapper.hpvmAdaptiveGetConfigIndex()
            mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(currentIdx / 2)
            approximationVotes = 0
            ApproximationChange.LESS_2
        } else if (approximationVotes >= 2) {
            // Results stable. Approximate more
            mApproxHPVMWrapper.hpvmAdaptiveApproximateMore()
            approximationVotes = 0
            ApproximationChange.MORE
        } else {
            ApproximationChange.NONE
        }

        Log.i(TAG, "action $action")

        Log.i(TAG, "END")
        Log.i(TAG, "--")
    }

    private fun addState(state: Int) {
        while (lastNStates.size >= nTracked) {
            lastNStates.removeAt(0)
        }

        lastNStates.add(state)
    }

    private fun areLastTwoEqual(): Boolean {
        val (a: Int, b: Int) = lastNStates.takeLast(2)
        return a == b
    }

    companion object {
        private val TAG: String = "StateAdaptation"
    }

}