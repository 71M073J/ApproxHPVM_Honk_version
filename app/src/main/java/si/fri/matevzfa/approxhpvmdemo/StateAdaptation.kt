package si.fri.matevzfa.approxhpvmdemo

import android.util.Log


class StateAdaptation(
    approxHPVMWrapper: ApproxHPVMWrapper,
    val nTracked: Int,
    val wrongIfBelow: Int
) :

    AdaptationEngine(approxHPVMWrapper) {

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

        val counts = lastNStates.groupingBy { it }.eachCount()
        val majorityClass = counts.maxByOrNull { it.value }!!.key
        val unreliable =
            counts[majorityClass]!! <= wrongIfBelow || majorityClass != lastMajorityClass

        lastMajorityClass = majorityClass

        Log.i(TAG, "counts $counts")
        Log.i(TAG, "majorityClass $majorityClass")
        Log.i(TAG, "unreliable $unreliable")
        Log.i(TAG, "lastTwoEq ${areLastTwoEqual()}")

        approximationVotes = if (unreliable) {
            approximationVotes - 1
        } else {
            approximationVotes + 1
        }.coerceIn(-2, 2)


        Log.i(TAG, "approximationVotes $approximationVotes")

        val action = if (approximationVotes <= -2) {
            // NN misbehaving. Approximate less and reset last N states
            val currentIdx = mApproxHPVMWrapper.hpvmAdaptiveGetConfigIndex()
            mApproxHPVMWrapper.hpvmAdaptiveSetConfigIndex(currentIdx / 2)
            approximationVotes = 0
            ApproximationChange.LESS_2
        } else if (approximationVotes < 0) {
            mApproxHPVMWrapper.hpvmAdaptiveApproximateLess()
            approximationVotes += 1
            ApproximationChange.LESS
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