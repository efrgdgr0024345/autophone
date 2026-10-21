package com.blackcat.remote

import java.util.IdentityHashMap

internal enum class AutoTaskState(val label: String) {
    PLANNED("Planned"), PREPARING("Explaining"), SENDING("Sending"), WAITING("Waiting for Linux"),
    CHECKING("Checking evidence"), OBSERVED("Evidence checked"), FAILED("Error observed"),
    UNCERTAIN("Outcome uncertain"), REPLACED("Replaced by updated plan"), OUTSIDE_SESSION("Before this session")
}

/** Foreground-only task identities. Helpers never become tasks or turn an attempt into success. */
internal class AutoTaskRecord(val id: Int, val task: SuggestedCommand, initial: AutoTaskState) {
    var state = initial
        internal set
    var finding = ""
        internal set
}

internal class AutoTaskHistory(steps: List<SuggestedCommand>, startIndex: Int) {
    private val byTask = IdentityHashMap<SuggestedCommand, AutoTaskRecord>()
    private val records = mutableListOf<AutoTaskRecord>()
    val tasks: List<AutoTaskRecord> get() = records.toList()
    init { steps.forEachIndexed { i, task -> add(task, if (i < startIndex) AutoTaskState.OUTSIDE_SESSION else AutoTaskState.PLANNED) } }

    private fun add(task: SuggestedCommand, state: AutoTaskState): AutoTaskRecord = byTask[task] ?: AutoTaskRecord(records.size + 1, task, state).also {
        byTask[task] = it; records.add(it)
    }
    fun note(task: SuggestedCommand, state: AutoTaskState) { add(task, AutoTaskState.PLANNED).state = state }
    fun evidence(task: SuggestedCommand, state: AutoTaskState, finding: String) {
        add(task, AutoTaskState.PLANNED).also { it.state = state; it.finding = finding.take(1500) }
    }
    fun replaceRemaining(remaining: List<SuggestedCommand>, replacement: List<SuggestedCommand>) {
        remaining.forEach { task -> byTask[task]?.let { if (it.state == AutoTaskState.PLANNED) it.state = AutoTaskState.REPLACED } }
        replacement.forEach { add(it, AutoTaskState.PLANNED) }
    }
    fun interrupt() {
        records.forEach { record -> record.state = when (record.state) {
            AutoTaskState.PREPARING -> AutoTaskState.PLANNED
            AutoTaskState.SENDING, AutoTaskState.WAITING, AutoTaskState.CHECKING -> AutoTaskState.UNCERTAIN
            else -> record.state
        } }
    }
}
