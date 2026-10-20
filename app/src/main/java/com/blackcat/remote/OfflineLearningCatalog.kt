package com.blackcat.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.GZIPInputStream

internal class OfflineTool(val id: String, val name: String, val summary: String,
    val version: String, val distributions: List<String>, val entries: List<String>, val sources: List<String>)
internal class OfflineCli(val id: String, val name: String, val invocation: String, val kind: String,
    val summary: String, val flags: List<String>, val help: List<String>, val toolIds: List<String>)
internal class OfflineStep(val id: String, val commandId: String, val why: String, val lookFor: String, val onError: String)
internal class OfflineLesson(val id: String, val title: String, val category: String, val goal: String,
    val prerequisites: List<String>, val steps: List<OfflineStep>, val next: List<String>)
internal class OfflineCommandNotes(val prerequisites: List<String>, val troubleshooting: List<String>,
    val network: String, val sourceIds: List<String>, val lessons: List<String>)
internal class OfflineLearningData(val commands: List<LinuxCommandItem>, val tools: List<OfflineTool>,
    val entries: List<OfflineCli>, val lessons: List<OfflineLesson>, val notes: Map<String, OfflineCommandNotes>,
    val sources: Map<String, String>) {
    val commandById = commands.associateBy { it.id }
    val toolById = tools.associateBy { it.id }
    val entryById = entries.associateBy { it.id }
    val lessonById = lessons.associateBy { it.id }
}

/** Bundled reference data only. No download, process, sender, approval or persistent user state. */
internal object OfflineLearningCatalog {
    const val ASSET = "offline_learning_v2.json.gz"
    private const val MAX_JSON_BYTES = 12_000_000
    @Volatile private var cached: OfflineLearningData? = null

    fun load(context: Context): OfflineLearningData = cached ?: synchronized(this) {
        cached ?: context.assets.open(ASSET).use { raw ->
            GZIPInputStream(raw).use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= MAX_JSON_BYTES) { "Offline learning data is too large." }
                    out.write(buffer, 0, count)
                }
                val bytes = out.toByteArray()
                parse(bytes.toString(Charsets.UTF_8)).also { cached = it }
            }
        }
    }

    internal fun parse(text: String): OfflineLearningData {
        val root = JSONObject(text)
        require(root.getInt("schema_version") == 2) { "Unsupported offline learning format." }
        fun array(name: String, maximum: Int): JSONArray = root.getJSONArray(name).also {
            require(it.length() in 1..maximum) { "Invalid offline $name count." }
        }
        fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else {
            require(a.length() <= 10000)
            (0 until a.length()).map { a.getString(it).also { value -> require(value.length <= 10000) } }
        }
        fun JSONObject.list(name: String) = strings(optJSONArray(name))
        val commands = array("command_examples", 10000).mapObjects { o ->
            val command = o.getString("command")
            require(CommandPolicy.problem(command) == null) { "Unsupported offline command." }
            LinuxCommandItem(o.getString("id"), o.getString("category"), o.getString("topic"), o.getString("title"),
                command, o.getString("explanation"), o.getString("expected_result"),
                LinuxCommandRisk.valueOf(o.getString("risk").uppercase(java.util.Locale.ROOT)),
                o.getBoolean("requires_admin"), o.getString("compatibility"), o.list("tags"))
        }
        val notes = root.getJSONArray("command_examples").mapObjects { o ->
            o.getString("id") to OfflineCommandNotes(o.list("prerequisites"), o.list("troubleshooting"),
                o.getJSONObject("network").getString("effect"), o.getJSONArray("source_refs").mapObjects { it.getString("source_id") }, o.list("lesson_ids"))
        }.toMap()
        val tools = array("tools", 5000).mapObjects { o -> OfflineTool(o.getString("id"), o.getString("name"),
            o.optString("summary"), if (o.isNull("version")) "Not supplied by this source" else o.optString("version", "Not supplied by this source"),
            o.list("distributions"), o.list("entry_ids"), o.list("source_ids")) }
        val inventory = array("command_inventory", 20000)
        val references = root.getJSONArray("reference_inventory")
        require(references.length() <= 5000)
        val entryObjects = inventory.mapObjects { it } + references.mapObjects { it }
        val entries = entryObjects.map { o -> OfflineCli(o.getString("id"), o.getString("name"),
            o.getString("invocation"), o.getString("kind"), o.optString("summary"), o.list("documented_flags"),
            o.list("observed_help_invocations"), o.list("tool_ids")) }
        val lessons = array("lessons", 1000).mapObjects { o ->
            val steps = o.getJSONArray("steps")
            require(steps.length() in 1..40)
            OfflineLesson(o.getString("id"), o.getString("title"), o.getString("category"), o.getString("goal"),
                o.list("prerequisite_lesson_ids"), steps.mapObjects { s ->
                    val transitions = s.getJSONArray("transitions")
                    val errors = transitions.mapObjects { it }.filter { it.getString("condition") == "error_or_unexpected_result" }
                    OfflineStep(s.getString("id"), s.getString("command_id"), s.getString("why"), s.getString("look_for"),
                        errors.joinToString("\n") { it.getString("guidance") })
                }, o.list("next_lesson_ids"))
        }
        val sources = array("sources", 100).mapObjects { o -> o.getString("id") to
            (o.optString("name") + "\n" + o.optString("upstream_url", o.optString("repository", o.optString("source_path"))) +
                o.optString("commit").let { if (it.isBlank()) "" else "\nSnapshot: $it" }) }.toMap()
        require(commands.map { it.id }.distinct().size == commands.size && notes.size == commands.size)
        require(tools.map { it.id }.distinct().size == tools.size)
        require(entries.map { it.id }.distinct().size == entries.size)
        require(lessons.map { it.id }.distinct().size == lessons.size)
        val data = OfflineLearningData(commands, tools, entries, lessons, notes, sources)
        require(tools.all { t -> t.entries.all { it in data.entryById } && t.sources.all { it in sources } })
        require(entries.all { e -> e.toolIds.all { it in data.toolById } })
        require(notes.values.all { n -> n.sourceIds.all { it in sources } && n.lessons.all { it in data.lessonById } })
        require(lessons.all { l -> l.steps.all { it.commandId in data.commandById } &&
            l.prerequisites.all { it in data.lessonById } && l.next.all { it in data.lessonById } })
        val counts = root.getJSONObject("counts")
        require(counts.getInt("command_example_entries") == commands.size)
        require(counts.getInt("tool_or_package_groups") == tools.size)
        require(counts.getInt("documented_entries_total") == entries.size)
        return data
    }

    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
        (0 until length()).map { block(getJSONObject(it)) }
}
