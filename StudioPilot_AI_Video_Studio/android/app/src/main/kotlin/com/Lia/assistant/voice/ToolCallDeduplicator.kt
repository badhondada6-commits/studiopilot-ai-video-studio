package com.Lia.assistant.voice

class ToolCallDeduplicator(
    private val maxRememberedIds: Int = DEFAULT_MAX_REMEMBERED_IDS,
) {
    private val seenIds = LinkedHashSet<String>()

    init {
        require(maxRememberedIds > 0) { "maxRememberedIds must be positive" }
    }

    @Synchronized
    fun shouldProcess(callId: String): Boolean {
        if (callId in seenIds) return false
        seenIds.add(callId)
        while (seenIds.size > maxRememberedIds) {
            val oldest = seenIds.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }

    @Synchronized
    fun clear() {
        seenIds.clear()
    }

    private companion object {
        const val DEFAULT_MAX_REMEMBERED_IDS = 256
    }
}
