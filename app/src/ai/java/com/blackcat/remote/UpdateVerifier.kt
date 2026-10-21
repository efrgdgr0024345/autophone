package com.blackcat.remote

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal interface UpdateLedger {
    /** Implementations must serialize read, validation and durable write across all instances. */
    fun accept(release: UpdateRelease, installed: InstalledUpdateIdentity, now: Long)
}
internal data class UpdateApkIdentity(val packageName: String, val version: Long, val versionName: String,
    val signers: Set<String>, val permissions: Set<String>)
internal fun interface UpdateApkInspector { fun inspect(file: File): UpdateApkIdentity }

internal class UpdateVerifier(
    private val trust: UpdateTrust,
    private val installed: InstalledUpdateIdentity,
    private val installedPermissions: Set<String>,
    private val ledger: UpdateLedger,
    private val inspector: UpdateApkInspector,
    private val transport: UpdateHttps,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    fun check(): UpdateRelease {
        val bytes = ByteArrayOutputStream()
        transport.fetch(trust.metadataUrl, trust.metadataHosts, 16384, bytes)
        val release = SignedUpdateMetadata.authenticate(bytes.toByteArray(), trust)
        transport.ensureActive()
        ledger.accept(release, installed, now())
        return release
    }

    fun download(release: UpdateRelease, file: File) {
        // Revalidate freshness and the durable floor before every download and export.
        ledger.accept(release, installed, now())
        check(release.version > installed.version) { "This version is already installed" }
        check(file.createNewFile()) { "Update staging file already exists" }
        try {
            FileOutputStream(file).use { out ->
                check(transport.fetch(release.apkUrl, trust.apkHosts, release.size, out) == release.size)
                out.fd.sync()
            }
            verifyFile(release, file)
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun verifyFile(release: UpdateRelease, file: File) {
        transport.ensureActive()
        ledger.accept(release, installed, now())
        check(file.isFile && file.length() == release.size) { "APK size differs from signed metadata" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16384)
            var total = 0L
            while (true) {
                transport.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                check(total <= release.size)
                digest.update(buffer, 0, count)
            }
            check(total == release.size)
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        check(hash == release.hash) { "APK hash differs from signed metadata" }
        val apk = inspector.inspect(file)
        check(apk.packageName == installed.packageName && apk.packageName == release.packageName &&
            apk.version == release.version && apk.version > installed.version && apk.versionName == release.versionName &&
            apk.signers == setOf(installed.signer) && apk.signers == setOf(release.signer) &&
            apk.permissions == installedPermissions) { "APK package, version, signer or capabilities differ" }
        transport.ensureActive()
        ledger.accept(release, installed, now())
    }
    fun ensureActive() = transport.ensureActive()
    fun cancel() = transport.cancel()
}
