package com.blackcat.remote

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfflineLearningCatalogTest {
    private fun payload(): String = listOf(File("../catalog/offline-catalog.json"), File("catalog/offline-catalog.json"))
        .first { it.exists() }.readText()

    @Test fun bundledSnapshotSeparatesCommandsToolsAndReferencesWithRealLinks() {
        val data = OfflineLearningCatalog.parse(payload())
        assertEquals(467, data.commands.size)
        assertEquals(824, data.tools.size)
        assertEquals(35, data.lessons.size)
        assertTrue(data.commands.any { it.category == "Kali Linux" })
        assertTrue(data.entries.any { it.invocation == "sha256sum" && it.kind == "binary" })
        assertTrue(data.entries.any { it.invocation == "fsck.minix" && it.kind == "binary" })
        assertFalse(data.entries.any { it.invocation == "tc cake" && it.kind == "subcommand" })
        for (lesson in data.lessons) {
            assertTrue(lesson.steps.isNotEmpty())
            assertTrue(lesson.steps.all { it.commandId in data.commandById && it.onError.isNotBlank() })
        }
        assertEquals(listOf("workspace", "sample-copy"), data.lessonById["workspace-cleanup"]!!.prerequisites)
    }

    @Test(expected = IllegalArgumentException::class) fun danglingLessonCommandCannotBecomeANavigableStep() {
        val root = JSONObject(payload())
        root.getJSONArray("lessons").getJSONObject(0).getJSONArray("steps").getJSONObject(0).put("command_id", "missing-command")
        OfflineLearningCatalog.parse(root.toString())
    }

    @Test(expected = IllegalArgumentException::class) fun controlCharactersCannotBecomeTypingExamples() {
        val root = JSONObject(payload())
        root.getJSONArray("command_examples").getJSONObject(0).put("command", "id\nwhoami")
        OfflineLearningCatalog.parse(root.toString())
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateIdsAreRejectedBeforeLookupCanChooseTheWrongCommand() {
        val root = JSONObject(payload())
        val commands = root.getJSONArray("command_examples")
        commands.getJSONObject(1).put("id", commands.getJSONObject(0).getString("id"))
        OfflineLearningCatalog.parse(root.toString())
    }

    @Test(expected = IllegalArgumentException::class) fun aFalseCountCannotBeDisplayedAsACompleteSnapshot() {
        val root = JSONObject(payload())
        root.getJSONObject("counts").put("command_example_entries", 1)
        OfflineLearningCatalog.parse(root.toString())
    }

    @Test(expected = IllegalArgumentException::class) fun danglingToolReferencesAreRejected() {
        val root = JSONObject(payload())
        root.getJSONArray("tools").getJSONObject(0).getJSONArray("entry_ids").put("absent-entry")
        OfflineLearningCatalog.parse(root.toString())
    }
}
