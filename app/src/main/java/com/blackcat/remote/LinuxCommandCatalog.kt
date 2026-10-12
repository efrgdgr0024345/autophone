package com.blackcat.remote

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

enum class LinuxCommandRisk { LOW, MEDIUM, HIGH }

data class LinuxCommandItem(
    val id: String,
    val category: String,
    val topic: String,
    val title: String,
    val command: String,
    val explanation: String,
    val expectedResult: String,
    val risk: LinuxCommandRisk,
    val requiresAdmin: Boolean,
    val compatibility: String,
    val tags: List<String>
)

data class LinuxCommandGroup(val name: String, val count: Int)

object LinuxCommandCatalog {
    val ASSET_PARTS = listOf(
        "linux_commands_00.b64",
        "linux_commands_01.b64",
        "linux_commands_02.b64",
        "linux_commands_03.b64",
        "linux_commands_04.b64",
        "linux_commands_05.b64",
        "linux_commands_06.b64"
    )
    const val MIN_EXPECTED_COMMANDS = 200

    @Volatile
    private var cached: List<LinuxCommandItem>? = null

    fun load(context: Context): List<LinuxCommandItem> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: buildString {
                ASSET_PARTS.forEach { name ->
                    context.assets.open(name)
                        .bufferedReader(Charsets.US_ASCII)
                        .use { append(it.readText().trim()) }
                }
            }.let { encoded ->
                val compressed = Base64.decode(encoded, Base64.DEFAULT)
                GZIPInputStream(ByteArrayInputStream(compressed))
                    .bufferedReader(Charsets.UTF_8)
                    .use { reader ->
                        parse(reader.readText()).also { loaded ->
                            require(loaded.size >= MIN_EXPECTED_COMMANDS) {
                                "Offline Linux command data is incomplete."
                            }
                            cached = loaded
                        }
                    }
            }
        }
    }

    fun parse(text: String): List<LinuxCommandItem> {
        val lines = text.lineSequence()
            .map { it.removeSuffix("\r") }
            .filter { it.isNotBlank() }
            .toList()
        require(lines.isNotEmpty()) { "Offline Linux command data is empty." }

        val header = lines.first().split('\t')
        val expectedHeader = listOf(
            "id", "category", "topic", "title", "command", "explanation",
            "expected", "risk", "admin", "compatibility", "tags"
        )
        require(header == expectedHeader) { "Offline Linux command data has an unsupported format." }

        val seen = HashSet<String>()
        return lines.drop(1).mapIndexed { index, line ->
            val fields = line.split('\t')
            require(fields.size == expectedHeader.size) {
                "Invalid offline command row ${index + 2}."
            }
            val id = fields[0].trim()
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{2,79}"))) {
                "Invalid offline command identifier."
            }
            require(seen.add(id)) { "Duplicate offline command identifier: $id" }

            val command = fields[4]
            require(CommandPolicy.problem(command) == null) {
                "Unsupported offline command: $id"
            }
            val risk = try {
                LinuxCommandRisk.valueOf(fields[7].trim().uppercase(Locale.ROOT))
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid risk value for $id")
            }
            val admin = when (fields[8].trim().lowercase(Locale.ROOT)) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Invalid admin value for $id")
            }
            val tags = fields[10].split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            LinuxCommandItem(
                id = id,
                category = fields[1].requireText("category", id),
                topic = fields[2].requireText("topic", id),
                title = fields[3].requireText("title", id),
                command = command,
                explanation = fields[5].requireText("explanation", id),
                expectedResult = fields[6].requireText("expected result", id),
                risk = risk,
                requiresAdmin = admin,
                compatibility = fields[9].requireText("compatibility", id),
                tags = tags
            )
        }
    }

    fun categories(items: List<LinuxCommandItem>): List<LinuxCommandGroup> =
        groupedInSourceOrder(items) { it.category }

    fun topics(items: List<LinuxCommandItem>, category: String): List<LinuxCommandGroup> =
        groupedInSourceOrder(items.filter { it.category == category }) { it.topic }

    fun commands(
        items: List<LinuxCommandItem>,
        category: String,
        topic: String
    ): List<LinuxCommandItem> = items.filter {
        it.category == category && it.topic == topic
    }

    fun find(items: List<LinuxCommandItem>, id: String): LinuxCommandItem? =
        items.firstOrNull { it.id == id }

    fun search(
        items: List<LinuxCommandItem>,
        query: String,
        limit: Int = 60
    ): List<LinuxCommandItem> {
        val clean = normalize(query)
        if (clean.isBlank()) return emptyList()

        val rawTokens = clean.split(' ').filter { it.length >= 2 }
        if (rawTokens.isEmpty()) return emptyList()
        val tokens = LinkedHashSet<String>()
        rawTokens.forEach { token ->
            tokens += token
            SYNONYMS[token].orEmpty().forEach { tokens += it }
        }

        return items.asSequence()
            .map { item -> item to score(item, clean, tokens) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<LinuxCommandItem, Int>> { it.second }
                    .thenBy { it.first.title.lowercase(Locale.ROOT) }
            )
            .take(limit.coerceIn(1, 100))
            .map { it.first }
            .toList()
    }

    private fun score(
        item: LinuxCommandItem,
        cleanQuery: String,
        tokens: Set<String>
    ): Int {
        val title = normalize(item.title)
        val category = normalize(item.category)
        val topic = normalize(item.topic)
        val command = item.command.lowercase(Locale.ROOT)
        val explanation = normalize(item.explanation + " " + item.expectedResult)
        val tags = item.tags.map(::normalize)

        var score = 0
        if (title == cleanQuery) score += 300
        if (title.startsWith(cleanQuery)) score += 160
        else if (title.contains(cleanQuery)) score += 120
        if (command == cleanQuery) score += 170
        else if (command.contains(cleanQuery)) score += 70

        var matchedOriginal = 0
        tokens.forEach { token ->
            var tokenScore = 0
            if (title.startsWith(token)) tokenScore = maxOf(tokenScore, 62)
            if (title.contains(token)) tokenScore = maxOf(tokenScore, 48)
            if (tags.any { it == token }) tokenScore = maxOf(tokenScore, 46)
            if (tags.any { it.contains(token) }) tokenScore = maxOf(tokenScore, 36)
            if (topic.contains(token)) tokenScore = maxOf(tokenScore, 32)
            if (category.contains(token)) tokenScore = maxOf(tokenScore, 28)
            if (command.contains(token)) tokenScore = maxOf(tokenScore, 24)
            if (explanation.contains(token)) tokenScore = maxOf(tokenScore, 12)
            if (tokenScore > 0 && token in cleanQuery.split(' ')) matchedOriginal++
            score += tokenScore
        }
        if (rawQueryTokenCount(cleanQuery) > 1 &&
            matchedOriginal >= rawQueryTokenCount(cleanQuery)
        ) {
            score += 80
        }
        return score
    }

    private fun rawQueryTokenCount(value: String): Int =
        value.split(' ').count { it.length >= 2 }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9+._-]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun <T> groupedInSourceOrder(
        items: List<T>,
        key: (T) -> String
    ): List<LinuxCommandGroup> {
        val counts = LinkedHashMap<String, Int>()
        items.forEach { item ->
            val name = key(item)
            counts[name] = (counts[name] ?: 0) + 1
        }
        return counts.map { LinuxCommandGroup(it.key, it.value) }
    }

    private fun String.requireText(field: String, id: String): String {
        val value = trim()
        require(value.isNotEmpty()) { "Missing $field for $id" }
        return value
    }

    private val SYNONYMS = mapOf(
        "internet" to listOf("network", "connectivity"),
        "online" to listOf("network", "connectivity"),
        "wifi" to listOf("wireless", "network"),
        "wi-fi" to listOf("wireless", "network"),
        "lan" to listOf("ethernet", "network"),
        "ip" to listOf("address", "network"),
        "gateway" to listOf("route", "network"),
        "resolver" to listOf("dns", "nameserver"),
        "name" to listOf("dns", "hostname"),
        "space" to listOf("disk", "storage", "capacity"),
        "drive" to listOf("disk", "storage"),
        "ssd" to listOf("disk", "nvme", "storage"),
        "hdd" to listOf("disk", "smart", "storage"),
        "ram" to listOf("memory"),
        "slow" to listOf("performance", "cpu", "memory"),
        "processor" to listOf("cpu"),
        "temperature" to listOf("thermal", "sensors"),
        "temp" to listOf("thermal", "sensors"),
        "program" to listOf("process"),
        "app" to listOf("process", "service"),
        "daemon" to listOf("service", "systemd"),
        "error" to listOf("logs", "failed", "warning"),
        "boot" to listOf("kernel", "journal", "uptime"),
        "login" to listOf("user", "session", "ssh"),
        "admin" to listOf("sudo", "permissions"),
        "rights" to listOf("permissions", "acl"),
        "owner" to listOf("permissions", "user"),
        "installed" to listOf("packages"),
        "update" to listOf("packages", "upgrade"),
        "container" to listOf("docker", "podman"),
        "vm" to listOf("virtual", "kvm", "libvirt"),
        "checksum" to listOf("hash", "sha256", "integrity"),
        "manual" to listOf("help", "man", "apropos"),
        "port" to listOf("ports", "socket", "listening")
    )
}
