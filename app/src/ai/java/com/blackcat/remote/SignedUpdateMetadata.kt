package com.blackcat.remote

import java.net.URI
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** No downloaded root keys, TOFU, unsigned fallback or production test-key substitution. */
internal object ProductionUpdateTrust {
    // Deliberately unprovisioned. A reviewed public key AND release service are required.
    fun configured(): UpdateTrust? = null
}

internal class UpdateTrust(
    val metadataUrl: String,
    val metadataHosts: Set<String>,
    val apkHosts: Set<String>,
    val keys: Map<String, PublicKey>,
    val channel: String,
    val flavor: String = "ai"
) {
    init {
        require(keys.isNotEmpty() && keys.keys.all { it.matches(Regex("[a-z0-9-]{1,48}")) })
        require(keys.values.all { it is RSAPublicKey && it.modulus.bitLength() in 3072..8192 })
        require(channel.matches(Regex("[a-z0-9-]{1,32}")) && flavor == "ai")
        UpdateUrls.validate(metadataUrl, metadataHosts)
        require(apkHosts.isNotEmpty())
    }
}

internal object UpdateUrls {
    fun validate(value: String, hosts: Set<String>): URI {
        require(value.length in 1..2048 && value.all { it.code in 33..126 }) { "Invalid update URL" }
        val u = URI(value)
        require(u.scheme == "https" && u.host in hosts && u.port in setOf(-1, 443) &&
            u.rawUserInfo == null && u.rawFragment == null && u.rawQuery == null) { "Update host or HTTPS policy rejected" }
        // No percent escapes, alternate separators, dot traversal, query tokens or ambiguous paths.
        require(u.rawPath.matches(Regex("/[A-Za-z0-9_./~-]+")) &&
            u.rawPath.split('/').none { it == "." || it == ".." } && !u.rawPath.contains("//")) { "Invalid update path" }
        require(hosts.isNotEmpty() && hosts.all { it.matches(Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")) })
        return u
    }
}

/** Flat ASCII JSON deliberately excludes escapes, nesting, coercion, duplicate and unknown fields. */
internal object UpdateJson {
    private val token = Regex("\\s*\"([a-z_][a-z0-9_]*)\"\\s*:\\s*(\"[ !#-\\[\\]-~]*\"|[0-9]+)\\s*")
    fun parse(bytes: ByteArray, fields: Set<String>): Map<String, String> {
        require(bytes.size in 2..16384 && bytes.all { it.toInt() in setOf(9, 10, 13) || it.toInt() in 32..126 })
        val text = bytes.toString(Charsets.US_ASCII).trim()
        require(text.startsWith('{') && text.endsWith('}'))
        val result = linkedMapOf<String, String>()
        var offset = 1
        while (offset < text.length - 1) {
            val match = token.find(text, offset) ?: error("Invalid update metadata")
            require(match.range.first == offset && match.range.last < text.length - 1)
            val key = match.groupValues[1]
            val raw = match.groupValues[2]
            require(key in fields && key !in result)
            result[key] = raw
            offset = match.range.last + 1
            if (offset == text.length - 1) break
            require(text[offset] == ',')
            offset++
            require(offset < text.length - 1)
        }
        require(result.keys == fields)
        return result
    }
    fun string(map: Map<String, String>, name: String): String {
        val raw = map.getValue(name)
        require(raw.startsWith('"') && raw.endsWith('"'))
        return raw.substring(1, raw.length - 1)
    }
    fun number(map: Map<String, String>, name: String): Long {
        val raw = map.getValue(name)
        require(raw.matches(Regex("0|[1-9][0-9]{0,17}")))
        return raw.toLong()
    }
}

internal data class InstalledUpdateIdentity(val packageName: String, val version: Long, val signer: String)
internal data class UpdateRelease(
    val sequence: Long, val issued: Long, val expires: Long, val version: Long,
    val versionName: String, val packageName: String, val flavor: String, val channel: String,
    val size: Long, val hash: String, val signer: String, val apkUrl: String, val payloadHash: String
)
internal data class UpdateFloor(val sequence: Long = 0, val version: Long = 0,
    val payloadHash: String = "", val wallTime: Long = 0)

internal object SignedUpdateMetadata {
    private val fields = setOf("schema", "key_id", "package", "flavor", "channel", "sequence", "issued", "expires",
        "version_code", "version_name", "apk_size", "apk_sha256", "signer_sha256", "apk_url")
    private val domain = "BlackCat signed update v1\u0000".toByteArray(Charsets.US_ASCII)
    const val MAX_APK = 100L * 1024 * 1024
    const val MAX_LIFETIME = 7L * 24 * 3600
    fun authenticate(envelope: ByteArray, trust: UpdateTrust): UpdateRelease {
        val e = UpdateJson.parse(envelope, setOf("payload", "signature"))
        fun decode(name: String, max: Int): ByteArray {
            val text = UpdateJson.string(e, name)
            require(text.length <= max && text.matches(Regex("[A-Za-z0-9+/]+={0,2}")))
            val bytes = Base64.getDecoder().decode(text)
            require(Base64.getEncoder().encodeToString(bytes) == text)
            return bytes
        }
        val payload = decode("payload", 12000)
        val signature = decode("signature", 1400)
        val m = UpdateJson.parse(payload, fields)
        val key = trust.keys[UpdateJson.string(m, "key_id")] ?: error("Unknown update authority")
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(domain); update(payload); verify(signature)
        }) { "Update metadata signature rejected" }
        require(UpdateJson.number(m, "schema") == 1L)
        fun s(name: String) = UpdateJson.string(m, name)
        fun n(name: String) = UpdateJson.number(m, name)
        require(s("version_name").matches(Regex("[A-Za-z0-9._-]{1,64}")))
        require(s("apk_sha256").matches(Regex("[0-9a-f]{64}")) && s("signer_sha256").matches(Regex("[0-9a-f]{64}")))
        require(n("apk_size") in 1..MAX_APK && n("sequence") > 0 && n("version_code") in 1..2100000000)
        require(n("issued") > 0 && n("expires") > n("issued") && n("expires") - n("issued") <= MAX_LIFETIME)
        require(s("flavor") == trust.flavor && s("channel") == trust.channel)
        UpdateUrls.validate(s("apk_url"), trust.apkHosts)
        return UpdateRelease(n("sequence"), n("issued"), n("expires"), n("version_code"), s("version_name"),
            s("package"), s("flavor"), s("channel"), n("apk_size"), s("apk_sha256"), s("signer_sha256"), s("apk_url"), sha256(payload))
    }

    fun nextFloor(release: UpdateRelease, installed: InstalledUpdateIdentity, floor: UpdateFloor, now: Long): UpdateFloor {
        require(now > 0 && now >= floor.wallTime - 300 && now >= release.issued - 300 && now < release.expires) { "Update freshness check failed" }
        require(release.packageName == installed.packageName && release.signer == installed.signer) { "Update identity differs from this app" }
        require(release.version >= installed.version && release.version >= floor.version && release.sequence >= floor.sequence) { "Update rollback rejected" }
        require(release.sequence != floor.sequence || release.payloadHash == floor.payloadHash) { "Update sequence reused with different content" }
        return UpdateFloor(release.sequence, release.version, release.payloadHash, maxOf(now, floor.wallTime))
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
