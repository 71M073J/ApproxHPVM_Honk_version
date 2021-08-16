package si.fri.matevzfa.approxhpvmdemo

class StateAdaptation(
    approxHPVMWrapper: ApproxHPVMWrapper,
    val nTracked: Int,
    val wrongIfBelow: Int
) :
    AdaptationEngine(approxHPVMWrapper) {

    var lastNStates = mutableListOf<Int>()

    var approximationVotes = 0


    override fun actUpon(softMax: FloatArray, argMax: Int) {
        addState(argMax)

        val counts = lastNStates.groupingBy { it }.eachCount()

        var unreliable = false
        for ((_, c) in counts) {
            if (c <= wrongIfBelow) {
                unreliable = true
                break
            }
        }

        val voteLimit = 2

        approximationVotes = if (unreliable) {
            approximationVotes - 1
        } else {
            approximationVotes + 1
        }.coerceIn(-voteLimit, voteLimit)

        if (approximationVotes <= -voteLimit) {
            mApproxHPVMWrapper.hpvmAdaptiveApproximateLess()
        } else if (approximationVotes >= voteLimit) {
            mApproxHPVMWrapper.hpvmAdaptiveApproximateMore()
        }
    }

    fun addState(state: Int) {
        while (lastNStates.size >= nTracked) {
            lastNStates.removeAt(0)
        }

        lastNStates.add(state)
    }

}